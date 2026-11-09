package com.parag.apkslim.core

import java.io.File

/** High-level buckets used for the size breakdown chart and before/after comparison. */
enum class Category(val label: String) {
    NATIVE("Native libraries"),
    DEX("Code (DEX)"),
    IMAGES("Images"),
    RESOURCES("Other resources"),
    RESOURCE_TABLE("Resource table"),
    ASSETS("Assets"),
    SIGNATURE("Signature & metadata"),
    OTHER("Other"),
}

data class EntryInfo(
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val stored: Boolean,
    val category: Category,
)

data class ApkAnalysis(
    val file: File,
    val fileSize: Long,
    val entries: List<EntryInfo>,
    /** Compressed bytes per category. */
    val byCategory: Map<Category, Long>,
    /** Compressed bytes of native libraries per ABI (e.g. "arm64-v8a" -> 12 MB). */
    val abiSizes: Map<String, Long>,
    /** True when the APK is a configuration/feature split rather than a complete app. */
    val isSplit: Boolean,
) {
    val abis: List<String> get() = abiSizes.keys.sorted()
    val entryCount: Int get() = entries.size
    fun largest(n: Int): List<EntryInfo> = entries.sortedByDescending { it.compressedSize }.take(n)
}

enum class ImageMode { OFF, LOSSLESS, LOSSY }

data class OptimizeOptions(
    /** ABIs to keep. null = keep every ABI that is present. */
    val keepAbis: Set<String>? = null,
    val imageMode: ImageMode = ImageMode.LOSSLESS,
    /** 1..100, only used for [ImageMode.LOSSY]. */
    val imageQuality: Int = 85,
    /** Also touch images under assets/ (off by default: some engines check file magic). */
    val includeAssetImages: Boolean = false,
    /** Deflate level 1..9 for entries that were already compressed. */
    val compressionLevel: Int = 9,
    /** Remove build-time metadata such as META-INF/ *.version and Maven descriptors. */
    val stripBuildMetadata: Boolean = true,
    /** minSdkVersion of the APK being processed (drives signature scheme & WebP support). */
    val minSdk: Int = 21,
)

data class SavingsEstimate(
    val abiBytes: Long,
    val imageBytes: Long,
    val compressionBytes: Long,
    val metadataBytes: Long,
) {
    val total: Long get() = abiBytes + imageBytes + compressionBytes + metadataBytes
}

data class OptimizeStats(
    val entriesIn: Int,
    val entriesOut: Int,
    val removedAbiEntries: Int,
    val removedAbiBytes: Long,
    val removedMetadataBytes: Long,
    val imagesOptimized: Int,
    val imagesConsidered: Int,
    val imageBytesSaved: Long,
)

data class VerifyResult(val verified: Boolean, val schemes: List<String>, val errors: List<String>)

data class OptimizeResult(
    val input: File,
    val output: File,
    val originalSize: Long,
    val optimizedSize: Long,
    val before: Map<Category, Long>,
    val after: Map<Category, Long>,
    val stats: OptimizeStats,
    val verify: VerifyResult,
    val durationMs: Long,
) {
    val savedBytes: Long get() = originalSize - optimizedSize
    val savedPercent: Float get() = if (originalSize == 0L) 0f else savedBytes * 100f / originalSize
}

/**
 * Re-encodes one image. Return the new bytes, or null to keep the original.
 * Implementations must guarantee the result decodes to an image the platform can load.
 */
fun interface ImageProcessor {
    fun process(path: String, data: ByteArray, mode: ImageMode, quality: Int, minSdk: Int): ByteArray?
}

fun interface ProgressListener {
    fun onProgress(stage: Stage, fraction: Float, detail: String)
}

enum class Stage(val label: String) {
    READING("Reading APK"),
    IMAGES("Optimizing images"),
    REPACKING("Repacking & aligning"),
    SIGNING("Signing"),
    VERIFYING("Verifying signature"),
    DONE("Done"),
}

class CancelledException : RuntimeException("Cancelled")
