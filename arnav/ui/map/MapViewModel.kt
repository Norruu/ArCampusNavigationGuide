package com.campus.arnav.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.data.model.Route
import com.campus.arnav.data.model.Waypoint
import com.campus.arnav.data.repository.CampusRepository
import com.campus.arnav.data.repository.LocationRepository
import com.campus.arnav.data.repository.NavigationRepository
import com.campus.arnav.domain.pathfinding.CampusPathfinding
import com.campus.arnav.domain.pathfinding.RouteOptions
import com.campus.arnav.domain.pathfinding.RouteResult
import com.campus.arnav.ui.navigation.NavigationState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import javax.inject.Inject
import kotlin.math.*

@HiltViewModel
class MapViewModel @Inject constructor(
    private val campusRepository: CampusRepository,
    private val locationRepository: LocationRepository,
    private val navigationRepository: NavigationRepository,
    private val campusPathfinding: CampusPathfinding
) : ViewModel() {

    // ============== STATE FLOWS ==============

    private val _buildings = MutableStateFlow<List<Building>>(emptyList())
    val buildings: StateFlow<List<Building>> = _buildings.asStateFlow()

    private val _selectedBuilding = MutableStateFlow<Building?>(null)
    val selectedBuilding: StateFlow<Building?> = _selectedBuilding.asStateFlow()

    private val _activeRoute = MutableStateFlow<Route?>(null)
    val activeRoute: StateFlow<Route?> = _activeRoute.asStateFlow()

    private val _navigationState = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    private val _userLocation = MutableStateFlow<CampusLocation?>(null)
    val userLocation: StateFlow<CampusLocation?> = _userLocation.asStateFlow()

    private val _isFollowingUser = MutableStateFlow(true)
    val isFollowingUser: StateFlow<Boolean> = _isFollowingUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiEvent = MutableSharedFlow<MapUiEvent>()
    val uiEvent: SharedFlow<MapUiEvent> = _uiEvent.asSharedFlow()

    // Pathfinding States
    private val _routePoints = MutableStateFlow<List<GeoPoint>?>(null)
    val routePoints: StateFlow<List<GeoPoint>?> = _routePoints.asStateFlow()

    private val _isPathfindingReady = MutableStateFlow(false)
    private val _useSmartPathfinding = MutableStateFlow(true)

    // Variables
    private var locationJob: Job? = null
    private var navigationJob: Job? = null
    private var currentStepIndex = 0

    private var allBuildingsCache: List<Building> = emptyList()

    private var lastLocationUpdate: Long = 0
    private var lastLocation: CampusLocation? = null

    // ============== INITIALIZATION ==============

    init {
        loadBuildings()
        startLocationUpdates()
        initializePathfinding()
    }

    private fun initializePathfinding() {
        viewModelScope.launch {
            try {
                // Initialize the brain
                campusPathfinding.initializeFromCampusPaths()
                _isPathfindingReady.value = true
            } catch (e: Exception) {
                _uiEvent.emit(MapUiEvent.ShowError("Failed to initialize pathfinding"))
            }
        }
    }

    // In MapViewModel.kt

    private fun loadBuildings() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Fetch from repository
                val result = campusRepository.getAllBuildings()

                // --- CRITICAL FIX: Save the result to the cache ---
                allBuildingsCache = result
                // -------------------------------------------------

                // Show all buildings initially
                _buildings.value = result

            } catch (e: Exception) {
                _uiEvent.emit(MapUiEvent.ShowError("Failed to load buildings"))
            }
            _isLoading.value = false
        }
    }

    private fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationRepository.locationUpdates.collect { location ->
                _userLocation.value = location

                val currentState = _navigationState.value
                if (currentState is NavigationState.Navigating) {
                    val currentTime = System.currentTimeMillis()
                    val timeDiff = currentTime - lastLocationUpdate

                    // Only update if 2 seconds passed OR moved > 3 meters
                    val dist = lastLocation?.let { calculateDistance(it, location) } ?: 100.0

                    if (timeDiff > 2000 || dist > 3.0) {
                        lastLocationUpdate = currentTime
                        lastLocation = location
                        updateNavigationProgress(location, currentState)
                    }
                }
            }
        }
    }

    // ============== ROUTING LOGIC ==============

    fun onBuildingSelected(building: Building) {
        val currentState = _navigationState.value

        // If we are already previewing THIS building, do nothing.
        // This stops the "flicker" on double-clicks.
        if (currentState is NavigationState.Previewing && currentState.destination.id == building.id) {
            return
        }

        // Only switch if we are Idle or moving to a DIFFERENT building
        _selectedBuilding.value = building
        _buildings.value = listOf(building)

        // Update state directly to Previewing
        _navigationState.value = NavigationState.Previewing(building, null)

        calculateRouteToBuilding(building)
    }

    private fun calculateRouteToBuilding(building: Building) {
        viewModelScope.launch {
            _isLoading.value = true
            val currentLocation = _userLocation.value

            if (currentLocation == null) {
                _uiEvent.emit(MapUiEvent.ShowError("Waiting for GPS location..."))
                _isLoading.value = false
                return@launch
            }

            // Decide which strategy to use
            if (_isPathfindingReady.value && _useSmartPathfinding.value) {
                calculateSmartRoute(currentLocation, building)
            } else {
                calculateSimpleRoute(currentLocation, building)
            }
            _isLoading.value = false
        }
    }

    private suspend fun calculateSmartRoute(currentLocation: CampusLocation, building: Building) {
        val start = GeoPoint(currentLocation.latitude, currentLocation.longitude)

        // Use nearest entrance if available, else center
        val destination = building.entrances.minByOrNull {
            calculateDistance(currentLocation, it)
        } ?: building.location

        val end = GeoPoint(destination.latitude, destination.longitude)

        // Find route
        val routeResult = campusPathfinding.findRoute(start, end)

        when (routeResult) {
            is RouteResult.Success -> {
                handleRouteSuccess(routeResult.route, building)
            }
            is RouteResult.NoRouteFound -> {
                // Fallback
                calculateSimpleRoute(currentLocation, building)
            }
            is RouteResult.Error -> {
                _uiEvent.emit(MapUiEvent.ShowError(routeResult.message))
            }
        }
    }

    /**
     * FIXED: This function now correctly handles RouteResult
     */
    private suspend fun calculateSimpleRoute(currentLocation: CampusLocation, building: Building) {
        // calculateRoute returns RouteResult
        val result = navigationRepository.calculateRoute(currentLocation, building.location)

        when (result) {
            is RouteResult.Success -> {
                handleRouteSuccess(result.route, building)
            }
            is RouteResult.Error -> {
                _uiEvent.emit(MapUiEvent.ShowError("Route Error: ${result.message}"))
            }
            is RouteResult.NoRouteFound -> {
                _uiEvent.emit(MapUiEvent.ShowError("No route found: ${result.message}"))
            }
        }
    }

    private fun handleRouteSuccess(route: Route, building: Building) {
        _activeRoute.value = route

        // Generate points for the smooth overlay
        val points = route.waypoints.map {
            GeoPoint(it.location.latitude, it.location.longitude)
        }
        _routePoints.value = points

        // FIXED: Removed 'eta' and 'distance' parameters causing the error
        _navigationState.value = NavigationState.Previewing(
            destination = building,
            route = route
        )
    }

    // ============== NAVIGATION CONTROL ==============

    fun startNavigation() {
        val route = _activeRoute.value ?: return
        val building = _selectedBuilding.value ?: return

        currentStepIndex = 0
        val firstStep = route.steps.first()

        _navigationState.value = NavigationState.Navigating(
            route = route,
            currentStep = firstStep,
            currentStepIndex = 0,
            distanceToNextWaypoint = firstStep.distance,
            remainingDistance = route.totalDistance,
            remainingTime = route.estimatedTime
        )

        viewModelScope.launch { _uiEvent.emit(MapUiEvent.NavigationStarted) }
    }

    // In MapViewModel.kt

    fun stopNavigation() {
        navigationJob?.cancel()

        // 1. Restore ALL markers to the map
        _buildings.value = allBuildingsCache

        // 2. Clear selection and route
        _selectedBuilding.value = null
        _activeRoute.value = null
        _routePoints.value = null

        // 3. Reset State
        _navigationState.value = NavigationState.Idle
        viewModelScope.launch { _uiEvent.emit(MapUiEvent.NavigationStopped) }
    }

    fun recalculateRoute() {
        val building = _selectedBuilding.value ?: return
        calculateRouteToBuilding(building)
    }

    // ============== NAVIGATION UPDATES ==============

    private fun updateNavigationProgress(location: CampusLocation, state: NavigationState.Navigating) {
        val route = state.route

        // 1. Check Off-Route
        if (isOffRoute(location, route)) {
            viewModelScope.launch { _uiEvent.emit(MapUiEvent.OffRoute) }
            return
        }

        // 2. Check Waypoint Reached
        val nextWaypoint = route.waypoints.getOrNull(currentStepIndex + 1)
        if (nextWaypoint != null) {
            val dist = calculateDistance(location, nextWaypoint.location)
            if (dist < 10.0) { // 10 meters threshold
                advanceToNextStep(state)
            } else {
                // Update distance display
                _navigationState.value = state.copy(
                    distanceToNextWaypoint = dist
                )
            }
        }
    }

    private fun advanceToNextStep(state: NavigationState.Navigating) {
        currentStepIndex++
        if (currentStepIndex >= state.route.steps.size) {
            onArrived()
            return
        }

        val nextStep = state.route.steps[currentStepIndex]
        _navigationState.value = state.copy(
            currentStep = nextStep,
            currentStepIndex = currentStepIndex,
            distanceToNextWaypoint = nextStep.distance
        )
        viewModelScope.launch { _uiEvent.emit(MapUiEvent.WaypointReached(currentStepIndex)) }
    }

    private fun onArrived() {
        val building = _selectedBuilding.value
        if (building != null) {
            _navigationState.value = NavigationState.Arrived(building)
            viewModelScope.launch { _uiEvent.emit(MapUiEvent.ShowArrivalDialog) }
        } else {
            // Fallback if building is null
            _navigationState.value = NavigationState.Idle
        }
    }

    private fun isOffRoute(location: CampusLocation, route: Route): Boolean {
        // Simple check: am I more than 30 meters from ANY point on the route?
        val minDist = route.waypoints.minOfOrNull {
            calculateDistance(location, it.location)
        } ?: 0.0
        return minDist > 30.0
    }

    // ============== HELPER METHODS ==============

    private fun calculateDistance(p1: CampusLocation, p2: CampusLocation): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun formatTime(seconds: Long): String {
        val mins = seconds / 60
        return "$mins min"
    }

    private fun formatDistance(meters: Double): String {
        return if (meters >= 1000) String.format("%.1f km", meters / 1000) else "${meters.toInt()} m"
    }

    // UI Events
    fun switchToARMode() {
        val route = _activeRoute.value ?: return
        viewModelScope.launch { _uiEvent.emit(MapUiEvent.LaunchARNavigation(route)) }
    }

    fun openSearch() {
        viewModelScope.launch { _uiEvent.emit(MapUiEvent.OpenSearch) }
    }

    fun setFollowingUser(following: Boolean) {
        _isFollowingUser.value = following
    }
}

sealed class MapUiEvent {
    object OpenSearch : MapUiEvent()
    object NavigationStarted : MapUiEvent()
    object NavigationStopped : MapUiEvent()
    object OffRoute : MapUiEvent()
    object ShowArrivalDialog : MapUiEvent()
    data class WaypointReached(val index: Int) : MapUiEvent()
    data class LaunchARNavigation(val route: Route) : MapUiEvent()
    data class ShowError(val message: String) : MapUiEvent()
}