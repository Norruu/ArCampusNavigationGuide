package com.campus.arnav.ui.map

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.transition.TransitionManager
import com.campus.arnav.R
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.Direction
import com.campus.arnav.data.model.Route
import com.campus.arnav.databinding.FragmentMapBinding
import com.campus.arnav.util.CompassManager
import com.campus.arnav.ui.ar.ARNavigationActivity
import com.campus.arnav.ui.map.components.CampusPathsOverlay
import com.campus.arnav.ui.map.components.CustomMarkerOverlay
import com.campus.arnav.ui.navigation.NavigationState
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
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
import android.content.res.ColorStateList

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
    private var showCampusPaths = true
    private var isSatelliteView = false
    private var isDarkMode = false

    private lateinit var compassManager: CompassManager
    private var isCompassMode = false

    // Bottom Sheet Behavior
    private lateinit var previewSheetBehavior: BottomSheetBehavior<View>

    // Drag Variables for Top Panel
    private var startY = 0f
    private var startHeight = 0
    private var maxPanelHeight = 0

    // --- BOTTOM SHEET CALLBACK ---
    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            when (newState) {
                BottomSheetBehavior.STATE_COLLAPSED -> {
                    setMiniViewVisible(true)
                }
                BottomSheetBehavior.STATE_EXPANDED -> {
                    setMiniViewVisible(false)
                }
                BottomSheetBehavior.STATE_HIDDEN -> {
                    clearRoute()
                }
                else -> { }
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            // Optional: Add fading effects
        }
    }

    private fun setMiniViewVisible(isMini: Boolean) {
        binding.navigationPanel.apply {
            // Swap Text: Description (Max) vs Route Info (Mini)
            tvDestinationDescription.isVisible = !isMini
            tvRouteInfoMini.isVisible = isMini

            // Toggle Mini Start Button
            btnMiniStart.isVisible = isMini
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configureOSMDroid()
        campusPathsOverlay = CampusPathsOverlay()
        compassManager = CompassManager(requireContext())

        setupMap()
        setupCampusMap(mapView)

        if (showCampusPaths) {
            campusPathsOverlay.addPathsToMap(mapView)
        }

        if (!mapView.overlays.contains(routePolyline)) {
            mapView.overlays.add(routePolyline)
        }

        setupLocationOverlay()
        setupUI()
        observeViewModel()
    }

    private fun configureOSMDroid() {
        Configuration.getInstance().apply {
            load(requireContext(), androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext()))
            userAgentValue = requireContext().packageName
        }
    }

    private fun setupMap() {
        mapView = binding.mapView
        val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        isDarkMode = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES

        mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(17.0)
            controller.setCenter(GeoPoint(9.8515, 122.8867))
            minZoomLevel = 14.0
            maxZoomLevel = 20.0
            isTilesScaledToDpi = true

            if (isDarkMode) {
                val inverseMatrix = floatArrayOf(
                    -1.0f, 0.0f, 0.0f, 0.0f, 255f,
                    0.0f, -1.0f, 0.0f, 0.0f, 255f,
                    0.0f, 0.0f, -1.0f, 0.0f, 255f,
                    0.0f, 0.0f, 0.0f, 1.0f, 0.0f
                )
                overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(inverseMatrix))
            }
        }

        routePolyline = Polyline(mapView)
        routePolyline.outlinePaint.apply {
            color = ContextCompat.getColor(requireContext(), R.color.white)
            strokeWidth = 20f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
    }

    fun setupCampusMap(mapView: MapView) {
        val campusNorth = 9.857365352521331
        val campusSouth = 9.843818207123961
        val campusEast = 122.89307299341952
        val campusWest = 122.88305672555643
        val boundingBox = BoundingBox(campusNorth, campusEast, campusSouth, campusWest)
        mapView.setScrollableAreaLimitDouble(boundingBox)
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

    private fun setupUI() {
        // --- 1. Bottom Sheet Setup ---
        val bottomSheet = binding.navigationPanel.root
        previewSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        val density = resources.displayMetrics.density
        previewSheetBehavior.peekHeight = (140 * density).toInt()

        previewSheetBehavior.skipCollapsed = false
        previewSheetBehavior.isHideable = true

        previewSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        previewSheetBehavior.addBottomSheetCallback(bottomSheetCallback)

        // --- 2. Active Panel (Top) Drag Logic ---
        binding.activeNavigationPanel.dragHandleContainer.setOnTouchListener { view, event ->
            val container = binding.activeNavigationPanel.infoContainer
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
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
                    animator.addUpdateListener { animation ->
                        val value = animation.animatedValue as Int
                        container.layoutParams.height = value
                        container.requestLayout()
                    }
                    animator.start()
                    if (!shouldOpen) {
                        view.postDelayed({
                            if (container.layoutParams.height == 0) container.visibility = View.GONE
                        }, 250)
                    } else {
                        view.postDelayed({ container.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT }, 250)
                    }
                    view.performClick()
                    true
                }
                else -> false
            }
        }

        // --- 3. FABs ---
        binding.fabRecenter.setOnClickListener {
            myLocationOverlay?.myLocation?.let { location ->
                mapView.controller.animateTo(location)
            }
            viewModel.setFollowingUser(true)
        }
        binding.fabCompass.setOnClickListener { toggleCompassMode() }
        binding.fabArMode.setOnClickListener { viewModel.switchToARMode() }

        // --- 4. Buttons ---
        binding.navigationPanel.btnMiniStart.setOnClickListener { viewModel.startNavigation() }
        binding.navigationPanel.btnStartNavigation.setOnClickListener { viewModel.startNavigation() }
        binding.navigationPanel.btnClosePanel.setOnClickListener {
            viewModel.stopNavigation()
            previewSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            clearRoute()
        }
        binding.activeNavigationPanel.btnEndNavigation.setOnClickListener {
            viewModel.stopNavigation()
            binding.activeNavigationPanel.root.visibility = View.GONE
            clearRoute()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.buildings.collectLatest { buildings -> updateBuildingMarkers(buildings) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.activeRoute.collectLatest { route ->
                if (route != null) displayRoute(route) else clearRoute()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.navigationState.collectLatest { state -> handleNavigationState(state) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiEvent.collectLatest { event -> handleUiEvent(event) }
        }
    }

    private fun displayRoute(route: Route) {
        val currentState = viewModel.navigationState.value

        if (currentState is NavigationState.Navigating) {
            val points = route.waypoints.map {
                GeoPoint(it.location.latitude, it.location.longitude)
            }
            routePolyline.setPoints(points)
            binding.fabArMode.visibility = View.VISIBLE
        } else {
            routePolyline.setPoints(emptyList())
            binding.fabArMode.visibility = View.GONE
        }

        val dist = formatDistance(route.totalDistance)
        val time = formatTime(route.estimatedTime)

        binding.navigationPanel.tvRouteInfoMini.text = "$dist • $time"
        binding.navigationPanel.tvDistance.text = dist
        binding.navigationPanel.tvEta.text = time
    }

    private fun clearRoute() {
        routePolyline.setPoints(emptyList())
        binding.fabArMode.visibility = View.GONE
    }

    private fun handleNavigationState(state: NavigationState) {
        when (state) {
            is NavigationState.Idle -> {
                previewSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                binding.activeNavigationPanel.root.visibility = View.GONE
                clearRoute()
            }
            is NavigationState.Previewing -> {
                if (previewSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                    previewSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                }

                binding.navigationPanel.tvDestinationName.text = state.destination.name
                binding.navigationPanel.tvDestinationDescription.text = state.destination.description ?: ""

                routePolyline.setPoints(emptyList())

                binding.activeNavigationPanel.root.visibility = View.GONE
                setMiniViewVisible(false)
            }
            is NavigationState.Navigating -> {
                previewSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                binding.activeNavigationPanel.root.visibility = View.VISIBLE
                binding.activeNavigationPanel.infoContainer.visibility = View.VISIBLE
                binding.activeNavigationPanel.infoContainer.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT

                val points = state.route.waypoints.map {
                    GeoPoint(it.location.latitude, it.location.longitude)
                }
                routePolyline.setPoints(points)
                updateActiveNavigation(state)
            }
            is NavigationState.Arrived -> {
                previewSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                binding.activeNavigationPanel.root.visibility = View.GONE
                Snackbar.make(binding.root, "You have arrived!", Snackbar.LENGTH_LONG).show()
                clearRoute()
            }
        }
    }

    private fun updateActiveNavigation(state: NavigationState.Navigating) {
        binding.activeNavigationPanel.tvCurrentInstruction.text = state.currentStep.instruction
        binding.activeNavigationPanel.tvDistanceToNext.text = "in ${formatDistance(state.distanceToNextWaypoint)}"
        binding.activeNavigationPanel.tvRemainingDistance.text = formatDistance(state.remainingDistance)
        binding.activeNavigationPanel.tvRemainingTime.text = formatTime(state.remainingTime)
        binding.activeNavigationPanel.progressRoute.progress = if (state.route.totalDistance > 0) {
            ((state.route.totalDistance - state.remainingDistance) / state.route.totalDistance * 100).toInt()
        } else 0
        binding.activeNavigationPanel.ivDirectionIcon.setImageResource(getDirectionIcon(state.currentStep.direction))
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

    private fun handleUiEvent(event: MapUiEvent) {
        when (event) {
            is MapUiEvent.LaunchARNavigation -> {
                val intent = Intent(requireContext(), ARNavigationActivity::class.java)
                intent.putExtra(ARNavigationActivity.EXTRA_ROUTE, event.route)
                startActivity(intent)
            }
            is MapUiEvent.ShowError -> Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
            is MapUiEvent.OffRoute -> Snackbar.make(binding.root, "You are off route", Snackbar.LENGTH_SHORT).show()
            is MapUiEvent.ShowArrivalDialog -> {
                Snackbar.make(binding.root, "You have arrived!", Snackbar.LENGTH_LONG).show()
                viewModel.stopNavigation()
            }
            else -> {}
        }
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

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun toggleMapLayer() {
        isSatelliteView = !isSatelliteView
        if (isSatelliteView) {
            try {
                mapView.overlayManager.tilesOverlay.setColorFilter(null)
                val googleSatellite = object : OnlineTileSourceBase("Google-Satellite", 0, 20, 256, ".png", arrayOf("https://mt0.google.com/vt/lyrs=s&hl=en&x=")) {
                    override fun getTileURLString(pMapTileIndex: Long): String = "${baseUrl}${MapTileIndex.getX(pMapTileIndex)}&y=${MapTileIndex.getY(pMapTileIndex)}&z=${MapTileIndex.getZoom(pMapTileIndex)}"
                }
                mapView.setTileSource(googleSatellite)
                Toast.makeText(requireContext(), "Satellite View", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { isSatelliteView = false }
        } else {
            mapView.setTileSource(TileSourceFactory.MAPNIK)
            if (isDarkMode) {
                val inverseMatrix = floatArrayOf(-1.0f, 0.0f, 0.0f, 0.0f, 255f, 0.0f, -1.0f, 0.0f, 0.0f, 255f, 0.0f, 0.0f, -1.0f, 0.0f, 255f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f)
                mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(inverseMatrix))
            }
            Toast.makeText(requireContext(), "Default View", Toast.LENGTH_SHORT).show()
        }
        mapView.invalidate()
    }

    private fun toggleCompassMode() {
        isCompassMode = !isCompassMode

        if (isCompassMode) {
            // --- ACTIVE STATE (LIGHT UP BLUE) ---
            // 1. Change Icon Color to Blue using imageTintList
            binding.fabCompass.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.map_blue)
            )

            // 2. Change the icon to the filled version
            binding.fabCompass.setImageResource(R.drawable.ic_compass)

            Toast.makeText(requireContext(), "Compass Mode: On", Toast.LENGTH_SHORT).show()

            compassManager.start { mapOrientation, isFlat ->
                if (isFlat) {
                    mapView.mapOrientation = mapOrientation
                }
            }
            viewModel.setFollowingUser(true)
            myLocationOverlay?.enableFollowLocation()

        } else {
            stopCompassMode()
            Toast.makeText(requireContext(), "Compass Mode: Off", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopCompassMode() {
        isCompassMode = false
        compassManager.stop()

        // --- RESET STATE ---
        // Reset to the default theme color (OnSurface)
        binding.fabCompass.imageTintList = ColorStateList.valueOf(
            com.google.android.material.color.MaterialColors.getColor(binding.fabCompass, com.google.android.material.R.attr.colorOnSurface)
        )

        // Animate Map back to North (0 degrees)
        val currentOrientation = mapView.mapOrientation
        val animator = android.animation.ValueAnimator.ofFloat(currentOrientation, 0f)
        animator.duration = 500
        animator.addUpdateListener {
            mapView.mapOrientation = it.animatedValue as Float
        }
        animator.start()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        if (myLocationOverlay == null && hasLocationPermission()) {
            setupLocationOverlay()
        }
        myLocationOverlay?.enableMyLocation()

        // RE-START COMPASS ON RESUME
        if (isCompassMode) {
            compassManager.start { mapOrientation, isFlat ->
                if (isFlat) mapView.mapOrientation = mapOrientation
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        myLocationOverlay?.disableMyLocation()
        compassManager.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::previewSheetBehavior.isInitialized) {
            previewSheetBehavior.removeBottomSheetCallback(bottomSheetCallback)
        }
        campusPathsOverlay.clearPaths(mapView)
        mapView.onDetach()
        _binding = null
    }
}