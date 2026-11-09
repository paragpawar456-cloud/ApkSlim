package com.parag.apkslim.data

import android.content.Context
import android.net.Uri
import com.parag.apkslim.core.SelfSignedCertificate
import com.parag.apkslim.core.SigningKey
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate

data class SigningInfo(
    val usingCustom: Boolean,
    val subject: String,
    val sha256: String,
    val validUntil: Long,
)

/**
 * Two signing options:
 *  1. ApkSlim key: an RSA-2048 key generated on first use and stored as PKCS#12 in private
 *     storage. The user can export it (.p12) to keep signing future versions with the same key.
 *  2. Custom keystore: the user imports their own .p12/.pfx/.bks. If it is the app's original
 *     release key, the optimized APK can update the installed app.
 */
class SigningKeyManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("signing", Context.MODE_PRIVATE)
    private val secrets = SecretBox(context)
    private val defaultFile get() = File(context.filesDir, "keys/apkslim.p12")
    private val customFile get() = File(context.filesDir, "keys/custom.keystore")

    val hasCustom: Boolean get() = customFile.exists() && prefs.getString("custom_alias", null) != null
    var useCustom: Boolean
        get() = prefs.getBoolean("use_custom", false) && hasCustom
        set(v) = prefs.edit().putBoolean("use_custom", v).apply()

    @Synchronized
    fun currentKey(): SigningKey = if (useCustom) loadCustom() else loadOrCreateDefault()

    fun info(): SigningInfo {
        val key = currentKey()
        val cert = key.certificates.first()
        return SigningInfo(
            usingCustom = useCustom,
            subject = cert.subjectX500Principal.name,
            sha256 = fingerprint(cert),
            validUntil = cert.notAfter.time,
        )
    }

    // ---- default key ----

    private fun loadOrCreateDefault(): SigningKey {
        val password = prefs.getString("default_pw", null)?.let { secrets.decrypt(it) }
        if (defaultFile.exists() && password != null) {
            runCatching { return load(defaultFile, "PKCS12", password, ALIAS, password) }
        }
        val pw = randomPassword()
        val (kp, cert) = SelfSignedCertificate.generate("ApkSlim re-signed", "ApkSlim")
        val ks = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        ks.setKeyEntry(ALIAS, kp.private, pw.toCharArray(), arrayOf(cert))
        defaultFile.parentFile?.mkdirs()
        defaultFile.outputStream().use { ks.store(it, pw.toCharArray()) }
        prefs.edit().putString("default_pw", secrets.encrypt(pw)).apply()
        return SigningKey(kp.private, listOf(cert))
    }

    /** Exports the ApkSlim key as .p12 protected by [password], for backup / use with apksigner. */
    fun exportDefault(target: Uri, password: String) {
        val key = loadOrCreateDefault()
        val ks = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        ks.setKeyEntry(ALIAS, key.privateKey, password.toCharArray(), key.certificates.toTypedArray())
        context.contentResolver.openOutputStream(target)?.use { ks.store(it, password.toCharArray()) }
            ?: error("Cannot write to the chosen file")
    }

    // ---- custom key ----

    /** Imports a keystore, validating the password and alias before saving anything. */
    fun importCustom(uri: Uri, fileName: String, storePassword: String, alias: String?, keyPassword: String?): SigningInfo {
        val type = if (fileName.lowercase().endsWith(".bks")) "BKS" else "PKCS12"
        val tmp = File(context.cacheDir, "import.keystore")
        context.contentResolver.openInputStream(uri)?.use { ins -> tmp.outputStream().use { ins.copyTo(it) } }
            ?: error("Cannot read the keystore file")
        try {
            val ks = KeyStore.getInstance(type).apply { tmp.inputStream().use { load(it, storePassword.toCharArray()) } }
            val chosen = alias?.takeIf { it.isNotBlank() } ?: ks.aliases().toList().firstOrNull { ks.isKeyEntry(it) }
                ?: error("No private key found in this keystore")
            val kp = keyPassword?.takeIf { it.isNotEmpty() } ?: storePassword
            load(tmp, type, storePassword, chosen, kp) // throws if wrong
            customFile.parentFile?.mkdirs()
            tmp.copyTo(customFile, overwrite = true)
            prefs.edit()
                .putString("custom_type", type)
                .putString("custom_alias", chosen)
                .putString("custom_store_pw", secrets.encrypt(storePassword))
                .putString("custom_key_pw", secrets.encrypt(kp))
                .putBoolean("use_custom", true)
                .apply()
            return info()
        } finally {
            tmp.delete()
        }
    }

    fun removeCustom() {
        customFile.delete()
        prefs.edit().remove("custom_type").remove("custom_alias").remove("custom_store_pw")
            .remove("custom_key_pw").putBoolean("use_custom", false).apply()
    }

    private fun loadCustom(): SigningKey {
        val type = prefs.getString("custom_type", "PKCS12")!!
        val alias = prefs.getString("custom_alias", null) ?: error("No custom key")
        val sp = secrets.decrypt(prefs.getString("custom_store_pw", "")!!)
        val kp = secrets.decrypt(prefs.getString("custom_key_pw", "")!!)
        return load(customFile, type, sp, alias, kp)
    }

    private fun load(file: File, type: String, storePw: String, alias: String, keyPw: String): SigningKey {
        val ks = KeyStore.getInstance(type).apply { file.inputStream().use { load(it, storePw.toCharArray()) } }
        val key = ks.getKey(alias, keyPw.toCharArray()) as? PrivateKey ?: error("Alias '$alias' has no private key")
        val chain = ks.getCertificateChain(alias)?.map { it as X509Certificate }
            ?: listOf(ks.getCertificate(alias) as X509Certificate)
        return SigningKey(key, chain)
    }

    private fun randomPassword(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    }

    companion object {
        private const val ALIAS = "apkslim"

        fun fingerprint(cert: X509Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02X".format(it) }
    }
}
