package com.campus.arnav.ui.ar

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.campus.arnav.databinding.ActivityArNavigationBinding
import com.campus.arnav.util.CompassManager
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.AndroidEntryPoint
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ARNavigationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArNavigationBinding
    private val viewModel: ARViewModel by viewModels()

    @Inject lateinit var compassManager: CompassManager

    private var arrowNode: ModelNode? = null
    private var currentRotationY: Float = 0f

    companion object {
        const val EXTRA_ROUTE = "extra_route"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val lat = intent.getDoubleExtra("TARGET_LAT", 0.0)
        val lon = intent.getDoubleExtra("TARGET_LON", 0.0)
        val name = intent.getStringExtra("TARGET_NAME") ?: "Destination"

        if (lat != 0.0) {
            viewModel.setTarget(lat, lon, name)
            binding.tvTargetName.text = "Navigating to $name"
        }

        if (allPermissionsGranted()) {
            setupARScene()
            startSensors()
        } else {
            requestPermissions.launch(REQUIRED_PERMISSIONS)
        }

        setupObservers()
        binding.btnExitAr.setOnClickListener { finish() }
    }

    private fun setupARScene() {
        binding.sceneView.apply {
            // Hide the floor scanning dots
            planeRenderer.isVisible = false

            configureSession { session, config ->
                config.lightEstimationMode = com.google.ar.core.Config.LightEstimationMode.DISABLED
            }

        }

        lifecycleScope.launch {
            try {
                val modelInstance = binding.sceneView.modelLoader.loadModelInstance("arrow.glb")
                if (modelInstance != null) {
                    arrowNode = ModelNode(
                        modelInstance = modelInstance,
                        // Make it 30 centimeters wide
                        scaleToUnits = 0.3f
                    ).apply {
                        isEditable = false

                        // Spawn it floating 1.5 meters directly in front of where you started
                        // y = -0.5f places it slightly below eye level
                        position = Position(0.0f, -0.5f, -1.5f)
                        rotation = Rotation(0.0f, 0.0f, 0.0f)
                    }

                    // --- THE CRITICAL FIX ---
                    // Attach it to the SCENE, not the camera!
                    // This makes it a real physical object in your room, bypassing the camera bug.
                    binding.sceneView.addChildNode(arrowNode!!)

                    runOnUiThread {
                        android.widget.Toast.makeText(this@ARNavigationActivity, "World Arrow Spawned!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("AR", "Failed to load arrow", e)
            }
        }
    }

    private fun rotateArrowSmoothly(targetY: Float) {
        val node = arrowNode ?: return

        // Calculate the shortest turning path
        val diff = targetY - currentRotationY
        val shortestDiff = (diff + 180) % 360 - 180

        // Smoothly rotate the arrow without crashing the engine
        currentRotationY += shortestDiff * 0.15f

        // We removed the -30 tilt. Because it is now a physical object in the world,
        // it sits flat in the air and spins like a real compass needle!
        node.rotation = Rotation(0.0f, currentRotationY, 0.0f)
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.arrowRotation.collectLatest { targetRotation ->
                rotateArrowSmoothly(-targetRotation)
            }
        }
        lifecycleScope.launch {
            viewModel.distanceToTarget.collectLatest { distance ->
                binding.tvDistance.text = distance
            }
        }
    }

    private fun startSensors() {
        compassManager.start { azimuth, _ -> viewModel.onSensorHeadingChanged(azimuth) }
        try {
            LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener {
                it?.let { loc -> viewModel.onLocationUpdated(loc) }
            }
        } catch (e: SecurityException) { }
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { if (it.all { p -> p.value }) { setupARScene(); startSensors() } else finish() }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        try {
            arrowNode?.let { node ->
                binding.sceneView.cameraNode.removeChildNode(node)
            }
            arrowNode = null
        } catch (e: Exception) { }

        super.onDestroy()
        compassManager.stop()
    }
}