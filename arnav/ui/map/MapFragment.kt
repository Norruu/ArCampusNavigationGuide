package com.campus.arnav.ui.map

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.campus.arnav.R
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.BuildingType
import com.campus.arnav.data.model.Direction
import com.campus.arnav.data.model.Route
import com.campus.arnav.databinding.FragmentMapBinding
import com.campus.arnav.ui.MainActivity
import com.campus.arnav.ui.ar.ARNavigationActivity
import com.campus.arnav.ui.map.components.CampusPathsOverlay
import com.campus.arnav.ui.map.components.CustomMarkerOverlay
import com.campus.arnav.ui.map.components.DestinationConnectorOverlay
import com.campus.arnav.ui.map.components.OffRoutePolyline
import com.campus.arnav.ui.navigation.NavigationState
import com.campus.arnav.util.MapCompassManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@AndroidEntryPoint
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MapViewModel by viewModels()

    private lateinit var mapView: MapView
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private lateinit var routePolyline: Polyline

    private val buildingMarkers = mutableListOf<CustomMarkerOverlay>()
    private lateinit var campusPathsOverlay: CampusPathsOverlay

    private var isDarkMode = false
    private lateinit var mapCompassManager: MapCompassManager

    // Drag Variables
    private var startY = 0f
    private var startHeight = 0
    private var maxPanelHeight = 0

    // Polyline overlays
    private val offRoutePolyline = OffRoutePolyline()
    private val destinationConnectorOverlay = DestinationConnectorOverlay()

    // Geofencing Variables
    private lateinit var geofencingClient: GeofencingClient
    private val GEOFENCE_RADIUS_IN_METERS = 20f

    private val mainActivity get() = activity as? MainActivity

    // Listens for the AR screen to close and checks if we need to cancel navigation
    private val arNavigationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val shouldCancel = result.data?.getBooleanExtra("CANCEL_NAVIGATION", false) == true
            if (shouldCancel) {
                viewModel.stopNavigation()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // MOVED HERE: Correct place to initialize the Geofencing Client
        geofencingClient = LocationServices.getGeofencingClient(requireActivity())

        configureOSMDroid()
        campusPathsOverlay = CampusPathsOverlay()
        mapCompassManager = MapCompassManager(requireContext())

        setupMap()
        setupCampusMap(mapView)

        if (!mapView.overlays.contains(routePolyline)) {
            mapView.overlays.add(routePolyline)
        }
        mapView.overlays.add(offRoutePolyline)
        mapView.overlays.add(destinationConnectorOverlay)

        setupLocationOverlay()
        setupUI()
        observeViewModel()

        setFragmentResultListener("search_request") { _, bundle ->
            val buildingId = bundle.getString("building_id")
            if (buildingId != null) viewModel.selectBuildingById(buildingId)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupUI() {
        setupCategories()

        binding.navigationPanel.root.visibility = View.GONE
        setupActivePanelDrag()

        binding.fabRecenter.setOnClickListener {
            myLocationOverlay?.myLocation?.let { location ->
                mapView.controller.animateTo(location)
            }
            viewModel.setFollowingUser(true)
        }

        binding.fabCompass.setOnClickListener { toggleCompassMode() }
        binding.fabArMode.setOnClickListener { viewModel.switchToARMode() }

        binding.navigationPanel.btnStartNavigation.setOnClickListener { viewModel.startNavigation() }
        binding.navigationPanel.btnClosePanel.setOnClickListener { viewModel.stopNavigation() }
        binding.activeNavigationPanel.btnEndNavigation.setOnClickListener { viewModel.stopNavigation() }
    }

    private fun setupCategories() {
        val chipGroup = binding.categoryChipGroup
        chipGroup.removeAllViews()

        val bgStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val bgColors = intArrayOf(
            ContextCompat.getColor(requireContext(), R.color.map_blue),
            Color.parseColor("#F0F0F0")
        )
        val chipBackgroundColor = ColorStateList(bgStates, bgColors)
        val contentColors = intArrayOf(Color.WHITE, Color.BLACK)
        val contentColorStateList = ColorStateList(bgStates, contentColors)

        fun applyStyle(chip: com.google.android.material.chip.Chip) {
            chip.isCheckable = true
            chip.isCheckedIconVisible = false
            chip.chipBackgroundColor = chipBackgroundColor
            chip.setTextColor(contentColorStateList)
            chip.chipIconTint = contentColorStateList
        }

        val allChip = com.google.android.material.chip.Chip(
            requireContext(), null, com.google.android.material.R.style.Widget_Material3_Chip_Filter
        )
        allChip.text = "All"
        applyStyle(allChip)
        allChip.isChecked = true
        allChip.setOnClickListener { viewModel.filterBuildingsByCategory(null) }
        chipGroup.addView(allChip)

        BuildingType.values().forEach { type ->
            val chip = com.google.android.material.chip.Chip(
                requireContext(), null, com.google.android.material.R.style.Widget_Material3_Chip_Filter
            )
            chip.text = type.name.lowercase().replaceFirstChar { it.uppercase() }
            chip.chipIcon = ContextCompat.getDrawable(requireContext(), getIconForBuildingType(type))
            chip.isChipIconVisible = true
            applyStyle(chip)
            chip.setOnClickListener { viewModel.filterBuildingsByCategory(type) }
            chipGroup.addView(chip)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupActivePanelDrag() {
        binding.activeNavigationPanel.dragHandleContainer.setOnTouchListener { view, event ->
            val container = binding.activeNavigationPanel.infoContainer
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (maxPanelHeight == 0) {
                        val widthSpec = View.MeasureSpec.makeMeasureSpec(binding.activeNavigationPanel.root.width, View.MeasureSpec.EXACTLY)
                        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                        container.measure(widthSpec, heightSpec)
                        maxPanelHeight = container.measuredHeight
                    }
                    if (!container.isVisible) {
                        container.layoutParams.height = 0
                        container.visibility = View.VISIBLE
                    }
                    startHeight = container.layoutParams.height
                    startY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startY
                    var newHeight = (startHeight + dy).toInt()
                    if (newHeight < 0) newHeight = 0
                    if (newHeight > maxPanelHeight) newHeight = maxPanelHeight
                    container.layoutParams.height = newHeight
                    container.requestLayout()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val currentHeight = container.layoutParams.height
                    val shouldOpen = currentHeight > (maxPanelHeight / 2)
                    val targetHeight = if (shouldOpen) maxPanelHeight else 0
                    val animator = ValueAnimator.ofInt(currentHeight, targetHeight)
                    animator.duration = 250
                    animator.interpolator = DecelerateInterpolator()
                    animator.addUpdateListener {
                        container.layoutParams.height = it.animatedValue as Int
                        container.requestLayout()
                    }
                    animator.start()
                    if (!shouldOpen) {
                        view.postDelayed({ if (container.layoutParams.height == 0) container.visibility = View.GONE }, 250)
                    } else {
                        view.postDelayed({ container.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT }, 250)
                    }
                    view.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch { viewModel.buildings.collectLatest { updateBuildingMarkers(it) } }
        viewLifecycleOwner.lifecycleScope.launch { viewModel.activeRoute.collectLatest { route -> if (route != null) displayRoute(route) else clearRoute() } }
        viewLifecycleOwner.lifecycleScope.launch { viewModel.navigationState.collectLatest { handleNavigationState(it) } }
        viewLifecycleOwner.lifecycleScope.launch { viewModel.uiEvent.collectLatest { handleUiEvent(it) } }
        viewLifecycleOwner.lifecycleScope.launch { viewModel.isSatelliteView.collectLatest { isSat -> if (isSat) enableSatellite() else disableSatellite() } }
        viewLifecycleOwner.lifecycleScope.launch { viewModel.isCompassMode.collectLatest { isCompass -> if (isCompass) startCompassMode() else stopCompassMode() } }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.userLocation.collectLatest { location ->
                val routePts = viewModel.routePoints.value
                val user = location?.let { GeoPoint(it.latitude, it.longitude) }
                offRoutePolyline.update(user, routePts)
                mapView.invalidate()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.routePoints.collectLatest { routePts ->
                val location = viewModel.userLocation.value
                val user = location?.let { GeoPoint(it.latitude, it.longitude) }
                offRoutePolyline.update(user, routePts)
                if (routePts != null) routePolyline.setPoints(routePts)
                mapView.invalidate()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.destinationConnector.collectLatest { connector ->
                if (connector != null) destinationConnectorOverlay.set(connector.first, connector.second)
                else destinationConnectorOverlay.clear()
                mapView.invalidate()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                if (isLoading) {
                    binding.loadingOverlay.apply {
                        alpha = 0f
                        visibility = View.VISIBLE
                        animate().alpha(1f).setDuration(200).start()
                    }
                } else {
                    binding.loadingOverlay.animate().alpha(0f).setDuration(200).withEndAction {
                        binding.loadingOverlay.visibility = View.GONE
                    }.start()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun handleNavigationState(state: NavigationState) {
        when (state) {
            is NavigationState.Idle -> {
                binding.navigationPanel.root.visibility = View.GONE
                binding.activeNavigationPanel.root.visibility = View.GONE
                binding.categoryContainer.visibility = View.VISIBLE
                binding.fabArMode.visibility = View.GONE
                mainActivity?.setBottomNavVisibility(true)
                clearRoute()
                mainActivity?.isCurrentlyNavigating = false
                if (state !is NavigationState.Previewing) {
                    sendCommandToService(com.campus.arnav.service.NavigationService.ACTION_STOP)
                }
            }
            is NavigationState.Previewing -> {
                binding.navigationPanel.root.visibility = View.VISIBLE
                binding.activeNavigationPanel.root.visibility = View.GONE
                binding.categoryContainer.visibility = View.VISIBLE
                binding.fabArMode.visibility = View.GONE
                mainActivity?.setBottomNavVisibility(true)
                mainActivity?.isCurrentlyNavigating = false

                binding.navigationPanel.tvDestinationName.text = state.destination.name
                binding.navigationPanel.tvDestinationDescription.text = state.destination.description ?: ""
                binding.navigationPanel.ivDestinationIcon.setImageResource(getIconForBuildingType(state.destination.type))

                state.route?.let { route -> updateRouteInfo(route) } ?: run {
                    binding.navigationPanel.tvDistance.text = "..."
                    binding.navigationPanel.tvEta.text = "..."
                }
                clearRoute()
            }
            is NavigationState.Navigating -> {
                binding.navigationPanel.root.visibility = View.GONE
                binding.activeNavigationPanel.root.visibility = View.VISIBLE
                binding.categoryContainer.visibility = View.GONE
                binding.fabArMode.visibility = View.VISIBLE
                mainActivity?.setBottomNavVisibility(false)
                binding.activeNavigationPanel.infoContainer.visibility = View.VISIBLE
                displayRoute(state.route)
                updateActiveNavigation(state)
                mainActivity?.isCurrentlyNavigating = true

                val intent = Intent(requireContext(), com.campus.arnav.service.NavigationService::class.java).apply {
                    action = com.campus.arnav.service.NavigationService.ACTION_UPDATE
                    putExtra(com.campus.arnav.service.NavigationService.EXTRA_INSTRUCTION, state.currentStep.instruction)
                    putExtra(com.campus.arnav.service.NavigationService.EXTRA_DISTANCE, "in ${formatDistance(state.distanceToNextWaypoint)}")
                }
                ContextCompat.startForegroundService(requireContext(), intent)
            }
            is NavigationState.Arrived -> {
                binding.navigationPanel.root.visibility = View.GONE
                binding.activeNavigationPanel.root.visibility = View.GONE
                binding.categoryContainer.visibility = View.GONE
                binding.fabArMode.visibility = View.GONE
                mainActivity?.setBottomNavVisibility(true)
                clearRoute()
                mainActivity?.isCurrentlyNavigating = false
                sendCommandToService(com.campus.arnav.service.NavigationService.ACTION_STOP)
            }
        }
    }

    private fun sendCommandToService(actionStr: String) {
        val intent = Intent(requireContext(), com.campus.arnav.service.NavigationService::class.java)

        if (actionStr == com.campus.arnav.service.NavigationService.ACTION_STOP) {
            requireContext().stopService(intent)
        } else {
            intent.action = actionStr
            ContextCompat.startForegroundService(requireContext(), intent)
        }
    }

    private fun handleUiEvent(event: MapUiEvent) {
        when (event) {
            is MapUiEvent.LaunchARNavigation -> {
                val intent = Intent(requireContext(), ARNavigationActivity::class.java).apply {
                    val dest = event.route.waypoints.last().location

                    putExtra("TARGET_LAT", dest.latitude)
                    putExtra("TARGET_LON", dest.longitude)
                    putExtra("TARGET_NAME", event.destinationName)

                    val points = viewModel.routePoints.value ?: event.route.waypoints.map {
                        GeoPoint(it.location.latitude, it.location.longitude)
                    }
                    val lats = points.map { it.latitude }.toDoubleArray()
                    val lons = points.map { it.longitude }.toDoubleArray()

                    putExtra("ROUTE_LATS", lats)
                    putExtra("ROUTE_LONS", lons)
                    putExtra("MAP_TYPE", binding.mapView.tileProvider.tileSource.name())
                }

                // Set up the Geofence for the destination!
                val destNode = event.route.waypoints.last().location
                addDestinationGeofence(destNode.latitude, destNode.longitude, event.destinationName)

                arNavigationLauncher.launch(intent)
            }
            is MapUiEvent.MoveCameraTo -> {
                mapView.controller.animateTo(event.location, 18.5, 1500L)
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(3500)
                    myLocationOverlay?.myLocation?.let { myLoc ->
                        mapView.controller.animateTo(myLoc, 18.5, 1500L)
                        viewModel.setFollowingUser(true)
                    }
                }
            }
            is MapUiEvent.ShowError -> Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
            is MapUiEvent.OffRoute -> Snackbar.make(binding.root, "You are off route", Snackbar.LENGTH_SHORT).show()
            is MapUiEvent.ShowArrivalDialog -> showArrivalDialog(event.buildingName)
            else -> {}
        }
    }

    // --- GEOFENCING LOGIC ---
    @SuppressLint("MissingPermission")
    private fun addDestinationGeofence(targetLat: Double, targetLon: Double, targetName: String) {
        if (!hasLocationPermission()) return

        val geofence = Geofence.Builder()
            .setRequestId(targetName)
            .setCircularRegion(targetLat, targetLon, GEOFENCE_RADIUS_IN_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val intent = Intent(requireContext(), com.campus.arnav.receiver.GeofenceBroadcastReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            requireContext(),
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
        )

        geofencingClient.addGeofences(geofencingRequest, pendingIntent)?.run {
            addOnSuccessListener {
                android.util.Log.d("GEOFENCE", "Successfully added geofence for $targetName!")
            }
            addOnFailureListener {
                android.util.Log.e("GEOFENCE", "Failed to add geofence: ${it.message}")
            }
        }
    }

    private fun showArrivalDialog(buildingName: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_arrival, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<TextView>(R.id.tvArrivalMessage).text = "You have successfully reached $buildingName."
        dialogView.findViewById<Button>(R.id.btnFinishNavigation).setOnClickListener {
            dialog.dismiss()
            viewModel.stopNavigation()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun configureOSMDroid() {
        Configuration.getInstance().load(requireContext(), androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext()))
        Configuration.getInstance().userAgentValue = requireContext().packageName
    }

    private fun setupMap() {
        mapView = binding.mapView
        isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(17.0)
            controller.setCenter(GeoPoint(9.8515, 122.8867))
            minZoomLevel = 14.0
            maxZoomLevel = 20.0
            isTilesScaledToDpi = true
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            if (isDarkMode) applyDarkModeFilter()
        }

        routePolyline = Polyline(mapView).apply {
            outlinePaint.color = ContextCompat.getColor(requireContext(), R.color.route_blue)
            outlinePaint.strokeWidth = 20f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            outlinePaint.isAntiAlias = true
        }
    }

    private fun applyDarkModeFilter() {
        val inverseMatrix = floatArrayOf(
            -1.0f, 0.0f, 0.0f, 0.0f, 255f,
            0.0f, -1.0f, 0.0f, 0.0f, 255f,
            0.0f, 0.0f, -1.0f, 0.0f, 255f,
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        )
        mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(inverseMatrix))
    }

    fun setupCampusMap(mapView: MapView) {
        val campusNorth = 9.857365352521331
        val campusSouth = 9.843818207123961
        val campusEast = 122.89307299341952
        val campusWest = 122.88305672555643
        mapView.setScrollableAreaLimitDouble(BoundingBox(campusNorth, campusEast, campusSouth, campusWest))
        mapView.setMinZoomLevel(14.0)
        mapView.setMaxZoomLevel(20.0)
        mapView.setHorizontalMapRepetitionEnabled(false)
        mapView.setVerticalMapRepetitionEnabled(false)
    }

    private fun setupLocationOverlay() {
        if (hasLocationPermission()) {
            myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), mapView).apply {
                enableMyLocation()
                enableFollowLocation()
            }
            mapView.overlays.add(myLocationOverlay)
        }
    }

    private fun updateRouteInfo(route: Route) {
        val dist = formatDistance(route.totalDistance)
        val time = formatTime(route.estimatedTime)
        binding.navigationPanel.tvDistance.text = dist
        binding.navigationPanel.tvEta.text = time
        binding.navigationPanel.tvRouteInfoMini.text = "$dist • $time"
    }

    private fun displayRoute(route: Route) {
        val allPoints = viewModel.routePoints.value ?: route.waypoints.map { GeoPoint(it.location.latitude, it.location.longitude) }
        routePolyline.setPoints(allPoints)
        mapView.invalidate()
    }

    private fun clearRoute() {
        routePolyline.setPoints(emptyList())
        offRoutePolyline.clear()
        destinationConnectorOverlay.clear()
        mapView.invalidate()
    }

    private fun updateActiveNavigation(state: NavigationState.Navigating) {
        val step = state.currentStep
        binding.activeNavigationPanel.tvCurrentInstruction.text = step.instruction
        binding.activeNavigationPanel.tvDistanceToNext.text = "in ${formatDistance(state.distanceToNextWaypoint)}"
        binding.activeNavigationPanel.tvRemainingDistance.text = formatDistance(state.remainingDistance)
        binding.activeNavigationPanel.tvRemainingTime.text = formatTime(state.remainingTime)
        binding.activeNavigationPanel.ivDirectionIcon.setImageResource(getDirectionIcon(step.direction))
    }

    private fun updateBuildingMarkers(buildings: List<Building>) {
        buildingMarkers.forEach { mapView.overlays.remove(it) }
        buildingMarkers.clear()
        buildings.forEach { building ->
            val marker = CustomMarkerOverlay(mapView, building) { viewModel.onBuildingSelected(it) }
            buildingMarkers.add(marker)
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }

    private fun getDirectionIcon(direction: Direction): Int {
        return when (direction) {
            Direction.FORWARD -> R.drawable.ic_arrow_up
            Direction.LEFT -> R.drawable.ic_turn_left
            Direction.SLIGHT_LEFT -> R.drawable.ic_turn_slight_left
            Direction.SHARP_LEFT -> R.drawable.ic_turn_sharp_left
            Direction.RIGHT -> R.drawable.ic_turn_right
            Direction.SLIGHT_RIGHT -> R.drawable.ic_turn_slight_right
            Direction.SHARP_RIGHT -> R.drawable.ic_turn_sharp_right
            Direction.U_TURN -> R.drawable.ic_u_turn
            Direction.ARRIVE -> R.drawable.ic_destination
        }
    }

    private fun getIconForBuildingType(type: BuildingType): Int {
        return when (type) {
            BuildingType.ACADEMIC -> R.drawable.ic_school
            BuildingType.LIBRARY -> R.drawable.ic_library
            BuildingType.CAFETERIA -> R.drawable.ic_restaurant
            BuildingType.SPORTS -> R.drawable.ic_sports
            BuildingType.ADMINISTRATIVE -> R.drawable.ic_business
            BuildingType.LANDMARK -> R.drawable.ic_landmark
        }
    }

    private fun formatDistance(meters: Double): String {
        return if (meters < 1000) "${meters.toInt()} M" else String.format("%.1f KM", meters / 1000)
    }

    private fun formatTime(seconds: Long): String {
        val minutes = seconds / 60
        return when {
            minutes < 1 -> "< 1 Min"
            minutes < 60 -> "$minutes Mins"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun toggleMapLayer() { viewModel.setSatelliteView(!(viewModel.isSatelliteView.value)) }

    private fun toggleCompassMode() { viewModel.setCompassMode(!(viewModel.isCompassMode.value)) }

    private fun enableSatellite() {
        try {
            mapView.overlayManager.tilesOverlay.setColorFilter(null)
            val googleSatellite = object : OnlineTileSourceBase(
                "Google-Satellite", 0, 20, 256, ".png", arrayOf("https://mt0.google.com/vt/lyrs=s&hl=en&x=")
            ) {
                override fun getTileURLString(pMapTileIndex: Long): String =
                    "${baseUrl}${MapTileIndex.getX(pMapTileIndex)}&y=${MapTileIndex.getY(pMapTileIndex)}&z=${MapTileIndex.getZoom(pMapTileIndex)}"
            }
            mapView.setTileSource(googleSatellite)
            campusPathsOverlay.addPathsToMap(mapView)

            // Reorder overlays
            mapView.overlays.remove(routePolyline)
            mapView.overlays.add(routePolyline)
            myLocationOverlay?.let { mapView.overlays.remove(it); mapView.overlays.add(it) }
            buildingMarkers.forEach { marker -> mapView.overlays.remove(marker); mapView.overlays.add(marker) }

            Toast.makeText(requireContext(), "Satellite View", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            viewModel.setSatelliteView(false)
        }
        mapView.invalidate()
        mapView.overlays.remove(offRoutePolyline)
        mapView.overlays.add(offRoutePolyline)
        mapView.overlays.remove(destinationConnectorOverlay)
        mapView.overlays.add(destinationConnectorOverlay)
    }

    private fun disableSatellite() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        campusPathsOverlay.clearPaths(mapView)
        if (isDarkMode) applyDarkModeFilter() else mapView.overlayManager.tilesOverlay.setColorFilter(null)
    }

    private fun startCompassMode() {
        binding.fabCompass.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.map_blue))
        binding.fabCompass.setImageResource(R.drawable.ic_compass)
        Toast.makeText(requireContext(), "Compass Mode: On", Toast.LENGTH_SHORT).show()

        mapCompassManager.start { mapRotation -> mapView.mapOrientation = mapRotation }
        viewModel.setFollowingUser(true)
        myLocationOverlay?.enableFollowLocation()
    }

    private fun stopCompassMode() {
        mapCompassManager.stop()
        binding.fabCompass.imageTintList = ColorStateList.valueOf(
            com.google.android.material.color.MaterialColors.getColor(binding.fabCompass, com.google.android.material.R.attr.colorOnSurface)
        )
        val currentOrientation = mapView.mapOrientation
        if (currentOrientation != 0f) {
            val animator = ValueAnimator.ofFloat(currentOrientation, 0f)
            animator.duration = 500
            animator.addUpdateListener { mapView.mapOrientation = it.animatedValue as Float }
            animator.start()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        if (myLocationOverlay == null && hasLocationPermission()) setupLocationOverlay()
        myLocationOverlay?.enableMyLocation()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        myLocationOverlay?.disableMyLocation()
        mapCompassManager.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        campusPathsOverlay.clearPaths(mapView)
        mapView.onDetach()
        _binding = null
    }
}