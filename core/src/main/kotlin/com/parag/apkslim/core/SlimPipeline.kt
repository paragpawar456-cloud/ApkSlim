package com.parag.apkslim.core

import java.io.File

/** analyze → optimize → sign → verify, with one progress stream. */
class SlimPipeline(private val imageProcessor: ImageProcessor?) {

    fun run(
        input: File,
        output: File,
        workDir: File,
        options: OptimizeOptions,
        key: SigningKey,
        progress: ProgressListener = ProgressListener { _, _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): OptimizeResult {
        val start = System.currentTimeMillis()
        workDir.mkdirs()
        val before = ApkAnalyzer.analyze(input)
        val unsigned = File(workDir, "unsigned-${System.nanoTime()}.apk")
        try {
            val stats = ApkOptimizer(imageProcessor).optimize(input, unsigned, options, progress, isCancelled)
            if (isCancelled()) throw CancelledException()

            progress.onProgress(Stage.SIGNING, 0f, "")
            output.parentFile?.mkdirs()
            ApkSigning.sign(unsigned, output, key, options.minSdk)
            progress.onProgress(Stage.SIGNING, 1f, "")

            progress.onProgress(Stage.VERIFYING, 0f, "")
            val verify = ApkSigning.verify(output, options.minSdk)
            val after = ApkAnalyzer.analyze(output)
            progress.onProgress(Stage.DONE, 1f, "")

            return OptimizeResult(
                input = input,
                output = output,
                originalSize = input.length(),
                optimizedSize = output.length(),
                before = before.byCategory,
                after = after.byCategory,
                stats = stats,
                verify = verify,
                durationMs = System.currentTimeMillis() - start,
            )
        } catch (t: Throwable) {
            output.delete()
            throw t
        } finally {
            unsigned.delete()
        }
    }
}
