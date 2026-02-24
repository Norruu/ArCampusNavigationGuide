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
import kotlinx.coroutines.launch
import com.campus.arnav.databinding.ActivityArNavigationBinding
import com.campus.arnav.util.ARCompassManager
import com.google.android.gms.location.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
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

// A GPS-anchored node arrow placed at each route node
data class RouteArrow(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float,            // direction this arrow points (toward next node)
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

    // World-space route node arrows (long_arrow.glb) — one per route node
    private val routeArrows = mutableListOf<RouteArrow>()

    private val arWaypoints = mutableListOf<ARWaypoint>()
    private var currentLocation: Location? = null
    private var currentHeading: Float = 0f

    // ── Mini-map blue polyline — trimmed as the user walks ───────────────────
    private var routePolyline: Polyline? = null
    // Full ordered list of GPS route points (from intent extras)
    private val routeGeoPoints = mutableListOf<GeoPoint>()
    // Index into routeGeoPoints of the first point not yet passed
    private var routePointsClearedUpTo = 0

    // ── Camera-locked HUD arrow (arrow.glb) ──────────────────────────────────
    // This single node sticks to the camera and rotates to point at the
    // nearest upcoming route node.  It never moves in world space itself.
    private var hudArrowNode: ModelNode? = null

    private var hasArrived = false

    // ── Path clearing: indices of routeArrows already passed by the user ─────
    // Once a node is passed it is permanently hidden and removed from HUD logic.
    private val passedArrowIndices = mutableSetOf<Int>()

    // ── Jump detection: last GPS fix accepted as valid ────────────────────────
    private var lastValidLocation: Location? = null

    companion object {
        const val EXTRA_ROUTE = "extra_route"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        // ── Node arrow sizing (long_arrow.glb placed at every route node) ────
        private const val NODE_ARROW_SCALE      = 50.0f   // large world-space arrows

        // ── HUD arrow sizing (arrow.glb locked to camera) ────────────────────
        private const val HUD_ARROW_SCALE       = 2.0f// size in front of camera

        // ── Destination pin ───────────────────────────────────────────────────
        private const val DESTINATION_SCALE     = 4.5f

        // ── Arrow placement: one node every N metres along the route ──────────
        private const val ARROW_INTERVAL        = 10f

        // ── Vertical heights ──────────────────────────────────────────────────
        private const val NODE_ARROW_Y          = -0.8f   // chest-level world arrows
        private const val DEST_Y               = -0.2f    // eye-level destination pin
        // HUD arrow is placed this far in front of the camera (metres)
        private const val HUD_ARROW_FORWARD     = 7.0f
        // HUD arrow vertical offset relative to camera (negative = below centre)
        private const val HUD_ARROW_Y_OFFSET    = -0.4f

        // ── Visibility ────────────────────────────────────────────────────────
        // World-space node arrows visible only within this distance
        private const val NODE_SHOW_DIST        = 30f
        private const val NODE_HIDE_BEHIND_DIST = 2.5f
        private const val DEST_VISIBLE_DIST     = 150f

        // ── Arrival trigger ───────────────────────────────────────────────────
        private const val ARRIVAL_DISTANCE      = 15f

        // ── Turn detection ────────────────────────────────────────────────────
        private const val TURN_ANGLE_THRESHOLD  = 20f

        // ── Jump / GPS spike detection ────────────────────────────────────────
        // Max realistic walking speed in m/s (~25 km/h).  Any GPS update that
        // implies faster travel than this between two fixes is treated as a
        // sensor spike and discarded.
        private const val MAX_WALKING_SPEED_MS  = 7.0f   // m/s
        // A node is considered "passed" when the user walks within this radius.
        private const val NODE_PASSED_RADIUS    = 8f      // metres
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
        passedArrowIndices.clear()
        lastValidLocation = null
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
                // Build full point list for polyline trimming
                routeGeoPoints.clear()
                routeGeoPoints.addAll(routeLats.indices.map { i -> GeoPoint(routeLats[i], routeLons[i]) })

                routePolyline = Polyline(this).apply {
                    outlinePaint.color       = ContextCompat.getColor(this@ARNavigationActivity, R.color.route_blue)
                    outlinePaint.strokeWidth = 20f
                    outlinePaint.strokeCap   = Paint.Cap.ROUND
                    outlinePaint.strokeJoin  = Paint.Join.ROUND
                    outlinePaint.isAntiAlias = true
                    setPoints(routeGeoPoints)
                }
                overlays.add(routePolyline!!)
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
    // ROUTE NODE GENERATION
    // One RouteArrow is placed at every interpolated node along the route.
    // Each arrow's `bearing` points toward the next node so the 3D model
    // naturally faces the direction of travel.
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

            // Place a long_arrow node at every ARROW_INTERVAL metres along this segment
            var walked = ARROW_INTERVAL / 2f
            while (walked < segmentDistance) {
                val fraction = walked / segmentDistance
                val arrowLat = p1.latitude  + (p2.latitude  - p1.latitude)  * fraction
                val arrowLon = p1.longitude + (p2.longitude - p1.longitude) * fraction
                routeArrows.add(RouteArrow(arrowLat, arrowLon, segmentBearing))
                walked += ARROW_INTERVAL
            }
        }

        // Destination node
        arWaypoints.add(ARWaypoint(lats.last(), lons.last(), WaypointType.DESTINATION, 0f))
        routeArrows.add(RouteArrow(lats.last(), lons.last(), 0f, isDestination = true))
        Log.d(TAG, "Generated ${routeArrows.size} route nodes")
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
            val nodeArrowModel = binding.sceneView.modelLoader.loadModelInstance("long_arrow.glb") ?: return@launch
            val destModel      = binding.sceneView.modelLoader.loadModelInstance("map_pointer_3d_icon.glb")
            val hudArrowModel  = binding.sceneView.modelLoader.loadModelInstance("direction_arrow.glb")

            // ── Place long_arrow.glb at every route node ──────────────────────
            routeArrows.forEach { arrow ->
                val model = if (arrow.isDestination && destModel != null) destModel else nodeArrowModel
                val scale = if (arrow.isDestination) DESTINATION_SCALE else NODE_ARROW_SCALE
                val node  = ModelNode(
                    modelInstance = model,
                    scaleToUnits  = scale,
                    centerOrigin  = Position(0f, 0f, 0f)
                ).apply {
                    isEditable       = false
                    isVisible        = false
                    isShadowCaster   = false
                    isShadowReceiver = false
                }
                binding.sceneView.addChildNode(node)
                arrow.node = node
            }

            // ── Create the single HUD arrow — added to scene, positioned each frame ─
            hudArrowNode = hudArrowModel?.let { model ->
                ModelNode(
                    modelInstance = model,
                    scaleToUnits  = HUD_ARROW_SCALE,
                    centerOrigin  = Position(0f, 0f, 0f)
                ).apply {
                    isEditable       = false
                    isVisible        = false
                    isShadowCaster   = false
                    isShadowReceiver = false
                    binding.sceneView.addChildNode(this)
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // PER-FRAME UPDATE  (called every AR frame)
    // ════════════════════════════════════════════════════════════════════════

    private fun updateARNodesPosition() {
        val userLoc = currentLocation ?: return
        val cam     = binding.sceneView.cameraNode

        // ── 1. Mark nodes as passed & find nearest upcoming node ─────────────
        // We iterate by index so we can permanently hide passed nodes and skip
        // them when looking for the nearest target for the HUD arrow.
        var nearestNonDestArrow: RouteArrow? = null
        var nearestDist = Float.MAX_VALUE

        routeArrows.forEachIndexed { index, arrow ->
            if (arrow.isDestination) return@forEachIndexed
            if (index in passedArrowIndices) return@forEachIndexed   // already passed

            val loc = Location("").apply {
                latitude  = arrow.latitude
                longitude = arrow.longitude
            }
            val d = userLoc.distanceTo(loc)

            // If user is within NODE_PASSED_RADIUS, permanently mark as passed
            if (d < NODE_PASSED_RADIUS) {
                passedArrowIndices.add(index)
                arrow.node?.isVisible = false
                Log.d(TAG, "Node $index passed and cleared")
                trimPolylineToUser(userLoc)
                return@forEachIndexed
            }

            if (d < nearestDist) {
                nearestDist = d
                nearestNonDestArrow = arrow
            }
        }

        // ── 2. Update world-space node arrows (long_arrow.glb) ───────────────
        routeArrows.forEachIndexed { index, arrow ->
            val node = arrow.node ?: return@forEachIndexed

            // Already passed — keep hidden, skip all further logic
            if (index in passedArrowIndices) {
                node.isVisible = false
                return@forEachIndexed
            }

            val arrowLoc = Location("").apply {
                latitude  = arrow.latitude
                longitude = arrow.longitude
            }
            val distToArrow = userLoc.distanceTo(arrowLoc)

            // ── Destination pin ───────────────────────────────────────────────
            if (arrow.isDestination) {
                if (distToArrow < ARRIVAL_DISTANCE && !hasArrived) {
                    hasArrived = true
                    runOnUiThread { showArrivalDialog() }
                }

                if (distToArrow > DEST_VISIBLE_DIST) {
                    node.isVisible = false
                    return@forEachIndexed
                }
                node.isVisible = true

                val bRad = Math.toRadians((userLoc.bearingTo(arrowLoc) - currentHeading).toDouble())
                node.worldPosition = Position(
                    x = cam.worldPosition.x + (distToArrow * sin(bRad)).toFloat(),
                    y = cam.worldPosition.y + DEST_Y,
                    z = cam.worldPosition.z - (distToArrow * cos(bRad)).toFloat()
                )
                node.worldRotation = Rotation(
                    x = 0f,   // upright — no tilt
                    y = (System.currentTimeMillis() % 3600) / 10f,  // slow Y spin
                    z = 0f
                )
                return@forEachIndexed
            }

            // ── Regular long_arrow node ───────────────────────────────────────
            if (distToArrow > NODE_SHOW_DIST) {
                node.isVisible = false
                return@forEachIndexed
            }

            val bearingToArrow  = userLoc.bearingTo(arrowLoc)
            val relativeBearing = ((bearingToArrow - currentHeading + 360f) % 360f)
            val isBehind        = relativeBearing > 90f && relativeBearing < 270f
            if (isBehind && distToArrow < NODE_HIDE_BEHIND_DIST) {
                node.isVisible = false
                return@forEachIndexed
            }

            node.isVisible = true

            val bRad = Math.toRadians((bearingToArrow - currentHeading).toDouble())
            node.worldPosition = Position(
                x = cam.worldPosition.x + (distToArrow * sin(bRad)).toFloat(),
                y = cam.worldPosition.y + NODE_ARROW_Y,
                z = cam.worldPosition.z - (distToArrow * cos(bRad)).toFloat()
            )
            node.worldRotation = Rotation(
                x = 90f,
                y = -(arrow.bearing - currentHeading) - 90f,
                z = 0f
            )
        }

        // ── 3. Update the camera-locked HUD arrow (arrow.glb) ────────────────
        updateHudArrow(cam, nearestNonDestArrow)
    }

    // ════════════════════════════════════════════════════════════════════════
    // POLYLINE TRIMMING
    // Removes the portion of the blue minimap route the user has already walked.
    // We find the closest route point to the user and drop everything before it.
    // ════════════════════════════════════════════════════════════════════════

    private fun trimPolylineToUser(userLoc: Location) {
        val poly = routePolyline ?: return
        if (routeGeoPoints.isEmpty()) return

        // Find the closest route point index ahead of (or at) the current cleared index
        var closestIndex = routePointsClearedUpTo
        var closestDist  = Float.MAX_VALUE

        for (i in routePointsClearedUpTo until routeGeoPoints.size) {
            val pt = routeGeoPoints[i]
            val ptLoc = Location("").apply { latitude = pt.latitude; longitude = pt.longitude }
            val d = userLoc.distanceTo(ptLoc)
            if (d < closestDist) {
                closestDist  = d
                closestIndex = i
            } else {
                // Points are ordered — once distance starts growing we've passed the closest
                break
            }
        }

        if (closestIndex > routePointsClearedUpTo) {
            routePointsClearedUpTo = closestIndex
            val remaining = routeGeoPoints.subList(closestIndex, routeGeoPoints.size)
            runOnUiThread {
                poly.setPoints(remaining)
                binding.miniMapView.invalidate()
            }
            Log.d(TAG, "Polyline trimmed: $closestIndex/${routeGeoPoints.size} points cleared")
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // HUD ARROW  (direction_arrow.glb — world-space, flat compass needle)
    // ════════════════════════════════════════════════════════════════════════

    private fun updateHudArrow(
        cam: io.github.sceneview.node.CameraNode,
        nearestArrow: RouteArrow?
    ) {
        val hud = hudArrowNode ?: return

        if (nearestArrow == null) {
            hud.isVisible = false
            return
        }

        val userLoc = currentLocation ?: run {
            hud.isVisible = false
            return
        }

        hud.isVisible = true

        // ── Position: fixed offset from camera, horizontal plane only ─────────
        // Use only the camera's Y-axis rotation (horizontal yaw) so the arrow
        // always floats in front of the user at chest height regardless of tilt.
        val camPos = cam.worldPosition
        val camYaw = cam.worldRotation.y   // ARCore horizontal yaw, degrees
        val yawRad = Math.toRadians(camYaw.toDouble())

        hud.worldPosition = Position(
            x = camPos.x + (sin(yawRad) * HUD_ARROW_FORWARD).toFloat(),
            y = camPos.y + HUD_ARROW_Y_OFFSET,
            z = camPos.z - (cos(yawRad) * HUD_ARROW_FORWARD).toFloat()
        )

        // ── Rotation ──────────────────────────────────────────────────────────
        // The compass gives us `relativeAngle` = degrees the target is to the
        // right (+) or left (-) of the phone's facing direction.
        // ARCore's world Y-axis is arbitrary, so we add `camYaw` as an offset
        // to translate from compass-space into ARCore world-space.
        val targetLoc = Location("").apply {
            latitude  = nearestArrow.latitude
            longitude = nearestArrow.longitude
        }
        val bearingToNode = userLoc.bearingTo(targetLoc)   // magnetic compass, -180..180
        var relativeAngle = bearingToNode - currentHeading  // degrees right(+) / left(-) of phone
        while (relativeAngle < -180f) relativeAngle += 360f
        while (relativeAngle >  180f) relativeAngle -= 360f

        // Translate into ARCore world-space by adding the camera's current yaw
        val worldAngle = relativeAngle + camYaw

        // x = - 90 → lay model flat (Y-up GLB tipped onto the horizontal plane)
        // y = worldAngle → spin CW/CCW to point at node in world space
        // z = 0   → no roll
        hud.worldRotation = Rotation(
            x = -90f,
            y = worldAngle,
            z = 0f
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // ARRIVAL DIALOG
    // ════════════════════════════════════════════════════════════════════════

    private fun showArrivalDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_arrival, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val targetName = intent.getStringExtra("TARGET_NAME") ?: "Destination"
        dialogView.findViewById<TextView>(R.id.tvArrivalMessage).text =
            "You have successfully reached $targetName."

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
                val newLoc = result.lastLocation ?: return

                // ── Jump detection ────────────────────────────────────────────
                // Reject fixes that imply physically impossible movement speed.
                val prev = lastValidLocation
                if (prev != null) {
                    val distMeters  = prev.distanceTo(newLoc)
                    val timeSecs    = (newLoc.time - prev.time) / 1000f
                    if (timeSecs > 0) {
                        val speedMs = distMeters / timeSecs
                        if (speedMs > MAX_WALKING_SPEED_MS) {
                            Log.w(TAG, "GPS jump rejected: ${speedMs.toInt()} m/s over ${distMeters.toInt()} m")
                            return  // discard this fix
                        }
                    }
                }
                lastValidLocation = newLoc

                currentLocation = newLoc
                binding.miniMapView.controller.setCenter(GeoPoint(newLoc.latitude, newLoc.longitude))
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