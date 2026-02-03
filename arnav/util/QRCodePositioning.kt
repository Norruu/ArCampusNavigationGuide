package com.campus.arnav.util

import android.content.Context
import android.util.Log
import com.campus.arnav.data.model.CampusLocation
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Uses QR codes placed around campus for precise indoor positioning
 *
 * QR Code Format:
 * campus://location?lat=37.4275&lon=-122.1697&floor=2&building=library&room=201
 */
class QRCodePositioning(private val context: Context) {

    private val barcodeScanner = BarcodeScanning.getClient()

    /**
     * Scan image for location QR codes
     */
    suspend fun scanForLocationQR(image: InputImage): QRLocationResult? {
        return suspendCancellableCoroutine { continuation ->
            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val locationBarcode = barcodes.firstOrNull { barcode ->
                        barcode.valueType == Barcode.TYPE_URL &&
                                barcode.url?.url?.startsWith("campus://location") == true
                    }

                    val result = locationBarcode?.let { parseLocationQR(it) }
                    continuation.resume(result)
                }
                .addOnFailureListener { e ->
                    Log.e("QRCodePositioning", "Failed to scan QR code", e)
                    continuation.resume(null)
                }
        }
    }

    /**
     * Parse QR code data into location
     */
    private fun parseLocationQR(barcode: Barcode): QRLocationResult? {
        val url = barcode.url?.url ?: return null

        try {
            val uri = android.net.Uri.parse(url)

            val lat = uri.getQueryParameter("lat")?.toDoubleOrNull() ?: return null
            val lon = uri.getQueryParameter("lon")?.toDoubleOrNull() ?: return null
            val floor = uri.getQueryParameter("floor")?.toIntOrNull() ?: 0
            val building = uri.getQueryParameter("building") ?: ""
            val room = uri.getQueryParameter("room")

            return QRLocationResult(
                location = CampusLocation(
                    id = "qr_${System.currentTimeMillis()}",
                    latitude = lat,
                    longitude = lon,
                    altitude = floor * 3.5 // Estimate altitude from floor
                ),
                floor = floor,
                buildingId = building,
                roomId = room,
                confidence = 1.0f // QR codes provide high confidence
            )
        } catch (e: Exception) {
            Log.e("QRCodePositioning", "Failed to parse QR code", e)
            return null
        }
    }

    /**
     * Generate QR code content for a location (for creating QR signs)
     */
    fun generateLocationQRContent(
        lat: Double,
        lon: Double,
        floor: Int,
        buildingId: String,
        roomId: String? = null
    ): String {
        val baseUrl = "campus://location?lat=$lat&lon=$lon&floor=$floor&building=$buildingId"
        return if (roomId != null) {
            "$baseUrl&room=$roomId"
        } else {
            baseUrl
        }
    }

    fun close() {
        barcodeScanner.close()
    }
}

data class QRLocationResult(
    val location: CampusLocation,
    val floor: Int,
    val buildingId: String,
    val roomId: String?,
    val confidence: Float
)