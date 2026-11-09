package com.parag.apkslim.core

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.File
import java.security.PrivateKey
import java.security.cert.X509Certificate

data class SigningKey(val privateKey: PrivateKey, val certificates: List<X509Certificate>, val name: String = "CERT")

/** Thin wrapper over apksig (the same library Android's `apksigner` tool uses). */
object ApkSigning {

    fun sign(input: File, output: File, key: SigningKey, minSdk: Int) {
        val config = ApkSigner.SignerConfig.Builder(key.name, key.privateKey, key.certificates).build()
        val builder = ApkSigner.Builder(listOf(config))
            .setInputApk(input)
            .setOutputApk(output)
            .setMinSdkVersion(minSdk)
            // v1 (JAR) signatures are only needed below Android 7.0 and add one digest per file,
            // so they are skipped when the app can't run there anyway.
            .setV1SigningEnabled(minSdk < 24)
            .setV2SigningEnabled(true)
        // Newer apksig versions: enable v3 and 16 KB native-lib alignment when available.
        callIfExists(builder, "setV3SigningEnabled", java.lang.Boolean.TYPE, true)
        callIfExists(builder, "setLibraryPageAlignmentBytes", Integer.TYPE, 16384)
        builder.build().sign()
    }

    fun verify(apk: File, minSdk: Int): VerifyResult {
        return try {
            val result = ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(minSdk).build().verify()
            val schemes = buildList {
                if (result.isVerifiedUsingV1Scheme) add("v1")
                if (result.isVerifiedUsingV2Scheme) add("v2")
                if (boolMethod(result, "isVerifiedUsingV3Scheme")) add("v3")
            }
            VerifyResult(result.isVerified, schemes, result.errors.map { it.toString() })
        } catch (t: Throwable) {
            VerifyResult(false, emptyList(), listOf(t.message ?: t.toString()))
        }
    }

    private fun callIfExists(target: Any, method: String, type: Class<*>, arg: Any) {
        runCatching { target.javaClass.getMethod(method, type).invoke(target, arg) }
    }

    private fun boolMethod(target: Any, method: String): Boolean =
        runCatching { target.javaClass.getMethod(method).invoke(target) as Boolean }.getOrDefault(false)
}
