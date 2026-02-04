package com.campus.arnav.ui.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.arnav.data.repository.CampusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QRScannerViewModel @Inject constructor(
    private val buildingRepository: CampusRepository
) : ViewModel() {

    private val _scanResult = MutableStateFlow<QRScanResult?>(null)
    val scanResult: StateFlow<QRScanResult?> = _scanResult.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    /**
     * Process scanned QR code
     */
    fun processQRCode(qrValue: String) {
        if (_isProcessing.value) return

        _isProcessing.value = true

        viewModelScope.launch {
            try {
                // Parse QR code value
                // Expected format: "CAMPUS_BUILDING:{buildingId}"
                // or just the building ID

                val buildingId = extractBuildingId(qrValue)

                if (buildingId == null) {
                    _scanResult.value = QRScanResult.InvalidQRCode
                    _error.value = "Invalid QR code format"
                    return@launch
                }

                // Fetch building from repository
                val building = buildingRepository.getBuildingById(buildingId)

                if (building != null) {
                    _scanResult.value = QRScanResult.Success(
                        buildingId = building.id,
                        buildingName = building.name,
                        building = building
                    )
                } else {
                    _scanResult.value = QRScanResult.BuildingNotFound
                    _error.value = "Building not found"
                }

            } catch (e: Exception) {
                _scanResult.value = QRScanResult.InvalidQRCode
                _error.value = "Error processing QR code: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * Extract building ID from QR code value
     * Supports multiple formats:
     * - "CAMPUS_BUILDING:12345"
     * - "BUILDING:12345"
     * - "12345" (just the ID)
     */
    private fun extractBuildingId(qrValue: String): String? {
        return when {
            qrValue.startsWith("CAMPUS_BUILDING:", ignoreCase = true) -> {
                qrValue.substringAfter("CAMPUS_BUILDING:", "").trim()
            }
            qrValue.startsWith("BUILDING:", ignoreCase = true) -> {
                qrValue.substringAfter("BUILDING:", "").trim()
            }
            qrValue.matches(Regex("^[a-zA-Z0-9_-]+$")) -> {
                // Assume it's just the building ID
                qrValue.trim()
            }
            else -> null
        }
    }

    /**
     * Reset scan result
     */
    fun resetScanResult() {
        _scanResult.value = null
        _error.value = null
    }
}

/**
 * QR Scan Result sealed class
 */
sealed class QRScanResult {
    data class Success(
        val buildingId: String,
        val buildingName: String,
        val building: com.campus.arnav.data.model.Building
    ) : QRScanResult()

    object InvalidQRCode : QRScanResult()
    object BuildingNotFound : QRScanResult()
}