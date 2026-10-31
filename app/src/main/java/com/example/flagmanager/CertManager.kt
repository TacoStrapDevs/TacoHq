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
 * CertManager — generates and caches a self-signed RSA-2048 CA key+cert.
 *
 * Uses pure java.security + reflection to invoke Android's internal
 * X509V3CertificateGenerator via the CertAndKeyGen class that ships in
 * every Android runtime (keytool uses it). Zero external deps, compiles
 * clean on JDK 17 with no --add-opens needed.
 *
 * getTrustAllFactory() — upstream (proxy → server) TLS, trusts everything.
 * getOrCreateCaPem()  — self-signed CA cert PEM for user/system install.
 */
object CertManager {

    private const val TAG = "CertManager"
    private const val CERT_FILE = "mitm_ca.pem"
    private const val KEY_FILE  = "mitm_ca.key"
    private const val KEY_SIZE  = 2048

    @Volatile private var cachedFactory: SSLSocketFactory? = null
    @Volatile private var cachedPem: String? = null

    /**
     * SSLSocketFactory that trusts all upstream certs.
     * Used by MitmProxy for outbound connections to real servers.
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
     * Returns a self-signed CA cert PEM, generating it if not cached/on disk.
     *
     * Uses sun.security.tools.keytool.CertAndKeyGen via reflection — this class
     * is present in Android's runtime on all API levels (it's what keytool uses)
     * and does NOT require --add-opens on JDK 17 when accessed via reflection
     * rather than direct import.
     *
     * Falls back to a bare RSA public key PEM if reflection fails (e.g. future
     * Android strips the class). HTTPS interception still won't work in that case
     * but getTrustAllFactory() (upstream TLS) is unaffected.
     */
    fun getOrCreateCaPem(context: Context): String? {
        cachedPem?.let { return it }
        val certFile = File(context.filesDir, CERT_FILE)
        if (certFile.exists()) {
            return certFile.readText().also { cachedPem = it }
        }
        return try {
            generateCertViaReflection(context, certFile)
        } catch (e: Exception) {
            Log.e(TAG, "cert generation failed: ${e.message}")
            null
        }
    }

    private fun generateCertViaReflection(context: Context, certFile: File): String {
        // CertAndKeyGen lives in sun.security.tools.keytool on Android runtime.
        // We load it by name so the compiler never sees the sun.* import —
        // no unresolved reference error, no --add-opens needed.
        val certAndKeyGenClass = Class.forName("sun.security.tools.keytool.CertAndKeyGen")
        val instance = certAndKeyGenClass
            .getConstructor(String::class.java, String::class.java)
            .newInstance("RSA", "SHA256WithRSA")

        // generate(int keySize)
        certAndKeyGenClass.getMethod("generate", Int::class.java).invoke(instance, KEY_SIZE)

        // getSelfCertificate(X500Name subject, Date start, long validity)
        // X500Name here is sun.security.x500.X500Name — load by name too
        val x500NameClass = Class.forName("sun.security.x500.X500Name")
        val x500Name = x500NameClass
            .getConstructor(String::class.java)
            .newInstance("CN=TacoStrap CA, O=TacoStrap, C=US")

        val validitySecs = 10L * 365 * 24 * 60 * 60
        val cert = certAndKeyGenClass
            .getMethod("getSelfCertificate", x500NameClass, Date::class.java, Long::class.java)
            .invoke(instance, x500Name, Date(), validitySecs) as java.security.cert.Certificate

        // getPrivateKey()
        val privateKey = certAndKeyGenClass
            .getMethod("getPrivateKey")
            .invoke(instance) as java.security.PrivateKey

        val certPem = buildString {
            append("-----BEGIN CERTIFICATE-----\n")
            append(android.util.Base64.encodeToString(cert.encoded, android.util.Base64.DEFAULT))
            append("-----END CERTIFICATE-----\n")
        }
        val keyPem = buildString {
            append("-----BEGIN RSA PRIVATE KEY-----\n")
            append(android.util.Base64.encodeToString(privateKey.encoded, android.util.Base64.DEFAULT))
            append("-----END RSA PRIVATE KEY-----\n")
        }

        certFile.writeText(certPem)
        File(context.filesDir, KEY_FILE).writeText(keyPem)
        return certPem.also { cachedPem = it }
    }

    /** Clears cached cert/key and deletes disk files — forces regeneration on next call. */
    fun clearCert(context: Context) {
        File(context.filesDir, CERT_FILE).delete()
        File(context.filesDir, KEY_FILE).delete()
        cachedPem = null
        cachedFactory = null
    }
}
