package com.campus.arnav.ui.ar

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.content.Intent
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.campus.arnav.R
import com.campus.arnav.databinding.ActivityArNavigationBinding
import com.campus.arnav.service.NavigationService
import com.campus.arnav.ui.map.components.CampusPathsOverlay
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
import kotlin.math.*

private const val TAG = "ARNAV_DEBUG"

data class RouteArrow(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float,
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

/**
 * Shared snap engine for map + AR behavior parity.
 */
class SnapToPathEngine(
    private val smoothingAlpha: Float = 0.18f,
    private val snapMaxDistanceM: Float = 25f,
    private val forwardSearchWindow: Int = 5,
    private val predictionSecs: Float = 0.8f
) {
    var currentSegmentIndex: Int = 0
        private set

    var smoothedLat: Double = 0.0
        private set
    var smoothedLon: Double = 0.0
        private set

    private var initialised = false
    private var prevLat = 0.0
    private var prevLon = 0.0
    private var prevTimeMs = 0L

    fun reset() {
        currentSegmentIndex = 0
        initialised = false
        prevLat = 0.0
        prevLon = 0.0
        prevTimeMs = 0L
        Log.d(TAG, "SnapToPathEngine reset")
    }

    fun update(rawLocation: Location, routePoints: List<Location>): Location {
        if (routePoints.size < 2) return rawLocation

        val predictedLoc = computePredictedLocation(rawLocation)

        val windowEnd = minOf(currentSegmentIndex + forwardSearchWindow, routePoints.lastIndex)
        var best = findBestProjection(predictedLoc, routePoints, currentSegmentIndex, windowEnd)

        if (best.distM > snapMaxDistanceM * 2.0) {
            val fullScan = findBestProjection(predictedLoc, routePoints, currentSegmentIndex, routePoints.lastIndex)
            if (fullScan.distM < best.distM) best = fullScan
        }

        if (best.segIdx > currentSegmentIndex) {
            currentSegmentIndex = best.segIdx
        }

        val chosen = if (best.segIdx < routePoints.lastIndex) {
            projectOntoSegment(
                userLat = rawLocation.latitude,
                userLon = rawLocation.longitude,
                aLat = routePoints[best.segIdx].latitude,
                aLon = routePoints[best.segIdx].longitude,
                bLat = routePoints[best.segIdx + 1].latitude,
                bLon = routePoints[best.segIdx + 1].longitude
            )
        } else best

        val blendAlpha: Double = when {
            chosen.distM <= snapMaxDistanceM -> 1.0
            chosen.distM <= snapMaxDistanceM * 2.0 ->
                1.0 - ((chosen.distM - snapMaxDistanceM) / snapMaxDistanceM)
            else -> 0.0
        }

        val targetLat = lerp(rawLocation.latitude, chosen.projLat, blendAlpha)
        val targetLon = lerp(rawLocation.longitude, chosen.projLon, blendAlpha)

        if (!initialised) {
            smoothedLat = targetLat
            smoothedLon = targetLon
            initialised = true
        } else {
            smoothedLat = lerp(smoothedLat, targetLat, smoothingAlpha.toDouble())
            smoothedLon = lerp(smoothedLon, targetLon, smoothingAlpha.toDouble())
        }

        return Location(rawLocation).apply {
            latitude = smoothedLat
            longitude = smoothedLon
        }
    }

    private data class Projection(
        val projLat: Double,
        val projLon: Double,
        val distM: Double,
        val segIdx: Int = 0
    )

    private fun findBestProjection(
        loc: Location,
        routePoints: List<Location>,
        startSeg: Int,
        endSeg: Int
    ): Projection {
        var best = Projection(loc.latitude, loc.longitude, Double.MAX_VALUE, startSeg)
        for (i in startSeg until endSeg) {
            val p = projectOntoSegment(
                userLat = loc.latitude,
                userLon = loc.longitude,
                aLat = routePoints[i].latitude,
                aLon = routePoints[i].longitude,
                bLat = routePoints[i + 1].latitude,
                bLon = routePoints[i + 1].longitude
            ).copy(segIdx = i)
            if (p.distM < best.distM) best = p
        }
        return best
    }

    private fun projectOntoSegment(
        userLat: Double, userLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double
    ): Projection {
        val cosLat = cos(Math.toRadians(aLat))
        val mPerDegLat = 111_319.5
        val mPerDegLon = cosLat * 111_319.5

        val auE = (userLon - aLon) * mPerDegLon
        val auN = (userLat - aLat) * mPerDegLat
        val abE = (bLon - aLon) * mPerDegLon
        val abN = (bLat - aLat) * mPerDegLat

        val abLenSq = abE * abE + abN * abN
        if (abLenSq == 0.0) return Projection(aLat, aLon, haversine(userLat, userLon, aLat, aLon))

        val t = ((auE * abE + auN * abN) / abLenSq).coerceIn(0.0, 1.0)
        val projLat = aLat + t * (bLat - aLat)
        val projLon = aLon + t * (bLon - aLon)

        return Projection(projLat, projLon, haversine(userLat, userLon, projLat, projLon))
    }

    private fun computePredictedLocation(rawLocation: Location): Location {
        val now = rawLocation.time
        val dt = (now - prevTimeMs) / 1000.0

        val predicted = if (initialised && dt in 0.1..2.0 && predictionSecs > 0f) {
            val vLat = (rawLocation.latitude - prevLat) / dt
            val vLon = (rawLocation.longitude - prevLon) / dt
            Location(rawLocation).apply {
                latitude = rawLocation.latitude + vLat * predictionSecs
                longitude = rawLocation.longitude + vLon * predictionSecs
            }
        } else rawLocation

        prevLat = rawLocation.latitude
        prevLon = rawLocation.longitude
        prevTimeMs = now
        return predicted
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * asin(sqrt(a))
    }
}

@AndroidEntryPoint
class ARNavigationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArNavigationBinding
    private lateinit var arCompassManager: ARCompassManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var destinationMarker: Marker? = null

    private val routeArrows = mutableListOf<RouteArrow>()
    private val arWaypoints = mutableListOf<ARWaypoint>()

    // Raw GPS: ONLY for blue dot + off-route gap checks
    private var currentLocation: Location? = null
    // Snapped location: source of truth for nav logic
    private var snappedLocation: Location? = null

    private var currentHeading: Float = 0f

    private var routePolyline: Polyline? = null
    private val routeGeoPoints = mutableListOf<GeoPoint>()
    private var routePointsClearedUpTo = 0

    private var connectorPolyline: Polyline? = null
    private var markerPolyline: Polyline? = null

    private var hudArrowNode: ModelNode? = null
    private var hasArrived = false
    private val passedArrowIndices = mutableSetOf<Int>()
    private var lastValidLocation: Location? = null
    private var lastSentInstruction: String = ""
    private var lastSentDistance: String = ""
    private var isArLoaded = false
    private var isLocationFound = false

    private val snapEngine = SnapToPathEngine(
        smoothingAlpha = 0.18f,
        snapMaxDistanceM = 25f,
        forwardSearchWindow = 5,
        predictionSecs = 0.8f
    )

    private val arrowController = ARNavigationArrowController(
        smoothingAlpha = 0.85f,
        nodeReachedRadiusM = 8f,
        forwardAngleThresholdDeg = 100f,
        stabilityFramesRequired = 10,
        stabilityMovementThreshold = 0.05f
    )

    @Volatile private var lastCameraWorldYDeg: Float = 0f
    private val routeLocations = mutableListOf<Location>()

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        private const val NODE_ARROW_SCALE = 50.0f
        private const val HUD_ARROW_SCALE = 2.0f
        private const val DESTINATION_SCALE = 4.5f
        private const val ARROW_INTERVAL = 10f
        private const val NODE_ARROW_Y = -0.8f
        private const val DEST_Y = -0.2f
        private const val HUD_ARROW_FORWARD = 3.0f
        private const val HUD_ARROW_Y_OFFSET = -0.3f
        private const val NODE_SHOW_DIST = 30f
        private const val DEST_VISIBLE_DIST = 150f
        private const val ARRIVAL_DISTANCE = 15f
        private const val TURN_ANGLE_THRESHOLD = 20f
        private const val MAX_WALKING_SPEED_MS = 7.0f
        private const val ARROW_MODEL_FLIP_DEG = 180f
        private const val CONNECTOR_HIDE_RADIUS_M = 3f
        private const val DEST_NEAR_RADIUS_M = 20f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loadingOverlay.visibility = View.VISIBLE

        arCompassManager = ARCompassManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        binding.tvTargetName.text = intent.getStringExtra("TARGET_NAME") ?: "Destination"

        if (allPermissionsGranted()) {
            setupMiniMap()
            setupARScene()
            startSensors()
        } else {
            requestPermissions.launch(REQUIRED_PERMISSIONS)
        }

        binding.btnExitAr.setOnClickListener { finish() }
        binding.btnCancelNavigation.setOnClickListener {
            setResult(RESULT_OK, android.content.Intent().apply {
                putExtra("CANCEL_NAVIGATION", true)
            })
            finish()
        }
    }

    private fun checkLoadingStatus() {
        if (isArLoaded && isLocationFound) {
            runOnUiThread {
                binding.loadingOverlay.animate()
                    .alpha(0f)
                    .setDuration(400)
                    .withEndAction { binding.loadingOverlay.visibility = View.GONE }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.miniMapView.onResume()
        myLocationOverlay?.enableMyLocation()
        myLocationOverlay?.enableFollowLocation()

        val hideIntent = Intent(this, NavigationService::class.java).apply {
            action = NavigationService.ACTION_HIDE_OVERLAY
        }
        ContextCompat.startForegroundService(this, hideIntent)
    }

    override fun onPause() {
        super.onPause()
        binding.miniMapView.onPause()
        myLocationOverlay?.disableMyLocation()

        // Show overlay when app goes background
        if (android.provider.Settings.canDrawOverlays(this)) {
            val showIntent = Intent(this, NavigationService::class.java).apply {
                action = NavigationService.ACTION_SHOW_OVERLAY
            }
            ContextCompat.startForegroundService(this, showIntent)
        }
    }

    override fun onDestroy() {
        binding.sceneView.onSessionUpdated = null
        arCompassManager.stop()
        if (::locationCallback.isInitialized) fusedLocationClient.removeLocationUpdates(locationCallback)
        binding.miniMapView.onDetach()
        arrowController.reset()
        snapEngine.reset()
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMiniMap() {
        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))

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

            CampusPathsOverlay().addPathsToMap(this)

            val routeLats = intent.getDoubleArrayExtra("ROUTE_LATS")
            val routeLons = intent.getDoubleArrayExtra("ROUTE_LONS")
            if (routeLats != null && routeLons != null && routeLats.isNotEmpty()) {
                routeGeoPoints.clear()
                routeGeoPoints.addAll(routeLats.indices.map { i -> GeoPoint(routeLats[i], routeLons[i]) })

                routePolyline = Polyline(this).apply {
                    outlinePaint.color = ContextCompat.getColor(this@ARNavigationActivity, R.color.route_blue)
                    outlinePaint.strokeWidth = 20f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    outlinePaint.isAntiAlias = true
                    setPoints(routeGeoPoints)
                }
                overlays.add(routePolyline!!)

                connectorPolyline = Polyline(this).apply {
                    outlinePaint.color = ContextCompat.getColor(this@ARNavigationActivity, R.color.route_blue)
                    outlinePaint.strokeWidth = 20f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    outlinePaint.isAntiAlias = true
                    isVisible = false
                    setPoints(emptyList())
                }
                overlays.add(connectorPolyline!!)

                markerPolyline = Polyline(this).apply {
                    outlinePaint.color = ContextCompat.getColor(this@ARNavigationActivity, R.color.route_blue)
                    outlinePaint.strokeWidth = 20f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    outlinePaint.isAntiAlias = true
                    isVisible = false
                    setPoints(emptyList())
                }
                overlays.add(markerPolyline!!)

                generateRouteArrows(routeLats, routeLons)
                buildRouteLocations(routeLats, routeLons)
            }

            if (targetLat != 0.0 && targetLon != 0.0) {
                destinationMarker = Marker(this).apply {
                    position = GeoPoint(targetLat, targetLon)
                    title = intent.getStringExtra("TARGET_NAME") ?: "Destination"
                    val d = ContextCompat.getDrawable(this@ARNavigationActivity, R.drawable.ic_destination)?.mutate()
                    d?.setTint(Color.RED)
                    icon = d
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    infoWindow = null
                }
                overlays.add(destinationMarker!!)
            }

            myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this).apply {
                enableMyLocation()
                enableFollowLocation()
            }
            overlays.add(myLocationOverlay!!)

            overlayManager.tilesOverlay.setColorFilter(null)
            setOnTouchListener { _, _ -> true }
            invalidate()
        }
    }

    private fun buildRouteLocations(lats: DoubleArray, lons: DoubleArray) {
        routeLocations.clear()
        for (i in lats.indices) {
            routeLocations.add(Location("route").apply {
                latitude = lats[i]
                longitude = lons[i]
            })
        }
        snapEngine.reset()
        arrowController.reset()
        routePointsClearedUpTo = 0
    }

    private fun generateRouteArrows(lats: DoubleArray, lons: DoubleArray) {
        routeArrows.clear()
        arWaypoints.clear()

        for (i in 0 until lats.size - 1) {
            val p1 = Location("").apply { latitude = lats[i]; longitude = lons[i] }
            val p2 = Location("").apply { latitude = lats[i + 1]; longitude = lons[i + 1] }
            val segmentBearing = p1.bearingTo(p2)
            val segmentDistance = p1.distanceTo(p2)

            val isTurn = if (i > 0) {
                val prev = Location("").apply { latitude = lats[i - 1]; longitude = lons[i - 1] }
                val delta = abs(segmentBearing - prev.bearingTo(p1)).let { if (it > 180f) 360f - it else it }
                delta > TURN_ANGLE_THRESHOLD
            } else false

            if (isTurn) arWaypoints.add(ARWaypoint(p1.latitude, p1.longitude, WaypointType.TURN, segmentBearing))

            var walked = ARROW_INTERVAL / 2f
            while (walked < segmentDistance) {
                val fraction = walked / segmentDistance
                routeArrows.add(
                    RouteArrow(
                        latitude = p1.latitude + (p2.latitude - p1.latitude) * fraction,
                        longitude = p1.longitude + (p2.longitude - p1.longitude) * fraction,
                        bearing = segmentBearing
                    )
                )
                walked += ARROW_INTERVAL
            }
        }

        arWaypoints.add(ARWaypoint(lats.last(), lons.last(), WaypointType.DESTINATION, 0f))
        routeArrows.add(RouteArrow(lats.last(), lons.last(), 0f, isDestination = true))
    }

    private fun setupARScene() {
        binding.sceneView.apply {
            planeRenderer.isVisible = false
            configureSession { _, config ->
                config.lightEstimationMode = com.google.ar.core.Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.depthMode = com.google.ar.core.Config.DepthMode.DISABLED
            }
            onSessionUpdated = { _, _ ->
                val cam = cameraNode
                lastCameraWorldYDeg = cam.worldRotation.y
                arrowController.reportCameraPosition(cam.worldPosition.x, cam.worldPosition.y, cam.worldPosition.z)
                updateARNodesPosition()
            }
        }

        lifecycleScope.launch {
            val nodeArrowModel = binding.sceneView.modelLoader.loadModelInstance("long_arrow.glb") ?: return@launch
            val destModel = binding.sceneView.modelLoader.loadModelInstance("map_pointer_3d_icon.glb")
            val hudArrowModel = binding.sceneView.modelLoader.loadModelInstance("direction_arrow.glb")

            routeArrows.forEach { arrow ->
                val model = if (arrow.isDestination && destModel != null) destModel else nodeArrowModel
                val scale = if (arrow.isDestination) DESTINATION_SCALE else NODE_ARROW_SCALE
                ModelNode(modelInstance = model, scaleToUnits = scale, centerOrigin = Position(0f, 0f, 0f)).apply {
                    isEditable = false
                    isVisible = false
                    isShadowCaster = false
                    isShadowReceiver = false
                    binding.sceneView.addChildNode(this)
                    arrow.node = this
                }
            }

            hudArrowNode = hudArrowModel?.let { model ->
                ModelNode(modelInstance = model, scaleToUnits = HUD_ARROW_SCALE, centerOrigin = Position(0f, 0f, 0f)).apply {
                    isEditable = false
                    isVisible = false
                    isShadowCaster = false
                    isShadowReceiver = false
                    binding.sceneView.addChildNode(this)
                }
            }

            isArLoaded = true
            checkLoadingStatus()
        }
    }

    private fun updateARNodesPosition() {
        val userLoc = snappedLocation ?: return
        val cam = binding.sceneView.cameraNode

        val controllerTarget = arrowController.targetNodeIndex
        routeArrows.forEachIndexed { index, arrow ->
            if (!arrow.isDestination && index < controllerTarget) {
                if (passedArrowIndices.add(index)) arrow.node?.isVisible = false
            }
        }

        routeArrows.forEachIndexed { index, arrow ->
            val node = arrow.node ?: return@forEachIndexed
            if (passedArrowIndices.contains(index)) {
                node.isVisible = false
                return@forEachIndexed
            }

            val arrowLoc = Location("").apply {
                latitude = arrow.latitude
                longitude = arrow.longitude
            }
            val distToArrow = userLoc.distanceTo(arrowLoc)

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
                node.worldRotation = Rotation(0f, (System.currentTimeMillis() % 3600) / 10f, 0f)
                return@forEachIndexed
            }

            if (distToArrow > NODE_SHOW_DIST) {
                node.isVisible = false
                return@forEachIndexed
            }
            node.isVisible = true
            val bRad = Math.toRadians((userLoc.bearingTo(arrowLoc) - currentHeading).toDouble())
            node.worldPosition = Position(
                x = cam.worldPosition.x + (distToArrow * sin(bRad)).toFloat(),
                y = cam.worldPosition.y + NODE_ARROW_Y,
                z = cam.worldPosition.z - (distToArrow * cos(bRad)).toFloat()
            )
            node.worldRotation = Rotation(90f, -(arrow.bearing - currentHeading) + ARROW_MODEL_FLIP_DEG, 0f)
        }

        updateHudArrow(cam)
    }

    private fun trimPolylineToUser(userLoc: Location, snappedLoc: Location) {
        val poly = routePolyline ?: return
        if (routeGeoPoints.isEmpty()) return

        val scanLimit = minOf(snapEngine.currentSegmentIndex + 2, routeGeoPoints.lastIndex)
        var closestIndex = routePointsClearedUpTo
        var closestDist = Float.MAX_VALUE

        for (i in routePointsClearedUpTo..scanLimit) {
            val ptLoc = Location("").apply {
                latitude = routeGeoPoints[i].latitude
                longitude = routeGeoPoints[i].longitude
            }
            val d = userLoc.distanceTo(ptLoc)
            if (d < closestDist) {
                closestDist = d
                closestIndex = i
            }
        }

        if (closestIndex > routePointsClearedUpTo) routePointsClearedUpTo = closestIndex

        val remaining = mutableListOf<GeoPoint>()
        remaining.add(GeoPoint(snappedLoc.latitude, snappedLoc.longitude))
        val nextIndex = if (routePointsClearedUpTo + 1 < routeGeoPoints.size) routePointsClearedUpTo + 1 else routePointsClearedUpTo
        for (i in nextIndex until routeGeoPoints.size) remaining.add(routeGeoPoints[i])

        runOnUiThread {
            poly.setPoints(remaining)
            binding.miniMapView.invalidate()
        }
    }

    private fun updateHudArrow(cam: io.github.sceneview.node.CameraNode) {
        val hud = hudArrowNode ?: return
        val user = snappedLocation ?: run {
            hud.isVisible = false
            return
        }

        if (routeLocations.isEmpty()) {
            hud.isVisible = false
            return
        }

        val update = arrowController.update(
            userLocation = user,
            routePoints = routeLocations,
            magneticHeadingDeg = currentHeading,
            cameraWorldYDeg = lastCameraWorldYDeg
        )

        if (update.arrived && !hasArrived) {
            hasArrived = true
            hud.isVisible = false
            runOnUiThread { showArrivalDialog() }
            return
        }

        if (update.debugState == "awaiting_calibration") {
            hud.isVisible = false
            return
        }

        hud.isVisible = true

        // Place HUD arrow in front of camera (world space)
        val camPos = cam.worldPosition
        val yawRad = Math.toRadians(lastCameraWorldYDeg.toDouble())
        hud.worldPosition = Position(
            x = camPos.x + (sin(yawRad) * HUD_ARROW_FORWARD).toFloat(),
            y = camPos.y + HUD_ARROW_Y_OFFSET,
            z = camPos.z - (cos(yawRad) * HUD_ARROW_FORWARD).toFloat()
        )

        // Rotate HUD arrow using controller world-space output
        hud.worldRotation = Rotation(
            x = 0f,
            y = update.worldYDeg + ARROW_MODEL_FLIP_DEG,
            z = 0f
        )

        // Raw GPS only for off-route detection
        val raw = currentLocation
        val gap = if (raw != null) raw.distanceTo(user) else 0f

        val instruction: String
        val distanceText: String
        var directionCode = "straight"

        if (gap > CONNECTOR_HIDE_RADIUS_M) {
            instruction = "Head to the nearest path"
            distanceText = "in %.0f M".format(gap)
        } else {
            val nextIndex = update.targetNodeIndex.coerceIn(0, routeLocations.lastIndex)
            val nextPoint = routeLocations[nextIndex]
            distanceText = "%.0f M".format(user.distanceTo(nextPoint))

            // Stable turn direction from world-space delta (not raw compass bearing)
            var relativeAngle = update.worldYDeg - lastCameraWorldYDeg
            while (relativeAngle < -180f) relativeAngle += 360f
            while (relativeAngle > 180f) relativeAngle -= 360f

            instruction = when {
                relativeAngle > 20f -> {
                    directionCode = "right"
                    "Turn right"
                }
                relativeAngle < -20f -> {
                    directionCode = "left"
                    "Turn left"
                }
                else -> "Continue straight"
            }
        }

        updateNavigationService(instruction, distanceText, directionCode)
    }

    @SuppressLint("MissingPermission")
    private fun startSensors() {
        arCompassManager.setPathBearing(0f)
        arCompassManager.start { magneticNorth ->
            currentHeading = magneticNorth
            binding.miniMapView.mapOrientation = -magneticNorth
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateDistanceMeters(0f)
            .setMaxUpdateDelayMillis(2000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val newLoc = result.lastLocation ?: return

                val prev = lastValidLocation
                if (prev != null) {
                    val distMeters = prev.distanceTo(newLoc)
                    val timeSecs = (newLoc.time - prev.time) / 1000f
                    if (timeSecs > 0 && distMeters / timeSecs > MAX_WALKING_SPEED_MS) {
                        Log.w(TAG, "GPS jump rejected: ${(distMeters / timeSecs).toInt()} m/s")
                        return
                    }
                }

                lastValidLocation = newLoc
                currentLocation = newLoc // raw kept for blue dot/off-route only

                val newSnapped = if (routeLocations.isNotEmpty()) snapEngine.update(newLoc, routeLocations) else newLoc
                snappedLocation = newSnapped

                val gapM = newLoc.distanceTo(newSnapped)

                val destLat = intent.getDoubleExtra("TARGET_LAT", 0.0)
                val destLon = intent.getDoubleExtra("TARGET_LON", 0.0)
                val destLoc = if (destLat != 0.0 && destLon != 0.0) {
                    Location("dest").apply { latitude = destLat; longitude = destLon }
                } else null
                val distToDest = destLoc?.let { newLoc.distanceTo(it) } ?: Float.MAX_VALUE

                runOnUiThread {
                    val connector = connectorPolyline
                    val marker = markerPolyline

                    when {
                        distToDest <= DEST_NEAR_RADIUS_M && destLoc != null -> {
                            connector?.isVisible = false
                            routePolyline?.isVisible = false
                            if (marker != null) {
                                marker.setPoints(listOf(
                                    GeoPoint(newLoc.latitude, newLoc.longitude),
                                    GeoPoint(destLoc.latitude, destLoc.longitude)
                                ))
                                marker.isVisible = true
                            }
                        }
                        gapM > CONNECTOR_HIDE_RADIUS_M -> {
                            routePolyline?.isVisible = true
                            marker?.isVisible = false
                            if (connector != null) {
                                connector.setPoints(listOf(
                                    GeoPoint(newLoc.latitude, newLoc.longitude),
                                    GeoPoint(newSnapped.latitude, newSnapped.longitude)
                                ))
                                connector.isVisible = true
                            }
                        }
                        else -> {
                            routePolyline?.isVisible = true
                            connector?.isVisible = false
                            marker?.isVisible = false
                        }
                    }
                    binding.miniMapView.invalidate()
                }

                // blue dot and mini-map centering on raw GPS only
                binding.miniMapView.controller.setCenter(GeoPoint(newLoc.latitude, newLoc.longitude))
                trimPolylineToUser(newLoc, newSnapped)

                if (!isLocationFound) {
                    isLocationFound = true
                    checkLoadingStatus()
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
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

    private fun updateNavigationService(instruction: String, distance: String, directionCode: String) {
        if (instruction == lastSentInstruction && distance == lastSentDistance) return
        lastSentInstruction = instruction
        lastSentDistance = distance

        val intent = android.content.Intent(this, NavigationService::class.java).apply {
            action = NavigationService.ACTION_UPDATE
            putExtra(NavigationService.EXTRA_INSTRUCTION, instruction)
            putExtra(NavigationService.EXTRA_DISTANCE, distance)
            putExtra(NavigationService.EXTRA_DIRECTION, directionCode)
        }
        ContextCompat.startForegroundService(this, intent)
    }

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
            setResult(RESULT_OK, android.content.Intent().apply { putExtra("CANCEL_NAVIGATION", true) })
            finish()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}