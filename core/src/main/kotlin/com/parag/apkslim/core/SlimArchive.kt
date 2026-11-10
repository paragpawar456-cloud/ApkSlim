package com.parag.apkslim.core

import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZInputStream
import org.tukaani.xz.XZOutputStream
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Information stored in a .slim archive so it can be restored without the original. */
data class ArchiveMeta(
    val label: String,
    val packageName: String,
    val versionName: String,
    val minSdk: Int,
    val apkNames: List<String> = emptyList(),
)

data class NamedApk(val name: String, val file: File)

/**
 * The ".slim" storage format.
 *
 * APKs compress with zip "deflate" (from 1993). A .slim file holds every APK entry
 * *uncompressed* inside one solid XZ/LZMA2 stream, which finds far more repetition,
 * especially in app code (DEX): typically 30–35 % smaller than deflate.
 *
 * A .slim file is NOT installable. ApkSlim restores it into normal APK(s) (re-aligned and
 * re-signed) before installing.
 *
 * Layout: "APKSLIM\u0001" + XZ( DataOutputStream records ).
 */
object SlimArchive {
    private val MAGIC = "APKSLIM\u0001".toByteArray(Charsets.ISO_8859_1)
    private const val VERSION = 1
    const val EXTENSION = "slim"

    fun isArchive(file: File): Boolean = runCatching {
        file.inputStream().use { ins -> ByteArray(MAGIC.size).also { ins.read(it) }.contentEquals(MAGIC) }
    }.getOrDefault(false)

    fun pack(
        apks: List<NamedApk>,
        meta: ArchiveMeta,
        output: File,
        progress: ProgressListener = ProgressListener { _, _, _ -> },
        isCancelled: () -> Boolean = { false },
    ) {
        val totalRaw = apks.sumOf { a -> ZipFile(a.file).use { z -> z.entries().asSequence().sumOf { it.size.coerceAtLeast(0) } } }.coerceAtLeast(1)
        var done = 0L
        output.outputStream().buffered(1 shl 16).use { fileOut ->
            fileOut.write(MAGIC)
            // Preset 6 = 8 MB dictionary, ~100 MB encoder memory: safe on phones.
            XZOutputStream(fileOut, LZMA2Options(6)).use { xz ->
                val out = DataOutputStream(xz)
                out.writeInt(VERSION)
                out.writeUTF(meta.label); out.writeUTF(meta.packageName); out.writeUTF(meta.versionName); out.writeInt(meta.minSdk)
                out.writeInt(apks.size)
                val buffer = ByteArray(1 shl 16)
                for (apk in apks) {
                    ZipFile(apk.file).use { zip ->
                        val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
                        out.writeUTF(apk.name)
                        out.writeInt(entries.size)
                        for (e in entries) {
                            if (isCancelled()) throw CancelledException()
                            out.writeUTF(e.name)
                            out.writeByte(e.method)
                            out.writeLong(e.time)
                            out.writeLong(e.size)
                            out.writeLong(e.crc)
                            var written = 0L
                            zip.getInputStream(e).use { ins ->
                                while (true) {
                                    val n = ins.read(buffer)
                                    if (n < 0) break
                                    out.write(buffer, 0, n)
                                    written += n
                                }
                            }
                            check(written == e.size) { "Size mismatch in ${e.name}" }
                            done += written
                            progress.onProgress(Stage.ARCHIVING, done.toFloat() / totalRaw, e.name.substringAfterLast('/'))
                        }
                    }
                }
                out.flush()
            }
        }
    }

    /** Reads the whole archive once (XZ checks its own CRCs) and returns its metadata. */
    fun check(archive: File, progress: ProgressListener = ProgressListener { _, _, _ -> }): ArchiveMeta =
        read(archive, null, progress) { _, _ -> }

    /** Unpacks into UNSIGNED, aligned APKs in [outDir]. Sign them with [ApkSigning] afterwards. */
    fun unpack(archive: File, outDir: File, progress: ProgressListener = ProgressListener { _, _, _ -> }, isCancelled: () -> Boolean = { false }): Pair<ArchiveMeta, List<NamedApk>> {
        outDir.mkdirs()
        val result = ArrayList<NamedApk>()
        val meta = read(archive, outDir, progress, isCancelled) { name, file -> result += NamedApk(name, file) }
        return meta to result
    }

