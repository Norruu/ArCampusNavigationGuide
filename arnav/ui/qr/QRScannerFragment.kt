package com.campus.arnav.ui.qr

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.campus.arnav.MainActivity
import com.campus.arnav.databinding.FragmentQrScannerBinding
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class QRScannerFragment : Fragment() {

    private var _binding: FragmentQrScannerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: QRScannerViewModel by viewModels()

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private lateinit var cameraExecutor: ExecutorService

    private var isScanning = true
    private var lastScanTime = 0L
    private val SCAN_COOLDOWN = 2000L // 2 seconds between scans

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQrScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        observeViewModel()

        // Check camera permission
        if ((requireActivity() as MainActivity).hasCameraPermission()) {
            startCamera()
        } else {
            (requireActivity() as MainActivity).requestCameraPermission()
            // Show message to user
            Snackbar.make(
                binding.root,
                "Camera permission is required to scan QR codes",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scanResult.collectLatest { result ->
                result?.let {
                    handleScanResult(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collectLatest { errorMessage ->
                errorMessage?.let {
                    showError(it)
                    // Allow scanning again after showing error
                    binding.root.postDelayed({
                        isScanning = true
                        viewModel.resetScanResult()
                    }, 2000)
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
                showError("Failed to start camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, QRCodeAnalyzer { barcodes ->
                    if (isScanning && shouldProcessScan()) {
                        processBarcodes(barcodes)
                    }
                })
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            // Unbind all use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                viewLifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            Log.d(TAG, "Camera bound successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
            showError("Failed to bind camera: ${e.message}")
        }
    }

    private fun shouldProcessScan(): Boolean {
        val currentTime = System.currentTimeMillis()
        return if (currentTime - lastScanTime > SCAN_COOLDOWN) {
            lastScanTime = currentTime
            true
        } else {
            false
        }
    }

    private fun processBarcodes(barcodes: List<Barcode>) {
        if (barcodes.isEmpty() || !isScanning) return

        // Take the first barcode detected
        val barcode = barcodes.first()
        barcode.rawValue?.let { qrValue ->
            Log.d(TAG, "QR Code detected: $qrValue")
            isScanning = false
            viewModel.processQRCode(qrValue)
        }
    }

    private fun handleScanResult(result: QRScanResult) {
        when (result) {
            is QRScanResult.Success -> {
                Log.d(TAG, "QR Scan Success: ${result.buildingName}")
                showSuccess("Building found: ${result.buildingName}")

                // Navigate back to map and show the building
                try {
                    // Navigate back to map fragment
                    findNavController().navigateUp()

                    // The building selection should be handled by the MapFragment
                    // through shared ViewModel or navigation arguments
                } catch (e: Exception) {
                    Log.e(TAG, "Navigation error", e)
                    // Allow scanning again if navigation fails
                    binding.root.postDelayed({
                        isScanning = true
                        viewModel.resetScanResult()
                    }, 2000)
                }
            }
            is QRScanResult.InvalidQRCode -> {
                Log.w(TAG, "Invalid QR Code detected")
                showError("Invalid QR Code. Please scan a valid campus building QR code.")
                // Allow scanning again after a delay
                binding.root.postDelayed({
                    isScanning = true
                    viewModel.resetScanResult()
                }, 2000)
            }
            is QRScanResult.BuildingNotFound -> {
                Log.w(TAG, "Building not found in database")
                showError("Building not found. Please contact support.")
                binding.root.postDelayed({
                    isScanning = true
                    viewModel.resetScanResult()
                }, 2000)
            }
        }
    }

    private fun showSuccess(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
            .show()
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            .show()
    }

    override fun onResume() {
        super.onResume()
        isScanning = true
        // Restart camera if it was stopped
        if (cameraProvider == null && (requireActivity() as MainActivity).hasCameraPermission()) {
            startCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        isScanning = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        _binding = null
    }

    /**
     * QR Code Image Analyzer using ML Kit
     */
    private class QRCodeAnalyzer(
        private val onQRCodesDetected: (List<Barcode>) -> Unit
    ) : ImageAnalysis.Analyzer {

        private val scanner = BarcodeScanning.getClient()

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            onQRCodesDetected(barcodes)
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "QR Code scanning failed", exception)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }

    companion object {
        private const val TAG = "QRScannerFragment"
    }
}