package com.campus.arnav.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Waypoint(
    val location: CampusLocation,
    val type: WaypointType,
    val instruction: String? = null
) : Parcelable

enum class WaypointType {
    START,
    END,
    TURN_LEFT,
    TURN_RIGHT,
    CONTINUE_STRAIGHT,
    ENTER_BUILDING,
    EXIT_BUILDING,
    STAIRS_UP,
    STAIRS_DOWN,
    ELEVATOR,
    LANDMARK
}