package com.campus.arnav.ui.ar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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
import io.github.sceneview.node.Node
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import javax.inject.Inject

private const val TAG = "ARNAV_DEBUG"

@AndroidEntryPoint
class ARNavigationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArNavigationBinding
    private val viewModel: ARViewModel by viewModels()

    @Inject lateinit var compassManager: CompassManager

    private var hudNode: Node? = null
    private var arrowNode: ModelNode? = null

    private var currentRotationY: Float = 0f
    private var targetRotationY: Float = 0f

    companion object {
        const val EXTRA_ROUTE = "extra_route"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        private const val HUD_DISTANCE    = 1.2f
        private const val HUD_VERT_OFFSET = -0.25f
        private const val ROTATION_LERP   = 0.10f
        private const val ARROW_SIZE      = 0.18f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val lat  = intent.getDoubleExtra("TARGET_LAT", 0.0)
        val lon  = intent.getDoubleExtra("TARGET_LON", 0.0)
        val name = intent.getStringExtra("TARGET_NAME") ?: "Destination"

        if (lat != 0.0) {
            viewModel.setTarget(lat, lon, name)
            binding.tvTargetName.text = "Navigating to $name"
        }

        if (allPermissionsGranted()) { setupARScene(); startSensors() }
        else requestPermissions.launch(REQUIRED_PERMISSIONS)

        setupObservers()
        binding.btnExitAr.setOnClickListener { finish() }
    }

    private fun setupARScene() {
        binding.sceneView.apply {
            planeRenderer.isVisible = false
            configureSession { _, config ->
                config.lightEstimationMode =
                    com.google.ar.core.Config.LightEstimationMode.DISABLED
                config.depthMode =
                    com.google.ar.core.Config.DepthMode.DISABLED
            }
        }

        lifecycleScope.launch {
            try {
                val modelInstance =
                    binding.sceneView.modelLoader.loadModelInstance("arrow.glb")

                if (modelInstance == null) {
                    Log.e(TAG, "loadModelInstance returned null — check assets/arrow.glb")
                    runOnUiThread {
                        Toast.makeText(this@ARNavigationActivity,
                            "Error: arrow.glb not found", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // arrowNode: only holds the model-correction rotation (90° X to fix Sketchfab bake).
                // Never touched again after creation.
                arrowNode = ModelNode(
                    modelInstance = modelInstance,
                    scaleToUnits  = ARROW_SIZE,
                    centerOrigin  = Position(0f, 0f, 0f)
                ).apply {
                    isEditable = false
                    rotation = Rotation(90f, 0f, 0f)
                }

                // hudNode: plain node on scene root.
                // Handles position (locked in front of camera) + bearing rotation (Y axis).
                hudNode = Node(binding.sceneView.engine).also { hud ->
                    hud.addChildNode(arrowNode!!)
                    binding.sceneView.addChildNode(hud)
                }

                binding.sceneView.onSessionUpdated = { _, _ -> updateHUD() }

                Log.d(TAG, "Setup complete")
                runOnUiThread {
                    Toast.makeText(this@ARNavigationActivity,
                        "AR Navigation Ready", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}", e)
            }
        }
    }

    private fun updateHUD() {
        val hud = hudNode ?: return
        val cam = binding.sceneView.cameraNode

        // Use camera's world yaw (Y Euler angle) to compute horizontal forward vector.
        // We use only yaw so the HUD stays at a fixed height regardless of phone tilt.
        // Forward = -Z in Filament right-hand space → (−sin(yaw), 0, −cos(yaw))
        val yawDeg: Float = cam.worldRotation.y
        val yawRad: Float = Math.toRadians(yawDeg.toDouble()).toFloat()
        val fwdX: Float = -sin(yawRad)
        val fwdZ: Float = -cos(yawRad)

        val camPos = cam.worldPosition

        // Lock HUD in front of camera at fixed distance + vertical drop
        hud.worldPosition = Position(
            x = camPos.x + fwdX * HUD_DISTANCE,
            y = camPos.y + HUD_VERT_OFFSET,
            z = camPos.z + fwdZ * HUD_DISTANCE
        )

        // Smooth shortest-arc lerp toward compass bearing
        val diff: Float = ((targetRotationY - currentRotationY) + 180f) % 360f - 180f
        currentRotationY += diff * ROTATION_LERP

        // Apply bearing as world Y rotation on hudNode.
        // arrowNode inherits this and adds its own local 90° X correction on top.
        hud.worldRotation = Rotation(0f, currentRotationY, 0f)
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.arrowRotation.collectLatest { targetRotation ->
                // If the arrow points BACKWARD after testing, change to:
                //   targetRotationY = targetRotation + 180f
                targetRotationY = targetRotation
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
            LocationServices.getFusedLocationProviderClient(this)
                .lastLocation
                .addOnSuccessListener { it?.let { loc -> viewModel.onLocationUpdated(loc) } }
        } catch (e: SecurityException) { }
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.all { it.value }) { setupARScene(); startSensors() } else finish()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.sceneView.onSessionUpdated = null
        compassManager.stop()
    }
}