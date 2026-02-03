package com.campus.arnav

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class CampusARNavApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Configure OSMDroid
        Configuration.getInstance().apply {
            userAgentValue = packageName
            // Set cache location
            osmdroidBasePath = filesDir
            osmdroidTileCache = cacheDir
        }
    }
}