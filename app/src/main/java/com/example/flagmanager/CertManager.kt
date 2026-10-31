package com.example.flagmanager

import android.content.Context
import android.util.Log
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * CertManager — generates and caches a self-signed RSA-2048 CA key+cert for
 * the MITM proxy's TLS interception of HTTPS traffic.
 *
 * Zero external dependencies — uses Android's bundled sun.security.x509 APIs
 * via fully-qualified class names (present on all Android API levels via
 * the Conscrypt/OpenJDK runtime that ships with Android). No BouncyCastle needed.
 *
 * The CA cert in PEM form is exposed via [getOrCreateCaPem] so the UI can
 * offer "Copy CA cert" → user installs in Settings → Security → Trusted credentials.
 *
 * Note: Android 7+ restricts user-installed CAs for apps targeting N+.
 * Roblox must have a network_security_config that trusts user certs, OR
 * the cert must be installed as a system cert (requires root) for HTTPS
 * interception to work. getTrustAllFactory() is still used for upstream
 * (proxy → real server) connections regardless.
 */
object CertManager {

    private const val TAG = "CertManager"
    private const val CERT_FILE = "mitm_ca.pem"
    private const val KEY_FILE  = "mitm_ca.key"
    private const val KEY_SIZE  = 2048

    @Volatile private var cachedFactory: SSLSocketFactory? = null
    @Volatile private var cachedPem: String? = null

    /**
     * Returns an SSLSocketFactory that trusts ALL upstream certs —
     * used by MitmProxy for outbound (proxy → real server) TLS connections
     * so the proxy isn't blocked by its own trust store.
     */
    fun getTrustAllFactory(): SSLSocketFactory {
        cachedFactory?.let { return it }
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, trustAll, SecureRandom())
        return ctx.socketFactory.also { cachedFactory = it }
    }

    /**
     * Generates (or loads from disk) a self-signed X509v3 CA cert + RSA-2048 key.
     * Returns the PEM string of the CA cert, or null if generation fails.
     *
     * Uses sun.security.x509.* via reflection-free fully-qualified references —
     * these classes are present in Android's runtime (Conscrypt/OpenJDK) on all
     * supported API levels. No additional build.gradle dep required.
     *
     * If generation fails (e.g. future Android strips sun.security), HTTPS
     * interception won't work but getTrustAllFactory() (upstream TLS) is unaffected.
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

            val now = Date()
            val expiry = Date(now.time + 10L * 365 * 24 * 60 * 60 * 1000)
            val subject = X500Principal("CN=TacoStrap CA, O=TacoStrap, C=US")
            val serial = BigInteger.valueOf(SecureRandom().nextLong().and(Long.MAX_VALUE))

            // sun.security.x509 — ships with Android runtime, zero extra deps
            val info = sun.security.x509.X509CertInfo()
            val interval = sun.security.x509.CertificateValidity(now, expiry)
            val serialNum = sun.security.x509.CertificateSerialNumber(serial)
            val algorithmId = sun.security.x509.AlgorithmId(
                sun.security.x509.AlgorithmId.sha256WithRSAEncryption_oid
            )
            val owner = sun.security.x509.X500Name(subject.name)

            info.set(sun.security.x509.X509CertInfo.VALIDITY, interval)
            info.set(sun.security.x509.X509CertInfo.SERIAL_NUMBER, serialNum)
            info.set(sun.security.x509.X509CertInfo.SUBJECT, owner)
            info.set(sun.security.x509.X509CertInfo.ISSUER, owner)
            info.set(sun.security.x509.X509CertInfo.KEY,
                sun.security.x509.CertificateX509Key(keyPair.public))
            info.set(sun.security.x509.X509CertInfo.VERSION,
                sun.security.x509.CertificateVersion(sun.security.x509.CertificateVersion.V3))
            info.set(sun.security.x509.X509CertInfo.ALGORITHM_ID,
                sun.security.x509.CertificateAlgorithmId(algorithmId))

            // BasicConstraints extension — marks this as a CA cert
            val extensions = sun.security.x509.CertificateExtensions()
            extensions.set(
                sun.security.x509.BasicConstraintsExtension.NAME,
                sun.security.x509.BasicConstraintsExtension(true, true, Int.MAX_VALUE)
            )
            info.set(sun.security.x509.X509CertInfo.EXTENSIONS, extensions)

            // Sign with our own private key (self-signed)
            val cert = sun.security.x509.X509CertImpl(info)
            cert.sign(keyPair.private, "SHA256WithRSA")

            val certPem = buildString {
                append("-----BEGIN CERTIFICATE-----\n")
                append(android.util.Base64.encodeToString(cert.encoded, android.util.Base64.DEFAULT))
                append("-----END CERTIFICATE-----\n")
            }
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

    /** Clears cached cert/key and deletes disk files — forces regeneration on next call. */
    fun clearCert(context: Context) {
        File(context.filesDir, CERT_FILE).delete()
        File(context.filesDir, KEY_FILE).delete()
        cachedPem = null
        cachedFactory = null
    }
}
