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
        @Suppress("DEPRECATION")
        val apps = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else pm.getInstalledApplications(0)
        return apps.asSequence()
            .filter { it.packageName != context.packageName && it.sourceDir != null && File(it.sourceDir).canRead() }
            .map { ai ->
                val splits = ai.splitSourceDirs?.size ?: 0
                val size = File(ai.sourceDir).length() + (ai.splitSourceDirs?.sumOf { File(it).length() } ?: 0L)
                InstalledApp(
                    label = ai.loadLabel(pm).toString(),
                    packageName = ai.packageName,
                    versionName = runCatching { packageInfo(ai.packageName).versionName }.getOrNull() ?: "",
                    sourceDir = ai.sourceDir,
                    splitDirs = ai.splitSourceDirs?.toList().orEmpty(),
                    splitCount = splits,
                    totalSize = size,
                    isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    category = ai.category,
                    lastUpdate = File(ai.sourceDir).lastModified(),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun storage(): StorageInfo = runCatching {
        val st = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        StorageInfo(st.totalBytes, st.availableBytes)
    }.getOrDefault(StorageInfo(0, 0))

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
        val name = displayName(uri)?.replace(Regex("[^A-Za-z0-9._-]"), "_") ?: "picked.apk"
        val dir = File(context.cacheDir, "inputs").apply { deleteRecursively(); mkdirs() }
        val known = listOf(".apk", ".apks", ".slim").any { name.endsWith(it, true) }
        val out = File(dir, if (known) name else "$name.apk")
        context.contentResolver.openInputStream(uri)?.use { ins -> out.outputStream().use { ins.copyTo(it, 1 shl 16) } }
            ?: error("Could not open the selected file")
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
