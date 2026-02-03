package com.campus.arnav.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.campus.arnav.ui.navigation.NavigationState
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@AndroidEntryPoint
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MapViewModel by viewModels()

    private lateinit var mapView: MapView
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var routeOverlay: SmoothRouteOverlay? = null
    private val buildingMarkers = mutableListOf<CustomMarkerOverlay>()

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
        setupMap()
        setupUI()
        observeViewModel()
    }

    private fun setupMap() {
        mapView = binding.mapView

        mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(17.0)
            controller.setCenter(GeoPoint(37.4275, -122.1697))
            minZoomLevel = 14.0
            maxZoomLevel = 20.0
        }

        setupLocationOverlay()
    }

    private fun setupLocationOverlay() {
        if (hasLocationPermission()) {
            myLocationOverlay = MyLocationNewOverlay(
                GpsMyLocationProvider(requireContext()),
                mapView
            ).apply {
                enableMyLocation()
                enableFollowLocation()
            }
            mapView.overlays.add(myLocationOverlay)
        }
    }

    private fun setupUI() {
        binding.searchCard.setOnClickListener {
            viewModel.openSearch()
        }

        binding.fabRecenter.setOnClickListener {
            myLocationOverlay?.myLocation?.let { location ->
                mapView.controller.animateTo(location)
            }
            viewModel.setFollowingUser(true)
        }

        binding.fabCompass.setOnClickListener {
            mapView.controller.animateTo(mapView.mapCenter, 17.0, 500, 0f)
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
        }

        binding.btnEndNavigation.setOnClickListener {
            viewModel.stopNavigation()
            binding.activeNavigationPanel.visibility = View.GONE
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
            viewModel.selectedBuilding.collectLatest { building ->
                if (building != null) {
                    showNavigationPanel(building)
                } else {
                    binding.navigationPanel.visibility = View.GONE
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

    private fun displayRoute(route: Route) {
        routeOverlay?.let { mapView.overlays.remove(it) }

        val geoPoints = route.waypoints.map { waypoint ->
            GeoPoint(waypoint.location.latitude, waypoint.location.longitude)
        }

        routeOverlay = SmoothRouteOverlay.createPrimaryRoute(geoPoints)
        mapView.overlays.add(routeOverlay)
        mapView.invalidate()

        binding.fabArMode.visibility = View.VISIBLE
        binding.tvDistance.text = formatDistance(route.totalDistance)
        binding.tvEta.text = formatTime(route.estimatedTime)
    }

    private fun clearRoute() {
        routeOverlay?.let { mapView.overlays.remove(it) }
        routeOverlay = null
        mapView.invalidate()
        binding.fabArMode.visibility = View.GONE
    }

    private fun showNavigationPanel(building: Building) {
        binding.navigationPanel.visibility = View.VISIBLE
        binding.activeNavigationPanel.visibility = View.GONE
        binding.tvDestinationName.text = building.name
        binding.tvDestinationDescription.text = building.description
    }

    /**
     * Handle all navigation states
     */
    private fun handleNavigationState(state: NavigationState) {
        when (state) {
            is NavigationState.Idle -> {
                binding.navigationPanel.visibility = View.GONE
                binding.activeNavigationPanel.visibility = View.GONE
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
                Snackbar.make(
                    binding.root,
                    "You have arrived at ${state.destination.name}!",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateActiveNavigation(state: NavigationState.Navigating) {
        binding.tvCurrentInstruction.text = state.currentStep.instruction
        binding.tvDistanceToNext.text = "in ${formatDistance(state.distanceToNextWaypoint)}"
        binding.tvRemainingDistance.text = formatDistance(state.remainingDistance)
        binding.tvRemainingTime.text = formatTime(state.remainingTime)

        val totalDistance = state.route.totalDistance
        val remaining = state.remainingDistance
        val progress = if (totalDistance > 0) {
            ((totalDistance - remaining) / totalDistance * 100).toInt()
        } else {
            0
        }
        binding.progressRoute.progress = progress

        // Get direction icon
        val iconRes = getDirectionIcon(state.currentStep.direction)
        binding.ivDirectionIcon.setImageResource(iconRes)
    }

    /**
     * Get drawable resource for direction
     */
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

    private fun handleUiEvent(event: MapUiEvent) {
        when (event) {
            is MapUiEvent.LaunchARNavigation -> {
                val intent = Intent(requireContext(), ARNavigationActivity::class.java)
                intent.putExtra(ARNavigationActivity.EXTRA_ROUTE, event.route)
                startActivity(intent)
            }
            is MapUiEvent.OpenSearch -> {
                // Open search bottom sheet or fragment
            }
            is MapUiEvent.ShowError -> {
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
            }
            is MapUiEvent.NavigationStarted -> {
                // Navigation started
            }
            is MapUiEvent.NavigationStopped -> {
                // Navigation stopped
            }
            is MapUiEvent.WaypointReached -> {
                // Waypoint reached feedback
            }
            is MapUiEvent.OffRoute -> {
                Snackbar.make(binding.root, "You are off route", Snackbar.LENGTH_SHORT)
                    .setAction("Recalculate") {
                        viewModel.recalculateRoute()
                    }
                    .show()
            }
            is MapUiEvent.ShowArrivalDialog -> {
                Snackbar.make(
                    binding.root,
                    "You have arrived at your destination!",
                    Snackbar.LENGTH_LONG
                ).show()
                viewModel.stopNavigation()
            }
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
        mapView.onDetach()
        _binding = null
    }
}