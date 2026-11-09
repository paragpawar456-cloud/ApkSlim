package com.parag.apkslim.core

/** Path rules shared by the analyzer, the estimator and the optimizer so they always agree. */
object Rules {
    private val IMAGE_EXT = listOf(".png", ".jpg", ".jpeg", ".webp", ".gif")

    fun categorize(name: String): Category {
        val lower = name.lowercase()
        return when {
            lower.startsWith("lib/") && lower.endsWith(".so") -> Category.NATIVE
            lower.startsWith("lib/") -> Category.NATIVE
            lower.matches(Regex("classes\\d*\\.dex")) -> Category.DEX
            lower == "resources.arsc" -> Category.RESOURCE_TABLE
            lower.startsWith("res/") && IMAGE_EXT.any { lower.endsWith(it) } -> Category.IMAGES
            lower.startsWith("res/") -> Category.RESOURCES
            lower.startsWith("assets/") && IMAGE_EXT.any { lower.endsWith(it) } -> Category.IMAGES
            lower.startsWith("assets/") -> Category.ASSETS
            lower.startsWith("meta-inf/") -> Category.SIGNATURE
            else -> Category.OTHER
        }
    }

    /** "lib/arm64-v8a/libfoo.so" -> "arm64-v8a" */
    fun abiOf(name: String): String? {
        if (!name.startsWith("lib/")) return null
        val parts = name.split('/')
        return if (parts.size >= 3 && parts[1].isNotEmpty()) parts[1] else null
    }

    /**
     * Images we are willing to re-encode. 9-patch PNGs carry layout chunks that a re-encode
     * would destroy, so they are never touched. WebP/GIF are left alone (already efficient /
     * may be animated).
     */
    fun isOptimizableImage(name: String, includeAssets: Boolean): Boolean {
        val lower = name.lowercase()
        val inScope = lower.startsWith("res/") || (includeAssets && lower.startsWith("assets/"))
        if (!inScope) return false
        if (lower.endsWith(".9.png")) return false
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
    }

    /** Old JAR signature files; always dropped because the APK is re-signed. */
    fun isOldSignatureFile(name: String): Boolean {
        val upper = name.uppercase()
        if (!upper.startsWith("META-INF/") || upper.count { it == '/' } != 1) return false
        return upper == "META-INF/MANIFEST.MF" ||
            upper.endsWith(".SF") || upper.endsWith(".RSA") ||
            upper.endsWith(".DSA") || upper.endsWith(".EC")
    }

    /**
     * Build-time files that the Android runtime never reads. META-INF/services is
     * deliberately NOT included (ServiceLoader needs it) and neither are license files.
     */
    fun isBuildMetadata(name: String): Boolean {
        return (name.startsWith("META-INF/") && name.endsWith(".version") && name.count { it == '/' } == 1) ||
            name.startsWith("META-INF/maven/") ||
            name.startsWith("META-INF/com/android/build/gradle/") ||
            name.startsWith("META-INF/proguard/") ||
            name == "META-INF/version-control-info.textproto" ||
            name == "DebugProbesKt.bin" ||
            name == "kotlin-tooling-metadata.json"
    }

    /** Alignment for STORED entries: native libs must be page aligned (16 KB covers 4 KB too). */
    fun alignmentFor(name: String): Int = if (name.endsWith(".so")) 16384 else 4
}
