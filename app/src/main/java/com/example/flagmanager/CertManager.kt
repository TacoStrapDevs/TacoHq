package com.example.flagmanager

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
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
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object CertManager {

    private const val TAG = "CertManager"
    private const val CERT_FILE = "mitm_ca.pem"
    private const val KEY_FILE = "mitm_ca.key"
    private const val KEY_SIZE = 2048
    private const val CERT_ALIAS = "mitm_ca"
    private const val CA_VALIDITY_MS = 10L * 365 * 24 * 60 * 60 * 1000
    private const val LEAF_VALIDITY_MS = 365L * 24 * 60 * 60 * 1000

    data class Leaf(val keyPair: KeyPair, val cert: X509Certificate)

    @Volatile private var cachedPem: String? = null
    @Volatile private var cachedKeyPair: KeyPair? = null
    @Volatile private var cachedCert: X509Certificate? = null
    @Volatile private var cachedFactory: SSLSocketFactory? = null
    @Volatile private var cachedServer: SSLServerSocketFactory? = null

    private val leafCache = ConcurrentHashMap<String, Leaf>()

    fun getKeyPair(): KeyPair? = cachedKeyPair
    fun getCert(): X509Certificate? = cachedCert

    fun getOrCreateCaPem(context: Context): String? {
        cachedPem?.let { return it }
        return runCatching {
            loadOrGenerate(context)
            cachedPem
        }.onFailure { Log.e(TAG, "getOrCreateCaPem: ${it.message}") }.getOrNull()
    }

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

    fun getServerSocketFactory(context: Context): SSLServerSocketFactory? {
        cachedServer?.let { return it }
        return runCatching {
            loadOrGenerate(context)
            val kp = cachedKeyPair ?: return@runCatching null
            val cert = cachedCert ?: return@runCatching null
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(null, null)
            ks.setKeyEntry(CERT_ALIAS, kp.private, CharArray(0), arrayOf(cert))
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(ks, CharArray(0))
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(kmf.keyManagers, null, SecureRandom())
            ctx.serverSocketFactory.also { cachedServer = it }
        }.onFailure { Log.e(TAG, "getServerSocketFactory: ${it.message}") }.getOrNull()
    }

    fun generateLeafFor(host: String): Leaf? {
        leafCache[host]?.let { return it }
        return try {
            ensureLoadedOrThrow()
            val ca = cachedKeyPair ?: return null
            val caCert = cachedCert ?: return null

            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(KEY_SIZE, SecureRandom())
            val kp = kpg.generateKeyPair()
            val now = Date()
            val expiry = Date(now.time + LEAF_VALIDITY_MS)
            val serial = BigInteger(64, SecureRandom())
            val caName = X500Name(caCert.subjectX500Principal.name)
            val leafName = X500Name("CN=$host, O=FlagManager, C=US")

            val builder = JcaX509v3CertificateBuilder(
                caName, serial, now, expiry, leafName, kp.public
            )
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
            builder.addExtension(
                Extension.keyUsage, true,
                KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
            )
            val san = GeneralNames(arrayOf(GeneralName(GeneralName.dNSName, host)))
            builder.addExtension(Extension.subjectAlternativeName, false, san)

            val signer = JcaContentSignerBuilder("SHA256WithRSAEncryption").build(ca.private)
            val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))

            val leaf = Leaf(kp, cert)
            leafCache[host] = leaf
            leaf
        } catch (e: Exception) {
            Log.e(TAG, "generateLeafFor($host): ${e.message}")
            null
        }
    }

    fun promptInstall(context: Context) {
        val pem = getOrCreateCaPem(context) ?: return
        val file = File(context.cacheDir, "flagmanager_ca.crt")
        file.writeText(pem)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/x-x509-ca-cert")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun clearCert(context: Context) {
        File(context.filesDir, CERT_FILE).delete()
        File(context.filesDir, KEY_FILE).delete()
        cachedPem = null
        cachedKeyPair = null
        cachedCert = null
        cachedFactory = null
        cachedServer = null
        leafCache.clear()
    }

    private fun ensureLoadedOrThrow() {
        if (cachedCert != null && cachedKeyPair != null) return
        val ctx = MitmProxy.vpnService ?: return
        loadOrGenerate(ctx)
    }

    private fun loadOrGenerate(context: Context) {
        if (cachedCert != null && cachedKeyPair != null) return
        val certFile = File(context.filesDir, CERT_FILE)
        val keyFile = File(context.filesDir, KEY_FILE)
        if (certFile.exists() && keyFile.exists()) loadFromDisk(certFile, keyFile)
        else generate(certFile, keyFile)
    }

    private fun generate(certFile: File, keyFile: File) {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(KEY_SIZE, SecureRandom())
        val kp = kpg.generateKeyPair()
        val now = Date()
        val expiry = Date(now.time + CA_VALIDITY_MS)
        val serial = BigInteger(64, SecureRandom())
        val name = X500Name("CN=FlagManager CA, O=FlagManager, C=US")

        val cb = JcaX509v3CertificateBuilder(name, serial, now, expiry, name, kp.public)
        cb.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        cb.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))

        val signer = JcaContentSignerBuilder("SHA256WithRSAEncryption").build(kp.private)
        val cert = JcaX509CertificateConverter().getCertificate(cb.build(signer))

        val pemStr = "-----BEGIN CERTIFICATE-----\n" +
            Base64.encodeToString(cert.encoded, Base64.DEFAULT) +
            "-----END CERTIFICATE-----\n"
        val keyPem = "-----BEGIN PRIVATE KEY-----\n" +
            Base64.encodeToString(kp.private.encoded, Base64.DEFAULT) +
            "-----END PRIVATE KEY-----\n"

        certFile.writeText(pemStr)
        keyFile.writeText(keyPem)
        cachedKeyPair = kp
        cachedCert = cert
        cachedPem = pemStr
        Log.d(TAG, "CA generated serial=$serial")
    }

    private fun loadFromDisk(certFile: File, keyFile: File) {
        val pem = certFile.readText()
        val keyPem = keyFile.readText()

        fun strip(s: String, h: String, f: String) =
            Base64.decode(s.replace(h, "").replace(f, "").replace("\n", ""), Base64.DEFAULT)

        val certBytes = strip(pem, "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----")
        val keyBytes = strip(keyPem, "-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----")

        val cert = java.security.cert.CertificateFactory.getInstance("X.509")
            .generateCertificate(certBytes.inputStream()) as X509Certificate
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))

        cachedKeyPair = KeyPair(cert.publicKey, key)
        cachedCert = cert
        cachedPem = pem
        Log.d(TAG, "CA loaded from disk")
    }
}
