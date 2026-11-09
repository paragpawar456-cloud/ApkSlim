package com.parag.apkslim.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import java.io.File

/**
 * Installs one or more APKs (a split app needs all its parts in one session) through
 * PackageInstaller. The system shows its own confirmation dialog.
 */
object Installer {

    fun install(context: Context, apks: List<File>) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        params.setSize(apks.sumOf { it.length() })
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apks.forEachIndexed { i, f ->
                f.inputStream().use { ins ->
                    session.openWrite("${i}_${f.name}", 0, f.length()).use { out ->
                        ins.copyTo(out, 1 shl 16)
                        session.fsync(out)
                    }
                }
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, id, intent, flags)
            session.commit(pi.intentSender)
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Toast.makeText(context, "Installed successfully", Toast.LENGTH_LONG).show()
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
                val hint = if (status == PackageInstaller.STATUS_FAILURE_CONFLICT || msg.contains("signature", true) || msg.contains("UPDATE_INCOMPATIBLE"))
                    " The installed copy has a different signature: uninstall it first." else ""
                Toast.makeText(context, "Install failed: $msg.$hint", Toast.LENGTH_LONG).show()
            }
        }
    }
}
