package com.parag.apkslim.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ApkActions {
    private const val APK_MIME = "application/vnd.android.package-archive"

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun mimeFor(file: File): String = if (file.name.endsWith(".apk")) APK_MIME else "application/octet-stream"

    fun share(context: Context, file: File) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeFor(file)
            putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share ${file.name}"))
    }

    /** Shares plain text (the crash report) through the user's choice of app. Nothing is sent automatically. */
    fun shareText(context: Context, subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, subject))
    }

    /** True if ApkSlim may install apps; otherwise opens the "Install unknown apps" setting. */
    fun ensureInstallPermission(context: Context): Boolean {
        if (context.packageManager.canRequestPackageInstalls()) return true
        Toast.makeText(context, "Allow ApkSlim to install apps, then tap Install again.", Toast.LENGTH_LONG).show()
        try {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
        } catch (_: ActivityNotFoundException) { }
        return false
    }

    fun uninstall(context: Context, packageName: String) {
        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
    }

    fun copyTo(context: Context, file: File, target: Uri): Boolean = runCatching {
        context.contentResolver.openOutputStream(target)?.use { out -> file.inputStream().use { it.copyTo(out, 1 shl 16) } } != null
    }.getOrDefault(false)
}
