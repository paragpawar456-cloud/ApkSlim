package com.parag.apkslim.core

import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Rewrites an APK into a smaller, UNSIGNED APK. Signing is done afterwards by [ApkSigning].
 *
 * Safety rules:
 *  - Entries keep their original compression method. STORED entries (resources.arsc,
 *    page-aligned .so files, raw assets opened with openFd) stay STORED and get aligned.
 *  - Only the ABIs the user chose to drop are removed; everything else is copied byte-for-byte
 *    (or re-deflated, which is lossless).
 *  - Images are replaced only when the [ImageProcessor] returns something meaningfully smaller.
 */
class ApkOptimizer(private val imageProcessor: ImageProcessor?) {

    fun optimize(
        input: File,
        output: File,
        options: OptimizeOptions,
        progress: ProgressListener = ProgressListener { _, _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): OptimizeStats {
        ZipFile(input).use { zip ->
            progress.onProgress(Stage.READING, 0f, input.name)
            val all = zip.entries().asSequence().filterNot { it.isDirectory }.toList()

            fun dropReason(e: ZipEntry): Int = when {
                Rules.isOldSignatureFile(e.name) -> DROP_SIGNATURE
                options.keepAbis != null && Rules.abiOf(e.name)?.let { it !in options.keepAbis } == true -> DROP_ABI
                options.stripBuildMetadata && Rules.isBuildMetadata(e.name) -> DROP_METADATA
                else -> KEEP
            }

            val kept = all.filter { dropReason(it) == KEEP }
            val removedAbi = all.filter { dropReason(it) == DROP_ABI }
            val removedMeta = all.filter { dropReason(it) == DROP_METADATA }

            // ---- 1. Images (parallel) ----
            val replacements = HashMap<String, ByteArray>()
            val imageCandidates = if (options.imageMode != ImageMode.OFF && imageProcessor != null) {
                kept.filter { Rules.isOptimizableImage(it.name, options.includeAssetImages) }
            } else emptyList()
            var imageSaved = 0L
            if (imageCandidates.isNotEmpty()) {
                val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 6)
                val pool = Executors.newFixedThreadPool(threads)
                val done = AtomicInteger()
                try {
                    val futures: List<Pair<ZipEntry, Future<ByteArray?>>> = imageCandidates.map { e ->
                        e to pool.submit<ByteArray?> {
                            if (isCancelled()) return@submit null
                            val original = zip.getInputStream(e).use { it.readBytes() }
                            val result = runCatching {
                                imageProcessor!!.process(e.name, original, options.imageMode, options.imageQuality, options.minSdk)
                            }.getOrNull()
                            val n = done.incrementAndGet()
                            progress.onProgress(Stage.IMAGES, n.toFloat() / imageCandidates.size, e.name.substringAfterLast('/'))
                            // Keep only real wins (≥ 3 % and ≥ 64 bytes) to avoid churn.
                            if (result != null && result.size < original.size * 0.97 && original.size - result.size >= 64) result else null
                        }
                    }
                    for ((e, f) in futures) {
                        if (isCancelled()) throw CancelledException()
                        f.get()?.let { bytes ->
                            replacements[e.name] = bytes
                            imageSaved += (e.compressedSize - bytes.size).coerceAtLeast(0)
                        }
                    }
                } finally {
                    pool.shutdownNow()
                    pool.awaitTermination(5, TimeUnit.SECONDS)
                }
            }

            // ---- 2. Repack ----
            val counting = CountingOutputStream(output.outputStream().buffered(1 shl 16))
            val seen = HashSet<String>()
            ZipOutputStream(counting).use { zos ->
                zos.setLevel(options.compressionLevel.coerceIn(1, 9))
                val buffer = ByteArray(1 shl 16)
                kept.forEachIndexed { i, e ->
                    if (isCancelled()) throw CancelledException()
                    if (!seen.add(e.name)) return@forEachIndexed // duplicate entry names are invalid for PackageManager
                    progress.onProgress(Stage.REPACKING, i.toFloat() / kept.size, e.name.substringAfterLast('/'))

                    val replacement = replacements[e.name]
                    val out = ZipEntry(e.name)
                    if (e.time != -1L) out.time = e.time

                    if (e.method == ZipEntry.STORED) {
                        out.method = ZipEntry.STORED
                        val size: Long
                        val crc: Long
                        if (replacement != null) {
                            size = replacement.size.toLong()
                            crc = CRC32().apply { update(replacement) }.value
                        } else {
                            size = e.size
                            crc = e.crc
                        }
                        out.size = size
                        out.compressedSize = size
                        out.crc = crc
                        out.extra = alignmentExtra(counting.count, e.name, Rules.alignmentFor(e.name))
                    } else {
                        out.method = ZipEntry.DEFLATED
                    }

                    zos.putNextEntry(out)
                    if (replacement != null) {
                        zos.write(replacement)
                    } else {
                        zip.getInputStream(e).use { ins ->
                            while (true) {
                                val n = ins.read(buffer)
                                if (n < 0) break
                                zos.write(buffer, 0, n)
                            }
                        }
                    }
                    zos.closeEntry()
                }
            }
            progress.onProgress(Stage.REPACKING, 1f, "")

            return OptimizeStats(
                entriesIn = all.size,
                entriesOut = seen.size,
                removedAbiEntries = removedAbi.size,
                removedAbiBytes = removedAbi.sumOf { it.compressedSize },
                removedMetadataBytes = removedMeta.sumOf { it.compressedSize },
                imagesOptimized = replacements.size,
                imagesConsidered = imageCandidates.size,
                imageBytesSaved = imageSaved,
            )
        }
    }

    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
            private set
        override fun write(b: Int) { out.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
    }

    companion object {
        private const val KEEP = 0
        private const val DROP_SIGNATURE = 1
        private const val DROP_ABI = 2
        private const val DROP_METADATA = 3

        private const val LOCAL_HEADER_SIZE = 30
        private const val ALIGNMENT_EXTRA_ID = 0xD935 // same extra field apksig/zipalign use

        /**
         * Builds an extra field that pads the local file header so the entry data starts on an
         * [alignment] boundary. apksig reads this field and preserves the alignment when signing.
         */
        fun alignmentExtra(headerOffset: Long, name: String, alignment: Int): ByteArray {
            val nameLen = name.toByteArray(Charsets.UTF_8).size
            val dataOffsetWithoutExtra = headerOffset + LOCAL_HEADER_SIZE + nameLen
            val minExtra = 6 // id(2) + size(2) + alignment(2)
            val padding = ((alignment - ((dataOffsetWithoutExtra + minExtra) % alignment)) % alignment).toInt()
            val total = minExtra + padding
            val extra = ByteArray(total)
            extra[0] = (ALIGNMENT_EXTRA_ID and 0xFF).toByte()
            extra[1] = (ALIGNMENT_EXTRA_ID shr 8 and 0xFF).toByte()
            val dataSize = total - 4
            extra[2] = (dataSize and 0xFF).toByte()
            extra[3] = (dataSize shr 8 and 0xFF).toByte()
            extra[4] = (alignment and 0xFF).toByte()
            extra[5] = (alignment shr 8 and 0xFF).toByte()
            return extra
        }
    }
}
