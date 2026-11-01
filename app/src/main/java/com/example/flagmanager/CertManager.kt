package com.example.flagmanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// build.gradle: implementation("org.bouncycastle:bcprov-jdk15on:1.70")
object CertManager {

    private const val TAG         = "CertManager"
    private const val CERT_FILE   = "mitm_ca.pem"
    private const val KEY_FILE    = "mitm_ca.key"
    private const val KEY_SIZE    = 2048
    private const val CERT_ALIAS  = "mitm_ca"
    private const val VALIDITY_MS = 10L * 365 * 24 * 60 * 60 * 1000

    @Volatile private var cachedPem    : String?                 = null
    @Volatile private var cachedKeyPair: KeyPair?                = null
    @Volatile private var cachedCert   : X509Certificate?        = null
    @Volatile private var cachedFactory: SSLSocketFactory?       = null
    @Volatile private var cachedServer : SSLServerSocketFactory? = null

    fun getOrCreateCaPem(context: Context): String? {
        cachedPem?.let { return it }
        return runCatching { loadOrGenerate(context); cachedPem }
            .onFailure { Log.e(TAG, "getOrCreateCaPem: ${it.message}") }
            .getOrNull()
    }

    /** Trust-all factory for upstream (proxy → real Roblox server) connections. */
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

    /** Server socket factory presenting our CA for inbound TLS (Roblox → proxy). */
    fun getServerSocketFactory(context: Context): SSLServerSocketFactory? {
        cachedServer?.let { return it }
        return runCatching {
            loadOrGenerate(context)
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(null, null)
            ks.setKeyEntry(CERT_ALIAS, cachedKeyPair!!.private, "".toCharArray(), arrayOf(cachedCert!!))
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(ks, "".toCharArray())
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(kmf.keyManagers, null, SecureRandom())
            ctx.serverSocketFactory.also { cachedServer = it }
        }.onFailure { Log.e(TAG, "getServerSocketFactory: ${it.message}") }
         .getOrNull()
    }

    /**
     * Export CA cert and fire system install intent.
     * Settings → Security → Install certificate → CA certificate
     * Required for HTTPS interception on Android 7+ without root.
     */
    fun promptInstall(context: Context) {
        val pem  = getOrCreateCaPem(context) ?: return
        val file = File(context.cacheDir, "flagmanager_ca.crt")
        file.writeText(pem)
        val uri  = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/x-x509-ca-cert")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun clearCert(context: Context) {
        File(context.filesDir, CERT_FILE).delete()
        File(context.filesDir, KEY_FILE).delete()
        cachedPem = null; cachedKeyPair = null; cachedCert = null
        cachedFactory = null; cachedServer = null
    }

    // ── internal ──────────────────────────────────────────────────────────────

    private fun loadOrGenerate(context: Context) {
        if (cachedCert != null && cachedKeyPair != null) return
        val certFile = File(context.filesDir, CERT_FILE)
        val keyFile  = File(context.filesDir, KEY_FILE)
        if (certFile.exists() && keyFile.exists()) loadFromDisk(certFile, keyFile)
        else generate(certFile, keyFile)
    }

    private fun generate(certFile: File, keyFile: File) {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(KEY_SIZE, SecureRandom())
        val kp     = kpg.generateKeyPair()
        val now    = Date()
        val expiry = Date(now.time + VALIDITY_MS)
        val serial = BigInteger(64, SecureRandom())
        val name   = X500Name("CN=FlagManager CA, O=FlagManager, C=US")

        val cb = JcaX509v3CertificateBuilder(name, serial, now, expiry, name, kp.public)
        cb.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        cb.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))

        val signer = JcaContentSignerBuilder("SHA256WithRSAEncryption").build(kp.private)
        val cert   = JcaX509CertificateConverter().getCertificate(cb.build(signer))

        val pemStr = "-----BEGIN CERTIFICATE-----\n" +
            Base64.encodeToString(cert.encoded, Base64.DEFAULT) +
            "-----END CERTIFICATE-----\n"
        val keyPem = "-----BEGIN PRIVATE KEY-----\n" +
            Base64.encodeToString(kp.private.encoded, Base64.DEFAULT) +
            "-----END PRIVATE KEY-----\n"

        certFile.writeText(pemStr)
        keyFile.writeText(keyPem)
        cachedKeyPair = kp; cachedCert = cert; cachedPem = pemStr
        Log.d(TAG, "CA generated serial=$serial")
    }

    private fun loadFromDisk(certFile: File, keyFile: File) {
        val pem    = certFile.readText()
        val keyPem = keyFile.readText()

        fun strip(s: String, h: String, f: String) =
            Base64.decode(s.replace(h, "").replace(f, "").replace("\n", ""), Base64.DEFAULT)

        val certBytes = strip(pem, "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----")
        val keyBytes  = strip(keyPem, "-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----")

        val cert = java.security.cert.CertificateFactory.getInstance("X.509")
            .generateCertificate(certBytes.inputStream()) as X509Certificate
        val key  = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))

        cachedKeyPair = KeyPair(cert.publicKey, key)
        cachedCert    = cert
        cachedPem     = pem
        Log.d(TAG, "CA loaded from disk")
    }
}