    private fun read(
        archive: File,
        outDir: File?,
        progress: ProgressListener,
        isCancelled: () -> Boolean = { false },
        onApk: (String, File) -> Unit,
    ): ArchiveMeta {
        val length = archive.length().coerceAtLeast(1)
        val counting = CountingInputStream(BufferedInputStream(archive.inputStream(), 1 shl 16))
        counting.use { raw ->
            val magic = ByteArray(MAGIC.size).also { DataInputStream(raw).readFully(it) }
            require(magic.contentEquals(MAGIC)) { "Not an ApkSlim archive" }
            val ins = DataInputStream(XZInputStream(raw).buffered(1 shl 16))
            require(ins.readInt() == VERSION) { "Unsupported archive version" }
            val label = ins.readUTF(); val pkg = ins.readUTF(); val ver = ins.readUTF(); val minSdk = ins.readInt()
            val apkCount = ins.readInt()
            require(apkCount in 1..Limits.MAX_PARTS) { "Archive is damaged (bad part count)" }
            val names = ArrayList<String>()
            val usedTargets = HashSet<String>()
            var written = 0L
            val buffer = ByteArray(1 shl 16)
            repeat(apkCount) {
                val apkName = ins.readUTF()
                names += apkName
                val entryCount = ins.readInt()
                require(entryCount in 0..Limits.MAX_ENTRIES) { "Archive is damaged (bad entry count)" }
                val target = outDir?.let { dir ->
                    var n = safeName(apkName)
                    var i = 1
                    while (!usedTargets.add(n)) n = "${i++}_" + safeName(apkName)
                    File(dir, n)
                }
                val writer = target?.let { AlignedZipWriter(it) }
                try {
                    repeat(entryCount) {
                        if (isCancelled()) throw CancelledException()
                        val name = ins.readUTF()
                        val method = ins.readByte().toInt()
                        val time = ins.readLong()
                        val size = ins.readLong()
                        val crc = ins.readLong()
                        require(Limits.isSafeEntryName(name)) { "Archive is damaged (unsafe entry name)" }
                        require(method == ZipEntry.STORED || method == ZipEntry.DEFLATED) { "Archive is damaged (bad entry type)" }
                        require(size in 0..Limits.MAX_ENTRY_BYTES) { "Archive is damaged (bad entry size)" }
                        if (outDir != null) {
                            written += size
                            require(written <= Limits.MAX_TOTAL_BYTES) { "Archive is too large to restore" }
                            check(outDir.usableSpace > size + Limits.SPACE_MARGIN) { "No space left on the phone to restore this archive" }
                        }
                        val body = BoundedInputStream(ins, size)
                        if (writer != null) writer.write(name, method, time, size, crc, body)
                        else while (body.read(buffer) >= 0) { /* drain to check CRCs */ }
                        progress.onProgress(Stage.VERIFYING, counting.count.toFloat() / length, name.substringAfterLast('/'))
                    }
                } finally {
                    writer?.close()
                }
                if (target != null) onApk(apkName, target)
            }
            return ArchiveMeta(label, pkg, ver, minSdk, names)
        }
    }

    private fun safeName(n: String) = n.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.').take(100).ifEmpty { "part" }.let { if (it.endsWith(".apk")) it else "$it.apk" }

    private class BoundedInputStream(private val src: InputStream, private var remaining: Long) : InputStream() {
        override fun read(): Int = if (remaining <= 0) -1 else src.read().also { if (it >= 0) remaining-- }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val n = src.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n > 0) remaining -= n
            if (n < 0) throw java.io.EOFException("Archive is truncated")
            return n
        }
    }

    private class CountingInputStream(src: InputStream) : java.io.FilterInputStream(src) {
        var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
    }
}

/** Zip writer that keeps STORED entries aligned exactly like [ApkOptimizer] does. */
class AlignedZipWriter(file: File, level: Int = 9) : Closeable {
    private val counting = Counting(file.outputStream().buffered(1 shl 16))
    private val zos = ZipOutputStream(counting).apply { setLevel(level) }
    private val buffer = ByteArray(1 shl 16)

    fun write(name: String, method: Int, time: Long, size: Long, crc: Long, data: InputStream) {
        val e = ZipEntry(name)
        if (time != -1L) e.time = time
        if (method == ZipEntry.STORED) {
            e.method = ZipEntry.STORED
            e.size = size; e.compressedSize = size; e.crc = crc
            e.extra = ApkOptimizer.alignmentExtra(counting.count, name, Rules.alignmentFor(name))
        } else {
            e.method = ZipEntry.DEFLATED
        }
        zos.putNextEntry(e)
        while (true) {
            val n = data.read(buffer)
            if (n < 0) break
            zos.write(buffer, 0, n)
        }
        zos.closeEntry()
    }

    override fun close() = zos.close()

    private class Counting(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
        override fun write(b: Int) { out.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
    }
}
