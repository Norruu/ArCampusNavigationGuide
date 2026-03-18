package com.campus.arnav.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.campus.arnav.util.FirestoreSyncManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var syncManager: FirestoreSyncManager

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e("GEOFENCE", errorMessage)
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences
        val firstGeofenceId = triggeringGeofences?.firstOrNull()?.requestId

        if (firstGeofenceId == null) return

        // Look up custom message from Firestore geofence data
        val firestoreGeofence = syncManager.cachedGeofences.find {
            it.buildingId == firstGeofenceId || it.id == firstGeofenceId
        }

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            val message = firestoreGeofence?.enterMessage?.ifEmpty { null }
                ?: "Welcome to $firstGeofenceId!"
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            Log.d("GEOFENCE", "ENTER: $firstGeofenceId → $message")

        } else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            val message = firestoreGeofence?.exitMessage?.ifEmpty { null }
                ?: "You are leaving $firstGeofenceId"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            Log.d("GEOFENCE", "EXIT: $firstGeofenceId → $message")
        }
    }
}