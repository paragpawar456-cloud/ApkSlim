package com.parag.apkslim

import android.app.Application
import com.parag.apkslim.data.CrashLog
import java.io.File

class ApkSlimApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        // Leftovers from a run that was killed mid-way.
        File(cacheDir, "work").deleteRecursively()
    }
}
