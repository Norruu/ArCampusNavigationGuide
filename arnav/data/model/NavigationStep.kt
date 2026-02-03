package com.campus.arnav.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class NavigationStep(
    val instruction: String,
    val distance: Double,
    val direction: Direction,
    val startLocation: CampusLocation,
    val endLocation: CampusLocation,
    val isIndoor: Boolean = false
) : Parcelable