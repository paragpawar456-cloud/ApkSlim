package com.parag.apkslim.core

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.Date
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PipelineTest {

    private fun key(): SigningKey {
        val (kp, cert) = SelfSignedCertificate.generate("ApkSlim Test", "ApkSlim")
        return SigningKey(kp.private, listOf(cert))
    }

    @Test
    fun selfSignedCertificateIsValidAndStorableInPkcs12() {
        val (kp, cert) = SelfSignedCertificate.generate("ApkSlim re-signed", "ApkSlim")
        cert.checkValidity()
        assertTrue(cert.subjectX500Principal.name.contains("CN=ApkSlim re-signed"))
        assertTrue(cert.notAfter.after(Date(System.currentTimeMillis() + 29L * 365 * 86_400_000)))
        val ks = java.security.KeyStore.getInstance("PKCS12").apply { load(null, null) }
        ks.setKeyEntry("slim", kp.private, "pw".toCharArray(), arrayOf(cert))
        val bytes = java.io.ByteArrayOutputStream().also { ks.store(it, "pw".toCharArray()) }.toByteArray()
        val back = java.security.KeyStore.getInstance("PKCS12").apply { load(bytes.inputStream(), "pw".toCharArray()) }
        assertEquals(cert, back.getCertificate("slim"))
    }

    private fun ZipOutputStream.stored(name: String, data: ByteArray) {
        val e = ZipEntry(name).apply {
            method = ZipEntry.STORED; size = data.size.toLong(); compressedSize = size
            crc = CRC32().apply { update(data) }.value
        }
        putNextEntry(e); write(data); closeEntry()
    }

    private fun ZipOutputStream.deflated(name: String, data: ByteArray) {
        putNextEntry(ZipEntry(name)); write(data); closeEntry()
    }

    private fun fakeApk(dir: File): File {
        val f = File(dir, "in.apk")
        val rnd = Random(1)
        val text = ("public final class Foo { int x = 1; }\n").repeat(4000).toByteArray()
        ZipOutputStream(f.outputStream()).use { z ->
            z.setLevel(6)
            z.deflated("AndroidManifest.xml", ByteArray(2000) { (it % 13).toByte() })
            z.deflated("classes.dex", text)
            z.stored("resources.arsc", ByteArray(5000) { (it % 7).toByte() })
            for (abi in listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")) {
                z.stored("lib/$abi/libnative.so", rnd.nextBytes(300_000))
            }
            z.stored("res/drawable/icon.png", rnd.nextBytes(20_000))
            z.stored("res/drawable/frame.9.png", rnd.nextBytes(4_000))
            z.stored("res/drawable/raw.png", rnd.nextBytes(3_000))
            z.deflated("META-INF/androidx.core_core.version", "1.13.0".toByteArray())
            z.deflated("META-INF/services/kotlinx.coroutines.internal.MainDispatcherFactory", "x".toByteArray())
            z.deflated("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".toByteArray())
            z.deflated("META-INF/OLD.SF", "sig".toByteArray())
            z.deflated("META-INF/OLD.RSA", "sig".toByteArray())
        }
        return f
    }

    /** Returns data offset for every STORED entry by reading local headers. */
    private fun storedDataOffsets(apk: File): Map<String, Long> {
        val result = HashMap<String, Long>()
        val names = ZipFile(apk).use { z -> z.entries().toList().filter { it.method == ZipEntry.STORED }.map { it.name }.toSet() }
        RandomAccessFile(apk, "r").use { raf ->
            var pos = 0L
            val buf = ByteArray(30)
            while (pos + 30 < raf.length()) {
                raf.seek(pos); raf.readFully(buf)
                fun u16(i: Int) = (buf[i].toInt() and 0xFF) or ((buf[i + 1].toInt() and 0xFF) shl 8)
                fun u32(i: Int) = u16(i).toLong() or (u16(i + 2).toLong() shl 16)
                if (u32(0) != 0x04034b50L) break
                val flags = u16(6); val method = u16(8)
                val csize = u32(18); val nlen = u16(26); val xlen = u16(28)
                val nameBytes = ByteArray(nlen); raf.readFully(nameBytes)
                val name = String(nameBytes)
                val data = pos + 30 + nlen + xlen
                if (method == 0 && name in names) result[name] = data
                var next = data + csize
                if (flags and 8 != 0) {
                    // data descriptor; size not known from header — use central directory
                    val c = ZipFile(apk).use { it.getEntry(name).compressedSize }
                    next = data + c
                    raf.seek(next); val sig = ByteArray(4); raf.readFully(sig)
                    next += if (sig.contentEquals(byteArrayOf(0x50, 0x4b, 0x07, 0x08))) 16 else 12
                }
                pos = next
            }
        }
        return result
    }

    @Test
    fun stripsAbisRecompressesSignsAndAligns() {
        val dir = Files.createTempDirectory("slim").toFile()
        val input = fakeApk(dir)
        val analysis = ApkAnalyzer.analyze(input)
        assertEquals(setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"), analysis.abiSizes.keys)

        val options = OptimizeOptions(keepAbis = setOf("arm64-v8a"), imageMode = ImageMode.LOSSLESS, minSdk = 26)
        val estimate = ApkAnalyzer.estimate(analysis, options)
        assertEquals(900_000L, estimate.abiBytes)

        // Fake codec: pretend icon.png shrinks to half; leave others.
        val processor = ImageProcessor { path, data, _, _, _ -> if (path.endsWith("icon.png")) data.copyOf(data.size / 2) else null }
        val out = File(dir, "out.apk")
        val r = SlimPipeline(processor).run(input, out, File(dir, "work"), options, key())

        println("before=${r.originalSize} after=${r.optimizedSize} saved=${"%.1f".format(r.savedPercent)}% verify=${r.verify}")
        assertTrue(r.verify.verified, "signature must verify: ${r.verify.errors}")
        assertTrue("v2" in r.verify.schemes)

        val names = ZipFile(out).use { z -> z.entries().toList().map { it.name }.toSet() }
        assertTrue("lib/arm64-v8a/libnative.so" in names)
        assertFalse(names.any { it.startsWith("lib/x86") || it.startsWith("lib/armeabi") })
        assertFalse("META-INF/androidx.core_core.version" in names)
        assertTrue("META-INF/services/kotlinx.coroutines.internal.MainDispatcherFactory" in names)
        assertFalse("META-INF/OLD.SF" in names)
        assertTrue("res/drawable/frame.9.png" in names)
        assertEquals(1, r.stats.imagesOptimized)
        assertEquals(10_000, ZipFile(out).use { z -> z.getInputStream(z.getEntry("res/drawable/icon.png")).readBytes().size })

        val offsets = storedDataOffsets(out)
        println("stored offsets: $offsets")
        assertEquals(0L, offsets.getValue("lib/arm64-v8a/libnative.so") % 16384, "so must be 16KB aligned")
        assertEquals(0L, offsets.getValue("resources.arsc") % 4, "arsc must be 4-byte aligned")
        offsets.forEach { (n, o) -> assertEquals(0L, o % 4, "$n not aligned") }
        assertTrue(r.savedPercent > 50f)
    }

    @Test
    fun keepEverythingStillVerifies() {
        val dir = Files.createTempDirectory("slim2").toFile()
        val input = fakeApk(dir)
        val out = File(dir, "out.apk")
        val r = SlimPipeline(null).run(input, out, File(dir, "work"), OptimizeOptions(keepAbis = null, imageMode = ImageMode.OFF, stripBuildMetadata = false, minSdk = 24), key())
        assertTrue(r.verify.verified, r.verify.errors.toString())
        val offsets = storedDataOffsets(out)
        offsets.filterKeys { it.endsWith(".so") }.values.forEach { assertEquals(0L, it % 16384) }
        assertEquals(4, ZipFile(out).use { z -> z.entries().toList().count { it.name.endsWith(".so") } })
    }

    /** v1 (JAR) signing for minSdk < 24. Needs apksig 8.x; the legacy 2.3.0 build relies on JDK internals removed in JDK 17+. */
    @Test
    fun legacyMinSdkGetsV1Signature() {
        val modernApksig = runCatching { Class.forName("com.android.apksig.internal.pkcs7.SignedData") }.isSuccess
        org.junit.Assume.assumeTrue("apksig 8.x required for v1 on modern JDKs", modernApksig)
        val dir = Files.createTempDirectory("slim3").toFile()
        val out = File(dir, "out.apk")
        val r = SlimPipeline(null).run(fakeApk(dir), out, File(dir, "work"), OptimizeOptions(minSdk = 21), key())
        assertTrue(r.verify.verified, r.verify.errors.toString())
        assertTrue("v1" in r.verify.schemes)
    }
}
