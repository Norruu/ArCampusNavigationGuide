package com.campus.arnav.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.campus.arnav.R
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.Direction
import com.campus.arnav.data.model.Route
import com.campus.arnav.databinding.FragmentMapBinding
import com.campus.arnav.ui.ar.ARNavigationActivity
import com.campus.arnav.ui.map.components.CustomMarkerOverlay
import com.campus.arnav.ui.map.components.SmoothRouteOverlay
import com.campus.arnav.ui.map.components.CampusPathsOverlay
import com.campus.arnav.ui.navigation.NavigationState
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@AndroidEntryPoint
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MapViewModel by viewModels()

    private lateinit var mapView: MapView
    private var myLocationOverlay: MyLocationNewOverlay? = null

    // Smooth Painter initialized once
    private lateinit var routeOverlay: SmoothRouteOverlay

    private val buildingMarkers = mutableListOf<CustomMarkerOverlay>()
    private lateinit var campusPathsOverlay: CampusPathsOverlay
    private var showCampusPaths = true
    private var isSatelliteView = false

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

        // 1. Configure OSMDroid Engine
        configureOSMDroid()

        // 2. Initialize Overlay Helpers
        campusPathsOverlay = CampusPathsOverlay()

        // 3. Setup Map & UI
        setupMap()
        setupCampusMap(mapView)
        setupCampusPaths()
        setupUI()

        // 4. Start Observing Data
        observeViewModel()
    }

    private fun configureOSMDroid() {
        Configuration.getInstance().apply {
            load(requireContext(), androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext()))
            userAgentValue = requireContext().packageName
            tileDownloadThreads = 8.toShort()
            tileFileSystemThreads = 4.toShort()
        }
    }

    private fun setupMap() {
        mapView = binding.mapView

        mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(17.0)
            controller.setCenter(GeoPoint(9.8515, 122.8867))
            minZoomLevel = 14.0
            maxZoomLevel = 20.0
            isTilesScaledToDpi = true
        }

        setupLocationOverlay()

        // --- SINGLE INITIALIZATION OF PAINTER ---
        // We create the overlay once. We will update its data later.
        routeOverlay = SmoothRouteOverlay(
            mapView = mapView,
            useGradient = true, // Enable Blue-Purple Gradient
            animated = true     // Enable Drawing Animation
        )
        mapView.overlays.add(routeOverlay)
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

        // Disable infinite scrolling
        mapView.setHorizontalMapRepetitionEnabled(false)
        mapView.setVerticalMapRepetitionEnabled(false)
    }

    private fun setupCampusPaths() {
        campusPathsOverlay.addPathsToMap(mapView)
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
        binding.fabRecenter.setOnClickListener {
            myLocationOverlay?.myLocation?.let { location ->
                mapView.controller.animateTo(location)
            }
            viewModel.setFollowingUser(true)
        }

        binding.fabCompass.setOnClickListener {
            mapView.controller.animateTo(mapView.mapCenter, 17.0, 500, 0f)
        }

        binding.fabSatelliteView.setOnClickListener {
            toggleMapLayer()
        }

        binding.fabArMode.setOnClickListener {
            viewModel.switchToARMode()
        }

        binding.btnStartNavigation.setOnClickListener {
            viewModel.startNavigation()
        }

        binding.btnClosePanel.setOnClickListener {
            viewModel.stopNavigation()
            binding.navigationPanel.visibility = View.GONE
            clearRoute()
        }

        binding.btnEndNavigation.setOnClickListener {
            viewModel.stopNavigation()
            binding.activeNavigationPanel.visibility = View.GONE
            clearRoute()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.buildings.collectLatest { buildings ->
                updateBuildingMarkers(buildings)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.activeRoute.collectLatest { route ->
                if (route != null) {
                    displayRoute(route)
                } else {
                    clearRoute()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.navigationState.collectLatest { state ->
                handleNavigationState(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiEvent.collectLatest { event ->
                handleUiEvent(event)
            }
        }
    }

    // --- MAIN ROUTE DISPLAY LOGIC ---

    private fun displayRoute(route: Route) {
        // 1. Pass data to the Painter
        routeOverlay.setRoute(route)

        // 2. Update UI Panels
        binding.fabArMode.visibility = View.VISIBLE
        binding.tvDistance.text = formatDistance(route.totalDistance)
        binding.tvEta.text = formatTime(route.estimatedTime)
    }

    private fun clearRoute() {
        routeOverlay.clear()
        binding.fabArMode.visibility = View.GONE
    }

    // --- NAVIGATION STATE HANDLING ---

    private fun handleNavigationState(state: NavigationState) {
        when (state) {
            is NavigationState.Idle -> {
                binding.navigationPanel.visibility = View.GONE
                binding.activeNavigationPanel.visibility = View.GONE
                clearRoute()
            }
            is NavigationState.Previewing -> {
                binding.navigationPanel.visibility = View.VISIBLE
                binding.activeNavigationPanel.visibility = View.GONE

                binding.tvDestinationName.text = state.destination.name
                binding.tvDestinationDescription.text = state.destination.description
                binding.tvDistance.text = formatDistance(state.route.totalDistance)
                binding.tvEta.text = formatTime(state.route.estimatedTime)
            }
            is NavigationState.Navigating -> {
                binding.navigationPanel.visibility = View.GONE
                binding.activeNavigationPanel.visibility = View.VISIBLE
                updateActiveNavigation(state)
            }
            is NavigationState.Arrived -> {
                binding.navigationPanel.visibility = View.GONE
                binding.activeNavigationPanel.visibility = View.GONE
                Snackbar.make(binding.root, "You have arrived!", Snackbar.LENGTH_LONG).show()
                clearRoute()
            }
        }
    }

    private fun updateActiveNavigation(state: NavigationState.Navigating) {
        binding.tvCurrentInstruction.text = state.currentStep.instruction
        binding.tvDistanceToNext.text = "in ${formatDistance(state.distanceToNextWaypoint)}"
        binding.tvRemainingDistance.text = formatDistance(state.remainingDistance)
        binding.tvRemainingTime.text = formatTime(state.remainingTime)

        // UPDATE THE PAINTER (Green line follows user)
        routeOverlay.updateProgress(state.currentStepIndex, state.route.steps.size)

        // Update progress bar
        val totalDistance = state.route.totalDistance
        val remaining = state.remainingDistance
        val progress = if (totalDistance > 0) {
            ((totalDistance - remaining) / totalDistance * 100).toInt()
        } else { 0 }
        binding.progressRoute.progress = progress

        binding.ivDirectionIcon.setImageResource(getDirectionIcon(state.currentStep.direction))
    }

    // --- HELPER METHODS (These were missing previously) ---

    private fun updateBuildingMarkers(buildings: List<Building>) {
        // Clear existing markers
        for (marker in buildingMarkers) {
            mapView.overlays.remove(marker)
        }
        buildingMarkers.clear()

        // Add new markers
        for (building in buildings) {
            val marker = CustomMarkerOverlay(
                mapView = mapView,
                building = building,
                onMarkerClick = { clickedBuilding: Building ->
                    viewModel.onBuildingSelected(clickedBuilding)
                }
            )
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
            is MapUiEvent.OpenSearch -> { /* Handle search */ }
            is MapUiEvent.ShowError -> {
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
            }
            is MapUiEvent.NavigationStarted -> { /* Feedback */ }
            is MapUiEvent.NavigationStopped -> { /* Feedback */ }
            is MapUiEvent.WaypointReached -> { /* Sound/Haptics */ }
            is MapUiEvent.OffRoute -> {
                Snackbar.make(binding.root, "You are off route", Snackbar.LENGTH_SHORT)
                    .setAction("Recalculate") { viewModel.recalculateRoute() }
                    .show()
            }
            is MapUiEvent.ShowArrivalDialog -> {
                Snackbar.make(binding.root, "You have arrived!", Snackbar.LENGTH_LONG).show()
                viewModel.stopNavigation()
            }
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
        return if (meters < 1000) {
            "${meters.toInt()} m"
        } else {
            String.format("%.1f km", meters / 1000)
        }
    }

    private fun formatTime(seconds: Long): String {
        val minutes = seconds / 60
        return when {
            minutes < 1 -> "< 1 min"
            minutes < 60 -> "$minutes min"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun toggleMapLayer() {
        isSatelliteView = !isSatelliteView
        if (isSatelliteView) {
            try {
                val googleSatellite = object : OnlineTileSourceBase(
                    "Google-Satellite", 0, 20, 256, ".png",
                    arrayOf(
                        "https://mt0.google.com/vt/lyrs=s&hl=en&x=",
                        "https://mt1.google.com/vt/lyrs=s&hl=en&x=",
                        "https://mt2.google.com/vt/lyrs=s&hl=en&x=",
                        "https://mt3.google.com/vt/lyrs=s&hl=en&x="
                    )
                ) {
                    override fun getTileURLString(pMapTileIndex: Long): String {
                        val zoom = MapTileIndex.getZoom(pMapTileIndex)
                        val x = MapTileIndex.getX(pMapTileIndex)
                        val y = MapTileIndex.getY(pMapTileIndex)
                        return "${baseUrl}$x&y=$y&z=$zoom"
                    }
                }
                mapView.setTileSource(googleSatellite)
                Toast.makeText(requireContext(), "📡 Satellite View", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                isSatelliteView = false
            }
        } else {
            mapView.setTileSource(TileSourceFactory.MAPNIK)
            Toast.makeText(requireContext(), "🗺️ Default View", Toast.LENGTH_SHORT).show()
        }
        mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        myLocationOverlay?.enableMyLocation()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        myLocationOverlay?.disableMyLocation()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        campusPathsOverlay.clearPaths(mapView)
        mapView.onDetach()
        _binding = null
    }

    companion object {
        private const val TAG = "MapFragment"
    }
}