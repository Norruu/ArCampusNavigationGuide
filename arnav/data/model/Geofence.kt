package com.campus.arnav.data.model

data class Geofence(
    val id: String = "",
    val buildingId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radius: Double = 50.0,
    val enterMessage: String = "",
    val exitMessage: String = "",
    val isActive: Boolean = true,
    val triggerOnEnter: Boolean = true,
    val triggerOnExit: Boolean = true,
    // This is the new field that allows the app to read the custom shapes!
    val polygonPoints: List<Map<String, Double>>? = null
)