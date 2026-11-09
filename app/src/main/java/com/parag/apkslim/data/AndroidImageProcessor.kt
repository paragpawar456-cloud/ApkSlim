package com.parag.apkslim.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.parag.apkslim.core.ImageMode
import com.parag.apkslim.core.ImageProcessor
import java.io.ByteArrayOutputStream

/**
 * Re-encodes images with the platform codecs.
 *
 *  - LOSSLESS: PNG → lossless WebP, then decodes the result and compares every pixel with the
 *    original. If a single pixel differs, the original is kept. The file name is not changed
 *    (resources.arsc points at it); Android sniffs the real format from the file header.
 *  - LOSSY: PNG/JPEG → lossy WebP at the chosen quality. Visually close, not pixel-identical.
 *
 * PNGs that carry special chunks (9-patch data, colour profiles, 16-bit depth, animation)
 * are skipped because a re-encode could change how they render.
 */
class AndroidImageProcessor : ImageProcessor {

    override fun process(path: String, data: ByteArray, mode: ImageMode, quality: Int, minSdk: Int): ByteArray? {
        if (mode == ImageMode.OFF) return null
        val png = isPng(data)
        val jpeg = isJpeg(data)
        if (!png && !jpeg) return null
        // Lossless & alpha WebP decoding arrived in Android 4.2.1 (API 17/18).
        if (minSdk < 18) return null
        if (png && !PngChunks.isSafeToReencode(data)) return null

        val opts = BitmapFactory.Options().apply {
            inPremultiplied = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, opts) ?: return null
        try {
            if (bitmap.width * bitmap.height > MAX_PIXELS) return null
            return when (mode) {
                ImageMode.LOSSLESS -> {
                    if (!png) return null // JPEG is already lossy; re-encoding can't be lossless
                    val encoded = encode(bitmap, lossless = true, quality = 90) ?: return null
                    if (encoded.size >= data.size) return null
                    if (pixelsIdentical(bitmap, encoded)) encoded else null
                }
                ImageMode.LOSSY -> {
                    val encoded = encode(bitmap, lossless = false, quality = quality.coerceIn(1, 100)) ?: return null
                    if (encoded.size < data.size) encoded else null
                }
                ImageMode.OFF -> null
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun encode(bitmap: Bitmap, lossless: Boolean, quality: Int): ByteArray? {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (lossless) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP // quality 100 == lossless on API < 30
        }
        val q = if (lossless && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) 100 else quality
        val out = ByteArrayOutputStream(64 * 1024)
        return if (bitmap.compress(format, q, out)) out.toByteArray() else null
    }

    private fun pixelsIdentical(original: Bitmap, encoded: ByteArray): Boolean {
        val opts = BitmapFactory.Options().apply {
            inPremultiplied = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.size, opts) ?: return false
        try {
            if (decoded.width != original.width || decoded.height != original.height) return false
            val w = original.width
            val a = IntArray(w)
            val b = IntArray(w)
            for (y in 0 until original.height) {
                original.getPixels(a, 0, w, 0, y, w, 1)
                decoded.getPixels(b, 0, w, 0, y, w, 1)
                for (x in 0 until w) {
                    val pa = a[x]
                    val pb = b[x]
                    if (pa != pb) {
                        // Fully transparent pixels may legitimately lose their hidden RGB.
                        if ((pa ushr 24) == 0 && (pb ushr 24) == 0) continue
                        return false
                    }
                }
            }
            return true
        } finally {
            decoded.recycle()
        }
    }

    private fun isPng(d: ByteArray) = d.size > 8 && d[0] == 0x89.toByte() && d[1] == 'P'.code.toByte() && d[2] == 'N'.code.toByte() && d[3] == 'G'.code.toByte()
    private fun isJpeg(d: ByteArray) = d.size > 3 && d[0] == 0xFF.toByte() && d[1] == 0xD8.toByte()

    companion object {
        private const val MAX_PIXELS = 4096 * 4096
    }
}

/** Tiny PNG chunk scanner. */
internal object PngChunks {
    private val UNSAFE = setOf("npTc", "npLb", "npOl", "iCCP", "acTL", "sBIT")

    fun isSafeToReencode(d: ByteArray): Boolean {
        // IHDR bit depth is at byte 24; 16-bit images would lose precision.
        if (d.size < 33 || d[24].toInt() == 16) return false
        var pos = 8
        while (pos + 8 <= d.size) {
            val len = ((d[pos].toInt() and 0xFF) shl 24) or ((d[pos + 1].toInt() and 0xFF) shl 16) or
                ((d[pos + 2].toInt() and 0xFF) shl 8) or (d[pos + 3].toInt() and 0xFF)
            val type = String(d, pos + 4, 4, Charsets.US_ASCII)
            if (type in UNSAFE) return false
            if (type == "IEND" || len < 0) break
            pos += 12 + len
        }
        return true
    }
}
