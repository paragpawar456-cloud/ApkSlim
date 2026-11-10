package com.parag.apkslim.data

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Private, on-device crash log (ApkSlim has no internet permission, so nothing is ever uploaded).
 * If the app closes unexpectedly, the next launch offers to share the report.
 */
object CrashLog {
    private lateinit var file: File
    private val recent = ArrayDeque<String>()

    fun install(context: Context) {
        file = File(context.filesDir, "crash/last.txt")
        val version = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(buildString {
                    appendLine("ApkSlim $version crashed on ${stamp()}")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    append(trace(error))
                    synchronized(recent) {
                        if (recent.isNotEmpty()) { appendLine(); appendLine("Earlier handled errors:"); recent.forEach { appendLine(it) } }
                    }
                })
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Remembers a handled error (last 5) so a later crash report has context. */
    fun note(t: Throwable) = synchronized(recent) {
        recent.addLast("${stamp()} ${t.javaClass.simpleName}: ${t.message?.take(200)}")
        while (recent.size > 5) recent.removeFirst()
    }

    fun pending(): String? = if (::file.isInitialized && file.exists()) runCatching { file.readText() }.getOrNull() else null

    fun clear() { if (::file.isInitialized) file.delete() }

    private fun stamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    private fun trace(t: Throwable) = StringWriter().also { t.printStackTrace(PrintWriter(it)) }.toString().take(20_000)
}
