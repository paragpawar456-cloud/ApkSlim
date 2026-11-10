package com.parag.apkslim.core

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * analyze → optimize → (sign → verify | pack .slim → check), with one progress stream.
 *
 * Handles single APKs and split installs (base.apk + split_config.*.apk). For split installs
 * every part is optimized and signed with the same key, and the result is an ".apks" bundle
 * (a zip of the APKs) that ApkSlim installs in one session.
 */
class SlimPipeline(private val imageProcessor: ImageProcessor?) {

    fun run(
        input: File,
        output: File,
        workDir: File,
        options: OptimizeOptions,
        key: SigningKey,
        progress: ProgressListener = ProgressListener { _, _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): OptimizeResult = runAll(listOf(NamedApk("base.apk", input)), ArchiveMeta("", "", "", options.minSdk), output, workDir, options, key, progress, isCancelled)

    fun runAll(
        inputs: List<NamedApk>,
        meta: ArchiveMeta,
        output: File,
        workDir: File,
        options: OptimizeOptions,
        key: SigningKey?,
        progress: ProgressListener = ProgressListener { _, _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): OptimizeResult {
        require(inputs.isNotEmpty())
        val start = System.currentTimeMillis()
        val runDir = File(workDir, "run-${System.nanoTime()}").apply { mkdirs() }
        val before = ApkAnalyzer.analyzeAll(inputs.map { it.file })
        try {
            // 1. Optimize every part.
            var stats: OptimizeStats? = null
            val unsigned = inputs.mapIndexed { i, apk ->
                if (isCancelled()) throw CancelledException()
                val out = File(runDir, "u$i.apk")
                val s = ApkOptimizer(imageProcessor).optimize(apk.file, out, options, progress, isCancelled)
                stats = stats?.plus(s) ?: s
                NamedApk(apk.name, out)
            }
            output.parentFile?.mkdirs()

            val verify: VerifyResult
            var after: Map<Category, Long>? = null
            if (options.format == OutputFormat.ARCHIVE) {
                // 2a. Pack into one solid XZ archive, then read it back to prove it is intact.
                SlimArchive.pack(unsigned, meta.copy(apkNames = inputs.map { it.name }), output, progress, isCancelled)
                SlimArchive.check(output, progress)
                verify = VerifyResult(true, listOf("archive"), emptyList())
            } else {
                // 2b. Sign every part with the same key.
                val k = key ?: error("A signing key is required for APK output")
                val signed = unsigned.mapIndexed { i, apk ->
                    progress.onProgress(Stage.SIGNING, i.toFloat() / unsigned.size, apk.name)
                    val out = if (unsigned.size == 1) output else File(runDir, "s$i-${apk.name}")
                    ApkSigning.sign(apk.file, out, k, options.minSdk)
                    NamedApk(apk.name, out)
                }
                progress.onProgress(Stage.VERIFYING, 0f, "")
                val results = signed.map { ApkSigning.verify(it.file, options.minSdk) }
                verify = VerifyResult(results.all { it.verified }, results.first().schemes, results.flatMap { it.errors })
                after = ApkAnalyzer.analyzeAll(signed.map { it.file }).byCategory
                if (signed.size > 1) bundle(signed, output)
            }
            progress.onProgress(Stage.DONE, 1f, "")

            return OptimizeResult(
                input = inputs[0].file,
                output = output,
                originalSize = inputs.sumOf { it.file.length() },
                optimizedSize = output.length(),
                before = before.byCategory,
                after = after,
                stats = stats!!,
                verify = verify,
                durationMs = System.currentTimeMillis() - start,
                format = options.format,
                apkCount = inputs.size,
            )
        } catch (t: Throwable) {
            output.delete()
            throw t
        } finally {
            runDir.deleteRecursively()
        }
    }

    /**
     * Restores a .slim archive into signed APK(s) in [outDir].
     * Returns the archive metadata and the installable files (one per split).
     */
    fun restore(
        archive: File,
        outDir: File,
        workDir: File,
        key: SigningKey,
        progress: ProgressListener = ProgressListener { _, _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): Pair<ArchiveMeta, List<File>> {
        val runDir = File(workDir, "restore-${System.nanoTime()}").apply { mkdirs() }
        try {
            val (meta, unsigned) = SlimArchive.unpack(archive, runDir, progress, isCancelled)
            outDir.mkdirs()
            val signed = unsigned.mapIndexed { i, apk ->
                progress.onProgress(Stage.SIGNING, i.toFloat() / unsigned.size, apk.name)
                val out = File(outDir, apk.file.name)
                ApkSigning.sign(apk.file, out, key, meta.minSdk)
                val v = ApkSigning.verify(out, meta.minSdk)
                check(v.verified) { "Restored ${apk.name} failed verification: ${v.errors.firstOrNull()}" }
                out
            }
            progress.onProgress(Stage.DONE, 1f, "")
            return meta to signed
        } finally {
            runDir.deleteRecursively()
        }
    }

    companion object {
        /** .apks bundle: a plain zip holding the split APKs, stored (they're already compressed). */
        fun bundle(apks: List<NamedApk>, output: File) {
            ZipOutputStream(output.outputStream().buffered(1 shl 16)).use { z ->
                for (a in apks) {
                    val crc = CRC32()
                    a.file.inputStream().buffered().use { ins ->
                        val buf = ByteArray(1 shl 16)
                        while (true) { val n = ins.read(buf); if (n < 0) break; crc.update(buf, 0, n) }
                    }
                    val e = ZipEntry(a.name).apply {
                        method = ZipEntry.STORED; size = a.file.length(); compressedSize = size; this.crc = crc.value
                    }
                    z.putNextEntry(e)
                    a.file.inputStream().use { it.copyTo(z, 1 shl 16) }
                    z.closeEntry()
                }
            }
        }

        /** Extracts an .apks bundle into [dir] and returns the APK files. */
        fun unbundle(apks: File, dir: File): List<File> {
            dir.mkdirs()
            return java.util.zip.ZipFile(apks).use { z ->
                val entries = z.entries().asSequence().filter { it.name.endsWith(".apk") && !it.isDirectory }.toList()
                require(entries.isNotEmpty()) { "This .apks file has no APKs inside" }
                require(entries.size <= Limits.MAX_PARTS) { "This .apks file has too many parts" }
                val used = HashSet<String>()
                var total = 0L
                entries.map { e ->
                    // Only the bare file name is used, so "../" in an entry name cannot escape [dir].
                    var name = File(e.name).name.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.').ifEmpty { "part.apk" }
                    var i = 1
                    while (!used.add(name)) name = "${i++}_$name"
                    val f = File(dir, name)
                    z.getInputStream(e).use { ins ->
                        f.outputStream().use { out ->
                            val buf = ByteArray(1 shl 16)
                            while (true) {
                                val n = ins.read(buf)
                                if (n < 0) break
                                total += n
                                require(total <= Limits.MAX_TOTAL_BYTES) { "This .apks file is too large" }
                                out.write(buf, 0, n)
                            }
                        }
                    }
                    f
                }
            }
        }
    }
}
