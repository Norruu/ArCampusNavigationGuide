package com.campus.arnav.ui.ar

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.graphics.Paint
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.campus.arnav.R
import com.campus.arnav.databinding.ActivityArNavigationBinding
import com.campus.arnav.util.ARCompassManager
import com.google.android.gms.location.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val TAG = "ARNAV_DEBUG"

// A single GPS-anchored AR arrow on the route
data class RouteArrow(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float,          // direction this arrow points (toward next point)
    val isDestination: Boolean = false,
    var node: ModelNode? = null
)

enum class WaypointType { TURN, DESTINATION }

data class ARWaypoint(
    val latitude: Double,
    val longitude: Double,
    val type: WaypointType,
    var bearingToNext: Float = 0f,
    var node: ModelNode? = null
) {
    val isDestination get() = type == WaypointType.DESTINATION
}

@AndroidEntryPoint
class ARNavigationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArNavigationBinding
    private lateinit var arCompassManager: ARCompassManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var destinationMarker: Marker? = null

    // All GPS-anchored arrows placed along the route
    private val routeArrows = mutableListOf<RouteArrow>()

    private val arWaypoints = mutableListOf<ARWaypoint>()
    private var currentLocation: Location? = null
    private var currentHeading: Float = 0f

    // --- NEW: Prevents the dialog from popping up multiple times ---
    private var hasArrived = false

    companion object {
        const val EXTRA_ROUTE = "extra_route"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        // ── Arrow & Pin Sizing ──────────────
        private const val ARROW_SCALE       = 20.0f
        private const val DESTINATION_SCALE = 4.5f

        // ── Arrow placement along the route ──────────────────────────────
        private const val ARROW_INTERVAL    = 10f      // metres between arrows

        // ── Vertical Heights (Floating in the air) ───────────────────────
        private const val ARROW_Y           = -0.8f   // Floats around chest level
        private const val DEST_Y            = -0.2f   // Floats higher up, near eye level

        // ── Visibility rules — Google Maps behaviour ──────────────────────
        private const val SHOW_DIST         = 30f
        private const val HIDE_BEHIND_DIST  = 2.5f

        // ── Destination pin ────────────────────────────────────────────────
        private const val DEST_VISIBLE_DIST = 150f

        // --- NEW: Distance in meters to trigger the arrival dialog ---
        private const val ARRIVAL_DISTANCE  = 15f

        // ── Turn detection ─────────────────────────────────────────────────
        private const val TURN_ANGLE_THRESHOLD = 20f
    }

    // ════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        arCompassManager    = ARCompassManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        binding.tvTargetName.text = intent.getStringExtra("TARGET_NAME") ?: "Destination"

        if (allPermissionsGranted()) {
            setupMiniMap(); setupARScene(); startSensors()
        } else {
            requestPermissions.launch(REQUIRED_PERMISSIONS)
        }

        binding.btnExitAr.setOnClickListener { finish() }
        binding.btnCancelNavigation.setOnClickListener {
            setResult(android.app.Activity.RESULT_OK,
                android.content.Intent().apply { putExtra("CANCEL_NAVIGATION", true) })
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.miniMapView.onResume()
        myLocationOverlay?.enableMyLocation()
        myLocationOverlay?.enableFollowLocation()
        startService(android.content.Intent(this,
            com.campus.arnav.service.NavigationService::class.java).apply {
            action = com.campus.arnav.service.NavigationService.ACTION_HIDE_OVERLAY
        })
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.provider.Settings.canDrawOverlays(this)) {
            startService(android.content.Intent(this,
                com.campus.arnav.service.NavigationService::class.java).apply {
                action = com.campus.arnav.service.NavigationService.ACTION_SHOW_OVERLAY
            })
        }
    }

    override fun onPause() {
        super.onPause()
        binding.miniMapView.onPause()
        myLocationOverlay?.disableMyLocation()
    }

    override fun onDestroy() {
        binding.sceneView.onSessionUpdated = null
        arCompassManager.stop()
        if (::locationCallback.isInitialized)
            fusedLocationClient.removeLocationUpdates(locationCallback)
        binding.miniMapView.onDetach()
        super.onDestroy()
    }

    // ════════════════════════════════════════════════════════════════════════
    // MINI MAP
    // ════════════════════════════════════════════════════════════════════════

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMiniMap() {
        Configuration.getInstance().load(this,
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))

        val satelliteTileSource = object : org.osmdroid.tileprovider.tilesource.XYTileSource(
            "Google-Satellite", 1, 20, 256, ".png",
            arrayOf("https://mt1.google.com/vt/lyrs=s")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String =
                "$baseUrl&x=${org.osmdroid.util.MapTileIndex.getX(pMapTileIndex)}" +
                        "&y=${org.osmdroid.util.MapTileIndex.getY(pMapTileIndex)}" +
                        "&z=${org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex)}"
        }

        val targetLat = intent.getDoubleExtra("TARGET_LAT", 0.0)
        val targetLon = intent.getDoubleExtra("TARGET_LON", 0.0)

        binding.miniMapView.apply {
            setTileSource(satelliteTileSource)
            setMultiTouchControls(false)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 18.0
            controller.setZoom(18.5)

            val pathsOverlay = com.campus.arnav.ui.map.components.CampusPathsOverlay()
            pathsOverlay.addPathsToMap(this)

            val routeLats = intent.getDoubleArrayExtra("ROUTE_LATS")
            val routeLons = intent.getDoubleArrayExtra("ROUTE_LONS")
            if (routeLats != null && routeLons != null && routeLats.isNotEmpty()) {
                overlays.add(Polyline(this).apply {
                    outlinePaint.color       = ContextCompat.getColor(this@ARNavigationActivity, R.color.route_blue)
                    outlinePaint.strokeWidth = 20f
                    outlinePaint.strokeCap   = Paint.Cap.ROUND
                    outlinePaint.strokeJoin  = Paint.Join.ROUND
                    outlinePaint.isAntiAlias = true
                    setPoints(routeLats.indices.map { i -> GeoPoint(routeLats[i], routeLons[i]) })
                })
                generateRouteArrows(routeLats, routeLons)
            }

            if (targetLat != 0.0 && targetLon != 0.0) {
                destinationMarker = Marker(this).apply {
                    position = GeoPoint(targetLat, targetLon)
                    title    = intent.getStringExtra("TARGET_NAME") ?: "Destination"
                    val d = ContextCompat.getDrawable(
                        this@ARNavigationActivity, R.drawable.ic_destination)?.mutate()
                    d?.setTint(Color.RED)
                    icon = d
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    infoWindow = null
                }
                overlays.add(destinationMarker!!)
            }

            myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this).apply {
                enableMyLocation(); enableFollowLocation()
            }
            overlays.add(myLocationOverlay!!)
            overlayManager.tilesOverlay.setColorFilter(null)
            setOnTouchListener { _, _ -> true }
            invalidate()
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // ROUTE ARROW GENERATION
    // ════════════════════════════════════════════════════════════════════════

    private fun generateRouteArrows(lats: DoubleArray, lons: DoubleArray) {
        routeArrows.clear()
        arWaypoints.clear()

        for (i in 0 until lats.size - 1) {
            val p1 = Location("").apply { latitude = lats[i];   longitude = lons[i]   }
            val p2 = Location("").apply { latitude = lats[i+1]; longitude = lons[i+1] }
            val segmentBearing  = p1.bearingTo(p2)
            val segmentDistance = p1.distanceTo(p2)

            val isTurn = if (i > 0) {
                val prev  = Location("").apply { latitude = lats[i-1]; longitude = lons[i-1] }
                val delta = abs(segmentBearing - prev.bearingTo(p1))
                    .let { if (it > 180f) 360f - it else it }
                delta > TURN_ANGLE_THRESHOLD
            } else false

            if (isTurn) arWaypoints.add(
                ARWaypoint(p1.latitude, p1.longitude, WaypointType.TURN, segmentBearing)
            )

            var walked = ARROW_INTERVAL / 2f
            while (walked < segmentDistance) {
                val fraction = walked / segmentDistance
                val arrowLat = p1.latitude  + (p2.latitude  - p1.latitude)  * fraction
                val arrowLon = p1.longitude + (p2.longitude - p1.longitude) * fraction
                routeArrows.add(RouteArrow(arrowLat, arrowLon, segmentBearing))
                walked += ARROW_INTERVAL
            }
        }

        arWaypoints.add(ARWaypoint(lats.last(), lons.last(), WaypointType.DESTINATION, 0f))
        routeArrows.add(RouteArrow(lats.last(), lons.last(), 0f, isDestination = true))
        Log.d(TAG, "Generated ${routeArrows.size} route arrows")
    }

    // ════════════════════════════════════════════════════════════════════════
    // AR SCENE
    // ════════════════════════════════════════════════════════════════════════

    private fun setupARScene() {
        binding.sceneView.apply {
            planeRenderer.isVisible = false
            configureSession { _, config ->
                config.lightEstimationMode =
                    com.google.ar.core.Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.depthMode = com.google.ar.core.Config.DepthMode.DISABLED
            }
            onSessionUpdated = { _, _ -> updateARNodesPosition() }
        }

        lifecycleScope.launch {
            try {
                val arrowModel = binding.sceneView.modelLoader.loadModelInstance("pathway.glb")
                val destModel  = binding.sceneView.modelLoader.loadModelInstance("map_pointer_3d_icon.glb")

                if (arrowModel == null) {
                    Log.e(TAG, "pathway.glb not found in assets/")
                    return@launch
                }

                routeArrows.forEach { arrow ->
                    val model = if (arrow.isDestination && destModel != null) destModel else arrowModel
                    val scale = if (arrow.isDestination) DESTINATION_SCALE else ARROW_SCALE
                    val node  = ModelNode(
                        modelInstance = model,
                        scaleToUnits  = scale,
                        centerOrigin  = Position(0f, 0f, 0f)
                    ).apply {
                        isEditable = false
                        isVisible = false
                        isShadowCaster = false
                        isShadowReceiver = false
                    }

                    if (!arrow.isDestination) {
                        node.scale = Position(node.scale.x, node.scale.y, node.scale.z * 5.0f)
                    }

                    binding.sceneView.addChildNode(node)
                    arrow.node = node
                }

                Log.d(TAG, "AR nodes attached to ${routeArrows.size} route arrows")

            } catch (e: Exception) {
                Log.e(TAG, "setupARScene error: ${e.message}", e)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // PER-FRAME UPDATE
    // ════════════════════════════════════════════════════════════════════════

    private fun updateARNodesPosition() {
        val userLoc = currentLocation ?: return
        val cam     = binding.sceneView.cameraNode

        routeArrows.forEach { arrow ->
            val node = arrow.node ?: return@forEach

            val arrowLoc = Location("").apply {
                latitude  = arrow.latitude
                longitude = arrow.longitude
            }

            val distToArrow = userLoc.distanceTo(arrowLoc)

            // ── Destination pin — always show when within range ───────────
            if (arrow.isDestination) {

                // --- NEW: TRIGGER ARRIVAL DIALOG ---
                if (distToArrow < ARRIVAL_DISTANCE && !hasArrived) {
                    hasArrived = true
                    runOnUiThread { showArrivalDialog() }
                }

                if (distToArrow > DEST_VISIBLE_DIST) {
                    node.isVisible = false
                    return@forEach
                }
                node.isVisible = true
                val bRad = Math.toRadians((userLoc.bearingTo(arrowLoc) - currentHeading).toDouble())
                node.worldPosition = Position(
                    x = cam.worldPosition.x + (distToArrow * sin(bRad)).toFloat(),
                    y = cam.worldPosition.y + DEST_Y,
                    z = cam.worldPosition.z - (distToArrow * cos(bRad)).toFloat()
                )

                node.worldRotation = Rotation(-90f, (System.currentTimeMillis() % 3600) / 10f, 0f)
                return@forEach
            }

            // ── Regular direction arrows ──────────────────────────────────

            if (distToArrow > SHOW_DIST) {
                node.isVisible = false
                return@forEach
            }

            val bearingToArrow = userLoc.bearingTo(arrowLoc)
            val relativeBearing = ((bearingToArrow - currentHeading + 360f) % 360f)
            val isBehind = relativeBearing > 90f && relativeBearing < 270f
            if (isBehind && distToArrow < HIDE_BEHIND_DIST) {
                node.isVisible = false
                return@forEach
            }

            node.isVisible = true

            val bRad = Math.toRadians((bearingToArrow - currentHeading).toDouble())
            node.worldPosition = Position(
                x = cam.worldPosition.x + (distToArrow * sin(bRad)).toFloat(),
                y = cam.worldPosition.y + ARROW_Y,
                z = cam.worldPosition.z - (distToArrow * cos(bRad)).toFloat()
            )

            node.worldRotation = Rotation(
                x = 90f,
                y = -(arrow.bearing - currentHeading) - 90f,
                z = 0f
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // NEW: ARRIVAL DIALOG LOGIC
    // ════════════════════════════════════════════════════════════════════════
    private fun showArrivalDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_arrival, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val targetName = intent.getStringExtra("TARGET_NAME") ?: "Destination"

        dialogView.findViewById<TextView>(R.id.tvArrivalMessage).text = "You have successfully reached $targetName."

        dialogView.findViewById<MaterialButton>(R.id.btnFinishNavigation).setOnClickListener {
            dialog.dismiss()
            setResult(android.app.Activity.RESULT_OK,
                android.content.Intent().apply { putExtra("CANCEL_NAVIGATION", true) })
            finish()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    // ════════════════════════════════════════════════════════════════════════
    // SENSORS
    // ════════════════════════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    private fun startSensors() {
        arCompassManager.setPathBearing(0f)
        arCompassManager.start { magneticNorth ->
            currentHeading = magneticNorth
            binding.miniMapView.mapOrientation = -magneticNorth
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L).build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    currentLocation = it
                    binding.miniMapView.controller.setCenter(GeoPoint(it.latitude, it.longitude))
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            if (perms.all { it.value }) { setupMiniMap(); setupARScene(); startSensors() } else finish()
        }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}