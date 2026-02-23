package com.campus.arnav.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e("GEOFENCE", errorMessage)
            return
        }

        // Get the transition type (Did they ENTER or EXIT the zone?)
        val geofenceTransition = geofencingEvent.geofenceTransition

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            // Find out WHICH geofence they entered (in case you have multiple)
            val triggeringGeofences = geofencingEvent.triggeringGeofences
            val firstGeofenceId = triggeringGeofences?.get(0)?.requestId

            // Do something! (Show a toast, trigger a notification, etc.)
            Toast.makeText(context, "Welcome to $firstGeofenceId!", Toast.LENGTH_LONG).show()
            Log.d("GEOFENCE", "User entered: $firstGeofenceId")

        } else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            Toast.makeText(context, "You are leaving the area.", Toast.LENGTH_SHORT).show()
        }
    }
}