package com.campus.arnav.util

import android.content.Context
import com.google.ar.core.ArCoreApk

object ARCapabilityManager {
    fun isARCoreSupported(context: Context): Boolean {
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        if (availability.isTransient) {
            return false
        }
        return availability.isSupported
    }
}