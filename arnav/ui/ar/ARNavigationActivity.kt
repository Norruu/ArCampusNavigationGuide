package com.campus.arnav.ui.ar

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.graphics.Paint
import com.campus.arnav.R
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.databinding.ActivityArNavigationBinding
import com.campus.arnav.util.ARCompassManager
import com.campus.arnav.util.LocationUtils
import dagger.hilt.android.AndroidEntryPoint
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.cos
import kotlin.math.sin
import javax.inject.Inject

private const val TAG = "ARNAV_DEBUG"

@AndroidEntryPoint
class ARNavigationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArNavigationBinding
    private val viewModel: ARViewModel by viewModels()

    private lateinit var arCompassManager: ARCompassManager

    private var hudNode: Node? = null
    private var arrowNode: ModelNode? = null
    private var myLocationOverlay: MyLocationNewOverlay? = null

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

        // Initialize the new AR Compass Manager
        arCompassManager = ARCompassManager(this)

        val lat  = intent.getDoubleExtra("TARGET_LAT", 0.0)
        val lon  = intent.getDoubleExtra("TARGET_LON", 0.0)
        val name = intent.getStringExtra("TARGET_NAME") ?: "Destination"

        if (lat != 0.0) {
            viewModel.setTarget(lat, lon, name)
            binding.tvTargetName.text = name
        }

        if (allPermissionsGranted()) {
            setupMiniMap()
            setupARScene()
            startSensors()
        } else {
            requestPermissions.launch(REQUIRED_PERMISSIONS)
        }

        setupObservers()
        binding.btnExitAr.setOnClickListener { finish() }

        binding.btnCancelNavigation.setOnClickListener {
            val resultIntent = android.content.Intent().apply {
                putExtra("CANCEL_NAVIGATION", true)
            }
            setResult(android.app.Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMiniMap() {
        Configuration.getInstance().load(
            this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        )

        binding.miniMapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

            minZoomLevel = 18.0
            controller.setZoom(18.5)

            // 1. Add Campus Paths
            val pathsOverlay = com.campus.arnav.ui.map.components.CampusPathsOverlay()
            pathsOverlay.addPathsToMap(this)

            // 2. RETRIEVE AND DRAW THE BLUE ROUTE
            val routeLats = intent.getDoubleArrayExtra("ROUTE_LATS")
            val routeLons = intent.getDoubleArrayExtra("ROUTE_LONS")

            if (routeLats != null && routeLons != null && routeLats.isNotEmpty()) {
                val geoPoints = routeLats.indices.map { i -> GeoPoint(routeLats[i], routeLons[i]) }

                val routePolyline = org.osmdroid.views.overlay.Polyline(this).apply {
                    outlinePaint.color = ContextCompat.getColor(this@ARNavigationActivity, R.color.route_blue)
                    outlinePaint.strokeWidth = 20f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    outlinePaint.isAntiAlias = true
                    setPoints(geoPoints)
                }
                overlays.add(routePolyline)
            } else {
                android.util.Log.e("ARNAV_DEBUG", "Route arrays were null or empty in Intent!")
            }

            // 3. Setup the Location Overlay (Keeps user in center)
            myLocationOverlay = org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay(
                org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider(context), this
            ).apply {
                enableMyLocation()
                enableFollowLocation()
            }
            overlays.add(myLocationOverlay!!)

            // 4. Apply Dark Mode Filter
            val inverseMatrix = android.graphics.ColorMatrixColorFilter(
                floatArrayOf(
                    -1.0f, 0.0f, 0.0f, 0.0f, 255f,
                    0.0f, -1.0f, 0.0f, 0.0f, 255f,
                    0.0f, 0.0f, -1.0f, 0.0f, 255f,
                    0.0f, 0.0f, 0.0f, 1.0f, 0.0f
                )
            )
            overlayManager.tilesOverlay.setColorFilter(inverseMatrix)

            setOnTouchListener { _, _ -> true } // Disable dragging map

            invalidate()
        }
    }

    private fun setupARScene() {
        binding.sceneView.apply {
            planeRenderer.isVisible = false
            configureSession { _, config ->
                config.lightEstimationMode = com.google.ar.core.Config.LightEstimationMode.DISABLED
                config.depthMode = com.google.ar.core.Config.DepthMode.DISABLED
            }
        }

        lifecycleScope.launch {
            try {
                val modelInstance = binding.sceneView.modelLoader.loadModelInstance("arrow.glb")

                if (modelInstance == null) {
                    runOnUiThread {
                        Toast.makeText(this@ARNavigationActivity,
                            "Error: arrow.glb not found", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                arrowNode = ModelNode(
                    modelInstance = modelInstance,
                    scaleToUnits  = ARROW_SIZE,
                    centerOrigin  = Position(0f, 0f, 0f)
                ).apply {
                    isEditable = false
                    rotation = Rotation(90f, 0f, 0f)
                }

                hudNode = Node(binding.sceneView.engine).also { hud ->
                    hud.addChildNode(arrowNode!!)
                    binding.sceneView.addChildNode(hud)
                }

                binding.sceneView.onSessionUpdated = { _, _ -> updateHUD() }

            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}", e)
            }
        }
    }

    private fun updateHUD() {
        val hud = hudNode ?: return
        val cam = binding.sceneView.cameraNode

        val yawDeg: Float = cam.worldRotation.y
        val yawRad: Float = Math.toRadians(yawDeg.toDouble()).toFloat()
        val fwdX: Float = -sin(yawRad)
        val fwdZ: Float = -cos(yawRad)

        val camPos = cam.worldPosition

        hud.worldPosition = Position(
            x = camPos.x + fwdX * HUD_DISTANCE,
            y = camPos.y + HUD_VERT_OFFSET,
            z = camPos.z + fwdZ * HUD_DISTANCE
        )

        val diff: Float = ((targetRotationY - currentRotationY) + 180f) % 360f - 180f
        currentRotationY += diff * ROTATION_LERP

        hud.worldRotation = Rotation(0f, currentRotationY, 0f)
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.arrowRotation.collectLatest { targetRotation ->
                targetRotationY = targetRotation
            }
        }
        lifecycleScope.launch {
            viewModel.distanceToTarget.collectLatest { distance ->
                binding.tvDistance.text = distance
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startSensors() {
        // 1. Start the AR Compass
        arCompassManager.start { relativeAngle ->
            // Rotate the minimap so the path points UP when the phone is held vertically
            binding.miniMapView.mapOrientation = -relativeAngle
        }

        // 2. Start GPS Location
        try {
            com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
                .lastLocation
                .addOnSuccessListener { loc ->
                    loc?.let {
                        viewModel.onLocationUpdated(it)

                        // Update Map Center
                        val currentGeoPoint = GeoPoint(it.latitude, it.longitude)
                        binding.miniMapView.controller.setCenter(currentGeoPoint)

                        // Calculate bearing to destination and feed it to the AR compass
                        val targetLat = intent.getDoubleExtra("TARGET_LAT", 0.0)
                        val targetLon = intent.getDoubleExtra("TARGET_LON", 0.0)

                        if (targetLat != 0.0) {
                            val currentLoc = CampusLocation("current", it.latitude, it.longitude)
                            val targetLoc = CampusLocation("target", targetLat, targetLon)

                            val bearingToNextWaypoint = LocationUtils.bearing(currentLoc, targetLoc)
                            arCompassManager.setPathBearing(bearingToNextWaypoint.toFloat())
                        }
                    }
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission denied", e)
        }
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.all { it.value }) {
            setupMiniMap()
            setupARScene()
            startSensors()
        } else {
            finish()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        binding.sceneView.onSessionUpdated = null
        arCompassManager.stop()
        binding.miniMapView.onDetach()
        super.onDestroy()
    }
}