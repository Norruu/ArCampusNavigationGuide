package com.campus.arnav.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Route(
    val id: String,
    val origin: CampusLocation,
    val destination: CampusLocation,
    val waypoints: List<Waypoint>,
    val totalDistance: Double,      // in meters
    val estimatedTime: Long,        // in seconds
    val steps: List<NavigationStep>
) : Parcelable