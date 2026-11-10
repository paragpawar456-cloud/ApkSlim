package com.parag.apkslim.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class InstalledApp(
    val label: String,
    val packageName: String,
    val versionName: String,
    val sourceDir: String,
    val splitDirs: List<String>,
    val splitCount: Int,
    val totalSize: Long,
    val isSystem: Boolean,
    /** ApplicationInfo.CATEGORY_* (game, social, …), -1 if undefined. */
    val category: Int = -1,
    val lastUpdate: Long = 0,
)

data class StorageInfo(val total: Long, val free: Long) {
    val used get() = total - free
}

/** Everything we know about the APK the user picked. */
data class ApkInfo(
    val file: File,
    val label: String,
    val packageName: String,
    val versionName: String,
    val minSdk: Int,
    val targetSdk: Int,
    /** Installed app made of several APKs (Play "App Bundle" delivery). */
    val installedSplitCount: Int,
    val isInstalled: Boolean,
    val fromInstalledApp: Boolean,
    /** All APK parts: just the file itself, or base.apk + split_config.*.apk for split installs. */
    val parts: List<com.parag.apkslim.core.NamedApk>,
)

class AppRepository(private val context: Context) {
    private val pm = context.packageManager
    private val iconCache = LruCache<String, Bitmap>(200)

