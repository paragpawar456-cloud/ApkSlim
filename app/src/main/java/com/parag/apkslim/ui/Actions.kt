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

    fun share(context: Context, file: File) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = APK_MIME
            putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share optimized APK"))
    }

    /** Opens the system installer; asks for "Install unknown apps" permission first if needed. */
    fun install(context: Context, file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(context, "Allow ApkSlim to install apps, then tap Install again.", Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            )
            return
        }
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, file), APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(view)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No installer found on this device.", Toast.LENGTH_LONG).show()
        }
    }

    fun uninstall(context: Context, packageName: String) {
        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
    }

    fun copyTo(context: Context, file: File, target: Uri): Boolean = runCatching {
        context.contentResolver.openOutputStream(target)?.use { out -> file.inputStream().use { it.copyTo(out, 1 shl 16) } } != null
    }.getOrDefault(false)
}
