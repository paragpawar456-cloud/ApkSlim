package com.parag.apkslim

import android.app.Application
import java.io.File

class ApkSlimApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Leftovers from a run that was killed mid-way.
        File(cacheDir, "work").deleteRecursively()
    }
}
