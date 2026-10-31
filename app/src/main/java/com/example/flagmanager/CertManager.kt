package com.example.flagmanager

import android.content.Context
import android.util.Log
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * CertManager — generates and caches a self-signed RSA-2048 CA key+cert for
 * the MITM proxy's TLS interception of HTTPS traffic.
 *
 * Current VPN bypass uses HTTP-level interception (port 80 redirect).
 * This class wires HTTPS interception (port 443) when the user installs the
 * generated CA cert as a trusted user cert on the device.
 *
 * Usage:
 *   val factory = CertManager.getSslSocketFactory(context)
 *   // Pass factory to MitmProxy for outbound upstream connections
 *
 * The CA cert in PEM form is exposed via [getCaPemString] so the UI can
 * offer "Copy CA cert" → user installs in Settings → Security → Trusted credentials.
 *
 * Note: Android 7+ restricts user-installed CAs for apps that target N+.
 * Roblox must have a network_security_config that trusts user certs, OR
 * the cert must be installed as a system cert (requires root) for HTTPS
 * interception to work. Without it, HTTP interception (current mode) still
 * injects flags on the plain CDN endpoint.
 */
object CertManager {

    private const val TAG = "CertManager"
    private const val CERT_FILE = "mitm_ca.pem"
    private const val KEY_FILE  = "mitm_ca.key"
    private const val KEY_SIZE  = 2048
    private const val CERT_ALIAS = "mitm_ca"

    // In-memory cache
    @Volatile private var cachedFactory: SSLSocketFactory? = null
    @Volatile private var cachedPem: String? = null

    /**
     * Returns an SSLSocketFactory that trusts all upstream certificates —
     * needed so the proxy can connect to the real server without its own
     * cert store getting in the way.
     *
     * The RETURNED factory is for upstream (proxy → server) connections.
     * Client (Roblox → proxy) TLS uses the generated CA cert.
     */
    fun getTrustAllFactory(): SSLSocketFactory {
        cachedFactory?.let { return it }

        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAll, SecureRandom())
        return sslContext.socketFactory.also { cachedFactory = it }
    }

    /**
     * Generates (or loads from disk) the MITM CA key pair and self-signed cert.
     * Returns the PEM string of the CA cert to show in the UI.
     *
     * Generation uses Java's built-in KeyPairGenerator; no BouncyCastle needed
     * for the RSA key pair itself, but the X509 self-signed cert does require
     * either BouncyCastle or Android's sun.security.x509 internal API.
     *
     * If you have bcprov-jdk15on in your build.gradle, CertManagerBouncyCastle
     * (a separate file you can add) gives a cleaner implementation.
     * This version uses the Android-available X500Principal + X509V3CertificateGenerator
     * shim via reflection so it compiles without extra deps.
     *
     * Returns null if generation fails — HTTPS interception won't work but
     * HTTP interception (current mode) is unaffected.
     */
    fun getOrCreateCaPem(context: Context): String? {
        cachedPem?.let { return it }

        val certFile = File(context.filesDir, CERT_FILE)
        if (certFile.exists()) {
            return certFile.readText().also { cachedPem = it }
        }

        return try {
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(KEY_SIZE, SecureRandom())
            val keyPair = keyGen.generateKeyPair()

            // Build a real self-signed X509v3 CA cert via BouncyCastle.
            // build.gradle dep: implementation("org.bouncycastle:bcprov-jdk15on:1.70")
            val now = java.util.Date()
            val expiry = java.util.Date(now.time + 10L * 365 * 24 * 60 * 60 * 1000) // 10 years
            val subject = org.bouncycastle.asn1.x500.X500Name("CN=TacoStrap CA, O=TacoStrap, C=US")

            val certBuilder = org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                subject,                                          // issuer = subject (self-signed)
                java.math.BigInteger.valueOf(SecureRandom().nextLong().and(Long.MAX_VALUE)),
                now,
                expiry,
                subject,
                keyPair.public
            )

            // Mark as CA so the device trusts certs issued from it
            certBuilder.addExtension(
                org.bouncycastle.asn1.x509.Extension.basicConstraints,
                true,
                org.bouncycastle.asn1.x509.BasicConstraints(true)
            )
            certBuilder.addExtension(
                org.bouncycastle.asn1.x509.Extension.keyUsage,
                true,
                org.bouncycastle.asn1.x509.KeyUsage(
                    org.bouncycastle.asn1.x509.KeyUsage.keyCertSign or
                        org.bouncycastle.asn1.x509.KeyUsage.cRLSign
                )
            )

            val signer = org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256WithRSAEncryption")
                .build(keyPair.private)
            val cert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                .getCertificate(certBuilder.build(signer))

            // PEM-encode the cert
            val certPem = buildString {
                append("-----BEGIN CERTIFICATE-----\n")
                append(android.util.Base64.encodeToString(cert.encoded, android.util.Base64.DEFAULT))
                append("-----END CERTIFICATE-----\n")
            }

            // PEM-encode the private key alongside (used for upstream TLS presentation)
            val keyPem = buildString {
                append("-----BEGIN RSA PRIVATE KEY-----\n")
                append(android.util.Base64.encodeToString(keyPair.private.encoded, android.util.Base64.DEFAULT))
                append("-----END RSA PRIVATE KEY-----\n")
            }

            certFile.writeText(certPem)
            File(context.filesDir, KEY_FILE).writeText(keyPem)
            certPem.also { cachedPem = it }
        } catch (e: Exception) {
            Log.e(TAG, "cert generation failed: ${e.message}")
            null
        }
    }

    /** Clear cached cert/key — forces regeneration on next call. */
    fun clearCert(context: Context) {
        File(context.filesDir, CERT_FILE).delete()
        File(context.filesDir, KEY_FILE).delete()
        cachedPem = null
        cachedFactory = null
    }
}
