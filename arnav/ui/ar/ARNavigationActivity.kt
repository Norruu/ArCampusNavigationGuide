package com.campus.arnav.ui.ar

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.campus.arnav.databinding.ActivityArNavigationBinding
import com.campus.arnav.util.ARCapabilityManager
import com.campus.arnav.util.CompassManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@AndroidEntryPoint
class ARNavigationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArNavigationBinding
    private val viewModel: ARViewModel by viewModels()

    // Helper to access sensors (Compass)
    @Inject lateinit var compassManager: CompassManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Check Mode
        val isARCore = ARCapabilityManager.isARCoreSupported(this)
        setupCameraMode(isARCore)

        // 2. Start Sensors (Compass is needed for BOTH modes for direction)
        compassManager.start { azimuth, _ ->
            viewModel.onSensorHeadingChanged(azimuth)
        }

        // 3. Observe ViewModel to update UI Overlay
        lifecycleScope.launchWhenStarted {
            viewModel.arrowRotation.collectLatest { rotation ->
                // Smoothly rotate the arrow
                binding.ivDirectionArrow.animate().rotation(rotation).setDuration(200).start()
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.distanceToTarget.collectLatest { dist ->
                binding.tvDistance.text = dist
            }
        }

        binding.btnExitAr.setOnClickListener { finish() }
    }

    private fun setupCameraMode(isARCore: Boolean) {
        if (isARCore) {
            // Initialize ARCore Session/Fragment here
            // For now, let's stick to the Fallback mode code as it works on ALL devices
            // and is easier to get running immediately.
            setupSensorFallbackMode()
            binding.tvArModeDebug.text = "AR Mode: ARCore (Supported)"
        } else {
            setupSensorFallbackMode()
            binding.tvArModeDebug.text = "AR Mode: Sensor Fallback"
        }
    }

    private fun setupSensorFallbackMode() {
        // Init CameraX Preview into binding.cameraPreviewView
        // (Assuming you have a standard CameraX setup code)
    }

    override fun onDestroy() {
        super.onDestroy()
        compassManager.stop()
    }
}