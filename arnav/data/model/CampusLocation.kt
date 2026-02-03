package com.campus.arnav.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.osmdroid.util.GeoPoint

@Parcelize
data class CampusLocation(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f
) : Parcelable {

    fun toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude, altitude)

    companion object {
        fun fromGeoPoint(geoPoint: GeoPoint, id: String = ""): CampusLocation {
            return CampusLocation(
                id = id,
                latitude = geoPoint.latitude,
                longitude = geoPoint.longitude,
                altitude = geoPoint.altitude
            )
        }
    }
}