    fun installedApps(): List<InstalledApp> {
        // One getInstalledPackages call gives labels and versions together (was one extra IPC per app).
        @Suppress("DEPRECATION")
        val pkgs = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else pm.getInstalledPackages(0)
        return pkgs.asSequence()
            .mapNotNull { pi -> pi.applicationInfo?.let { pi to it } }
            .filter { (_, ai) -> ai.packageName != context.packageName && ai.sourceDir != null && File(ai.sourceDir).canRead() }
            .map { (pi, ai) ->
                val splits = ai.splitSourceDirs?.size ?: 0
                val size = File(ai.sourceDir).length() + (ai.splitSourceDirs?.sumOf { File(it).length() } ?: 0L)
                InstalledApp(
                    label = ai.loadLabel(pm).toString(),
                    packageName = ai.packageName,
                    versionName = pi.versionName ?: "",
                    sourceDir = ai.sourceDir,
                    splitDirs = ai.splitSourceDirs?.toList().orEmpty(),
                    splitCount = splits,
                    totalSize = size,
                    isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    category = ai.category,
                    lastUpdate = pi.lastUpdateTime,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun storage(): StorageInfo = runCatching {
        val st = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        StorageInfo(st.totalBytes, st.availableBytes)
    }.getOrDefault(StorageInfo(0, 0))

    /** Cached icon only, no loading: lets lists draw the real icon on the very first frame. */
    fun peekIcon(packageName: String): Bitmap? = iconCache.get(packageName)

    fun icon(packageName: String): Bitmap? {
        iconCache.get(packageName)?.let { return it }
        return runCatching { pm.getApplicationIcon(packageName).toSafeBitmap() }.getOrNull()?.also { iconCache.put(packageName, it) }
    }

    fun iconForArchive(file: File): Bitmap? = runCatching {
        archiveInfo(file)?.applicationInfo?.let { ai ->
            ai.sourceDir = file.absolutePath
            ai.publicSourceDir = file.absolutePath
            ai.loadIcon(pm).toSafeBitmap()
        }
    }.getOrNull()

    fun describe(file: File, fromInstalledApp: Boolean, parts: List<com.parag.apkslim.core.NamedApk> = listOf(com.parag.apkslim.core.NamedApk("base.apk", file))): ApkInfo {
        val info = archiveInfo(file) ?: error("This file is not a valid APK (Android could not parse its manifest).")
        val ai = info.applicationInfo!!
        ai.sourceDir = file.absolutePath
        ai.publicSourceDir = file.absolutePath
        val installed = runCatching { packageInfo(info.packageName) }.getOrNull()
        return ApkInfo(
            file = file,
            label = ai.loadLabel(pm).toString(),
            packageName = info.packageName,
            versionName = info.versionName ?: "",
            minSdk = ai.minSdkVersion,
            targetSdk = ai.targetSdkVersion,
            installedSplitCount = installed?.applicationInfo?.splitSourceDirs?.size ?: 0,
            isInstalled = installed != null,
            fromInstalledApp = fromInstalledApp,
            parts = parts,
        )
    }

    /** Installed app → its parts (base + splits) as named files. */
    fun partsOf(app: InstalledApp): List<com.parag.apkslim.core.NamedApk> =
        listOf(com.parag.apkslim.core.NamedApk("base.apk", File(app.sourceDir))) +
            app.splitDirs.map { com.parag.apkslim.core.NamedApk(File(it).name, File(it)) }

    /** Copies a picked document into the cache so we can random-access it as a zip. */
    fun importUri(uri: Uri): File {
        // Input checks: a safe file name, a size limit, enough free space, and the real file type
        // read from its first bytes rather than trusted from the name.
        val raw = displayName(uri) ?: "picked.apk"
        val name = raw.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.').take(120).ifEmpty { "picked.apk" }
        val declared = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
        if (declared > InputLimits.MAX_INPUT_BYTES) throw UserFacingException("This file is too large (over ${InputLimits.MAX_INPUT_BYTES shr 30} GB).")
        val dir = File(context.cacheDir, "inputs").apply { deleteRecursively(); mkdirs() }
        if (declared > 0 && dir.usableSpace < declared + InputLimits.SPACE_MARGIN) {
            throw UserFacingException("Not enough free storage to open this file. Free up some space and try again.")
        }
        val tmp = File(dir, "incoming.tmp")
        context.contentResolver.openInputStream(uri)?.use { ins ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(1 shl 16)
                var total = 0L
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > InputLimits.MAX_INPUT_BYTES) throw UserFacingException("This file is too large.")
                    out.write(buf, 0, n)
                }
            }
        } ?: throw UserFacingException("Could not open the selected file.")
        val ext = when (InputLimits.sniff(tmp)) {
            InputLimits.Kind.SLIM -> ".slim"
            InputLimits.Kind.ZIP -> if (name.endsWith(".apks", true)) ".apks" else ".apk"
            InputLimits.Kind.UNKNOWN -> { tmp.delete(); throw UserFacingException("This is not an APK, .apks or .slim file.") }
        }
        val base = name.substringBeforeLast('.').ifEmpty { "picked" }
        val out = File(dir, base + ext)
        if (!tmp.renameTo(out)) { tmp.copyTo(out, true); tmp.delete() }
        return out
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun archiveInfo(file: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(0))
        else pm.getPackageArchiveInfo(file.absolutePath, 0)

    @Suppress("DEPRECATION")
    private fun packageInfo(pkg: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        else pm.getPackageInfo(pkg, 0)

    private fun Drawable.toSafeBitmap(): Bitmap {
        val w = intrinsicWidth.takeIf { it in 1..512 } ?: 144
        val h = intrinsicHeight.takeIf { it in 1..512 } ?: 144
        return toBitmap(w, h)
    }
}

data class HistoryItem(
    val id: Long,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val originalSize: Long,
    val optimizedSize: Long,
    val timestamp: Long,
    val outputPath: String,
    val summary: String,
) {
    val savedBytes get() = originalSize - optimizedSize
    val savedPercent get() = if (originalSize == 0L) 0f else savedBytes * 100f / originalSize
    val outputExists get() = File(outputPath).exists()
}

/** Small JSON-file store; history is tiny so a database would be overkill. */
class HistoryStore(context: Context) {
    private val file = File(context.filesDir, "history.json")

    @Synchronized
    fun load(): List<HistoryItem> = runCatching {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            HistoryItem(
                id = o.getLong("id"),
                appName = o.getString("appName"),
                packageName = o.getString("packageName"),
                versionName = o.optString("versionName"),
                originalSize = o.getLong("originalSize"),
                optimizedSize = o.getLong("optimizedSize"),
                timestamp = o.getLong("timestamp"),
                outputPath = o.getString("outputPath"),
                summary = o.optString("summary"),
            )
        }.sortedByDescending { it.timestamp }
    }.getOrDefault(emptyList())

    @Synchronized
    fun add(item: HistoryItem) = save(listOf(item) + load())

    @Synchronized
    fun delete(id: Long) {
        val items = load()
        items.firstOrNull { it.id == id }?.let { File(it.outputPath).delete() }
        save(items.filterNot { it.id == id })
    }

    @Synchronized
    fun clear() {
        load().forEach { File(it.outputPath).delete() }
        save(emptyList())
    }

    private fun save(items: List<HistoryItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("appName", it.appName); put("packageName", it.packageName)
                put("versionName", it.versionName); put("originalSize", it.originalSize)
                put("optimizedSize", it.optimizedSize); put("timestamp", it.timestamp)
                put("outputPath", it.outputPath); put("summary", it.summary)
            })
        }
        val tmp = File(file.parentFile, "history.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }
}
