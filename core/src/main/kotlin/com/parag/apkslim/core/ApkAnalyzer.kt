package com.parag.apkslim.core

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object ApkAnalyzer {

    fun analyze(file: File): ApkAnalysis {
        ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map {
                    EntryInfo(
                        name = it.name,
                        size = it.size.coerceAtLeast(0),
                        compressedSize = it.compressedSize.coerceAtLeast(0),
                        stored = it.method == ZipEntry.STORED,
                        category = Rules.categorize(it.name),
                    )
                }.toList()

            val byCategory = Category.entries.associateWith { c ->
                entries.filter { it.category == c }.sumOf { it.compressedSize }
            }.toMutableMap()
            // Zip headers, central directory and the APK Signing Block are not entries;
            // fold that overhead into "Signature & metadata" so the chart adds up to the file size.
            val overhead = file.length() - entries.sumOf { it.compressedSize }
            if (overhead > 0) byCategory[Category.SIGNATURE] = byCategory.getValue(Category.SIGNATURE) + overhead

            val abiSizes = entries.mapNotNull { e -> Rules.abiOf(e.name)?.let { it to e.compressedSize } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, v) -> v.sum() }

            // Config splits (split_config.arm64_v8a.apk, split_config.xxhdpi.apk, ...) carry no code.
            // The app layer additionally checks PackageManager for installed split apps.
            val isSplit = entries.none { it.category == Category.DEX }

            return ApkAnalysis(file, file.length(), entries, byCategory, abiSizes, isSplit)
        }
    }

    /**
     * Estimates savings for [options]. ABI and metadata numbers are exact; image and
     * recompression numbers are conservative averages measured on typical apps.
     */
    fun estimate(a: ApkAnalysis, options: OptimizeOptions): SavingsEstimate {
        val abi = options.keepAbis?.let { keep ->
            a.entries.filter { e -> Rules.abiOf(e.name)?.let { it !in keep } == true }.sumOf { it.compressedSize }
        } ?: 0L

        val images = a.entries.filter { Rules.isOptimizableImage(it.name, options.includeAssetImages) }
        val pngBytes = images.filter { it.name.lowercase().endsWith(".png") }.sumOf { it.compressedSize }
        val jpgBytes = images.sumOf { it.compressedSize } - pngBytes
        val imageSave = when (options.imageMode) {
            ImageMode.OFF -> 0L
            ImageMode.LOSSLESS -> (pngBytes * 0.22).toLong()
            ImageMode.LOSSY -> {
                val q = options.imageQuality.coerceIn(1, 100)
                val pngFactor = 0.70 - (q - 50) * 0.006 // q50 ≈ 70%, q100 ≈ 40%
                val jpgFactor = (0.45 - (q - 50) * 0.008).coerceAtLeast(0.05)
                (pngBytes * pngFactor + jpgBytes * jpgFactor).toLong()
            }
        }

        val removedNames = a.entries.filter { e ->
            (options.keepAbis != null && Rules.abiOf(e.name)?.let { it !in options.keepAbis } == true)
        }.map { it.name }.toSet()
        val deflated = a.entries.filter { !it.stored && it.name !in removedNames && !Rules.isOptimizableImage(it.name, options.includeAssetImages) }
            .sumOf { it.compressedSize }
        val levelFactor = when {
            options.compressionLevel >= 9 -> 0.006
            options.compressionLevel >= 7 -> 0.003
            options.compressionLevel >= 5 -> 0.0
            else -> -0.03 // lower levels make the APK bigger
        }
        val compression = (deflated * levelFactor).toLong()

        val oldSig = a.entries.filter { Rules.isOldSignatureFile(it.name) }.sumOf { it.compressedSize }
        val meta = if (options.stripBuildMetadata) a.entries.filter { Rules.isBuildMetadata(it.name) }.sumOf { it.compressedSize } else 0L
        // v1 signature files are only regenerated for minSdk < 24.
        val sigDelta = if (options.minSdk >= 24) oldSig else 0L

        return SavingsEstimate(abi, imageSave, compression, meta + sigDelta)
    }
}
