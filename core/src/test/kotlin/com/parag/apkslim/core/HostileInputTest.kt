package com.parag.apkslim.core

import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Broken or hostile files must fail with a clear error and never write outside the target folder. */
class HostileInputTest {
    private val dir: File = Files.createTempDirectory("hostile").toFile()

    private fun archive(apkCount: Int = 1, apkName: String = "base.apk", entries: List<Pair<String, ByteArray>>, sizeOverride: Long? = null): File {
        val f = File(dir, "a-${System.nanoTime()}.slim")
        f.outputStream().use { fo ->
            fo.write("APKSLIM\u0001".toByteArray(Charsets.ISO_8859_1))
            DataOutputStream(XZOutputStream(fo, LZMA2Options(1))).use { o ->
                o.writeInt(1); o.writeUTF("L"); o.writeUTF("p.k"); o.writeUTF("1"); o.writeInt(26)
                o.writeInt(apkCount)
                o.writeUTF(apkName); o.writeInt(entries.size)
                for ((name, data) in entries) {
                    val crc = CRC32().apply { update(data) }.value
                    o.writeUTF(name); o.writeByte(ZipEntry.STORED); o.writeLong(0); o.writeLong(sizeOverride ?: data.size.toLong()); o.writeLong(crc)
                    o.write(data)
                }
            }
        }
        return f
    }

    @Test
    fun entryNameWithDotDotIsRejected() {
        val a = archive(entries = listOf("../../evil.txt" to byteArrayOf(1, 2, 3)))
        val e = assertFailsWith<IllegalArgumentException> { SlimArchive.unpack(a, File(dir, "out1")) }
        assertTrue("unsafe" in e.message!!)
    }

    @Test
    fun absurdCountsAreRejected() {
        assertFailsWith<IllegalArgumentException> { SlimArchive.unpack(archive(apkCount = 100_000, entries = emptyList()), File(dir, "out2")) }
        assertFailsWith<IllegalArgumentException> { SlimArchive.unpack(archive(entries = listOf("a" to byteArrayOf(1)), sizeOverride = -5), File(dir, "out3")) }
    }

    @Test
    fun apkNameCannotEscapeOutputFolder() {
        val out = File(dir, "out4")
        val (_, apks) = SlimArchive.unpack(archive(apkName = "../../escape", entries = listOf("AndroidManifest.xml" to byteArrayOf(1, 2))), out)
        assertEquals(out.canonicalFile, apks.single().file.canonicalFile.parentFile)
        assertFalse(File(dir, "escape.apk").exists())
    }

    @Test
    fun bundleTraversalStaysInFolder() {
        val bundle = File(dir, "b.apks")
        ZipOutputStream(bundle.outputStream()).use { z ->
            for (n in listOf("../../evil.apk", "a/base.apk", "b/base.apk")) { z.putNextEntry(ZipEntry(n)); z.write(byteArrayOf(9)); z.closeEntry() }
        }
        val out = File(dir, "parts")
        val files = SlimPipeline.unbundle(bundle, out)
        assertEquals(3, files.size)
        assertEquals(3, files.map { it.name }.toSet().size, "duplicate names must not overwrite each other")
        files.forEach { assertEquals(out.canonicalFile, it.canonicalFile.parentFile) }
        assertFalse(File(dir, "evil.apk").exists())
    }

    @Test
    fun emptyBundleFailsClearly() {
        val bundle = File(dir, "empty.apks")
        ZipOutputStream(bundle.outputStream()).use { z -> z.putNextEntry(ZipEntry("readme.txt")); z.closeEntry() }
        assertFailsWith<IllegalArgumentException> { SlimPipeline.unbundle(bundle, File(dir, "p2")) }
    }
}
