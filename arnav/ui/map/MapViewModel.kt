package com.campus.arnav.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.data.model.Route
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

@HiltViewModel
class MapViewModel @Inject constructor(
    private val campusRepository: CampusRepository,
    private val locationRepository: LocationRepository,
    private val navigationRepository: NavigationRepository,
    private val campusPathfinding: CampusPathfinding  // NEW: Integrated pathfinding
) : ViewModel() {

    // ============== EXISTING STATE FLOWS ==============

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

    // ============== NEW STATE FLOWS FOR PATHFINDING ==============

    private val _routePoints = MutableStateFlow<List<GeoPoint>?>(null)
    val routePoints: StateFlow<List<GeoPoint>?> = _routePoints.asStateFlow()

    private val _isPathfindingReady = MutableStateFlow(false)
    val isPathfindingReady: StateFlow<Boolean> = _isPathfindingReady.asStateFlow()

    private val _useSmartPathfinding = MutableStateFlow(true)  // Toggle for using A* vs simple routing
    val useSmartPathfinding: StateFlow<Boolean> = _useSmartPathfinding.asStateFlow()

    // ============== EXISTING VARIABLES ==============

    private var locationJob: Job? = null
    private var navigationJob: Job? = null
    private var currentStepIndex = 0

    // ============== INITIALIZATION ==============

    init {
        loadBuildings()
        startLocationUpdates()
        initializePathfinding()  // NEW: Initialize pathfinding system
    }

    /**
     * NEW: Initialize the pathfinding system with campus paths
     */
    private fun initializePathfinding() {
        viewModelScope.launch {
            try {
                campusPathfinding.initializeFromCampusPaths()
                _isPathfindingReady.value = true
                android.util.Log.d("MapViewModel", "Pathfinding initialized successfully")
            } catch (e: Exception) {
                android.util.Log.e("MapViewModel", "Failed to initialize pathfinding", e)
                _uiEvent.emit(MapUiEvent.ShowError("Failed to initialize pathfinding"))
            }
        }
    }

    private fun loadBuildings() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val buildingList = campusRepository.getAllBuildings()
                _buildings.value = buildingList
            } catch (e: Exception) {
                _uiEvent.emit(MapUiEvent.ShowError("Failed to load buildings: ${e.message}"))
            }
            _isLoading.value = false
        }
    }

    private fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationRepository.locationUpdates.collect { location ->
                _userLocation.value = location

                val state = _navigationState.value
                if (state is NavigationState.Navigating) {
                    updateNavigationProgress(location, state)
                }
            }
        }
    }

    // ============== ENHANCED ROUTE CALCULATION ==============

    fun onBuildingSelected(building: Building) {
        _selectedBuilding.value = building
        calculateRouteToBuilding(building)
    }

    /**
     * ENHANCED: Now uses smart pathfinding if enabled
     */
    private fun calculateRouteToBuilding(building: Building) {
        viewModelScope.launch {
            _isLoading.value = true

            val currentLocation = _userLocation.value
            if (currentLocation == null) {
                _uiEvent.emit(MapUiEvent.ShowError("Unable to get current location"))
                _isLoading.value = false
                return@launch
            }

            try {
                // Use smart pathfinding if ready and enabled
                if (_isPathfindingReady.value && _useSmartPathfinding.value) {
                    calculateSmartRoute(currentLocation, building)
                } else {
                    // Fallback to your existing repository method
                    calculateSimpleRoute(currentLocation, building)
                }
            } catch (e: Exception) {
                _uiEvent.emit(MapUiEvent.ShowError("Error calculating route: ${e.message}"))
            }

            _isLoading.value = false
        }
    }

    /**
     * NEW: Smart route calculation using A* pathfinding
     */
    private suspend fun calculateSmartRoute(currentLocation: CampusLocation, building: Building) {
        val start = GeoPoint(currentLocation.latitude, currentLocation.longitude)

        // Find nearest entrance or use building location
        val destination = if (building.entrances.isNotEmpty()) {
            building.entrances.minByOrNull { entrance ->
                calculateDistance(currentLocation, entrance)
            } ?: building.location
        } else {
            building.location
        }

        val end = GeoPoint(destination.latitude, destination.longitude)

        // Configure route options based on user preferences
        val options = RouteOptions(
            accessible = false,  // TODO: Get from user settings
            preferOutdoor = true,
            avoidStairs = false,
            walkingSpeed = 1.4  // m/s
        )

        val (result, pathPoints) = campusPathfinding.findRouteWithPath(start, end, options)

        when (result) {
            is RouteResult.Success -> {
                _activeRoute.value = result.route
                _routePoints.value = pathPoints  // NEW: Path points for drawing

                _navigationState.value = NavigationState.Previewing(
                    destination = building,
                    route = result.route
                )

                android.util.Log.d("MapViewModel", "Smart route calculated: ${result.route.waypoints.size} waypoints")
            }
            is RouteResult.NoRouteFound -> {
                android.util.Log.w("MapViewModel", "No route found, falling back to simple routing")
                // Fallback to simple routing
                calculateSimpleRoute(currentLocation, building)
            }
            is RouteResult.Error -> {
                android.util.Log.e("MapViewModel", "Route error: ${result.message}")
                _uiEvent.emit(MapUiEvent.ShowError(result.message))
            }
        }
    }

    /**
     * EXISTING: Simple route calculation (fallback method)
     */
    private suspend fun calculateSimpleRoute(currentLocation: CampusLocation, building: Building) {
        val route = navigationRepository.calculateRoute(currentLocation, building.location)

        if (route != null) {
            _activeRoute.value = route
            _routePoints.value = null  // No path points for simple routes

            _navigationState.value = NavigationState.Previewing(
                destination = building,
                route = route
            )
        } else {
            _uiEvent.emit(MapUiEvent.ShowError("Unable to calculate route"))
        }
    }

    // ============== NEW PATHFINDING METHODS ==============

    /**
     * NEW: Get alternative routes to current destination
     */
    fun getAlternativeRoutes() {
        viewModelScope.launch {
            val currentLocation = _userLocation.value
            val building = _selectedBuilding.value

            if (currentLocation == null || building == null || !_isPathfindingReady.value) {
                return@launch
            }

            val start = GeoPoint(currentLocation.latitude, currentLocation.longitude)
            val end = GeoPoint(building.location.latitude, building.location.longitude)

            try {
                val routes = campusPathfinding.getAlternativeRoutes(start, end, maxAlternatives = 3)

                if (routes.isNotEmpty()) {
                    // For now, show the first alternative
                    // TODO: Show UI to let user choose
                    _activeRoute.value = routes.first()
                    _routePoints.value = campusPathfinding.getRoutePathPoints(routes.first())

                    _uiEvent.emit(MapUiEvent.ShowError("Alternative route found"))
                }
            } catch (e: Exception) {
                android.util.Log.e("MapViewModel", "Error finding alternatives", e)
            }
        }
    }

    /**
     * NEW: Calculate accessible route (no stairs, elevator only)
     */
    fun calculateAccessibleRoute() {
        viewModelScope.launch {
            val currentLocation = _userLocation.value
            val building = _selectedBuilding.value

            if (currentLocation == null || building == null || !_isPathfindingReady.value) {
                return@launch
            }

            _isLoading.value = true

            val start = GeoPoint(currentLocation.latitude, currentLocation.longitude)
            val end = GeoPoint(building.location.latitude, building.location.longitude)

            val options = RouteOptions(
                accessible = true,
                avoidStairs = true,
                preferOutdoor = true,
                walkingSpeed = 1.4
            )

            val (result, pathPoints) = campusPathfinding.findRouteWithPath(start, end, options)

            when (result) {
                is RouteResult.Success -> {
                    _activeRoute.value = result.route
                    _routePoints.value = pathPoints

                    _navigationState.value = NavigationState.Previewing(
                        destination = building,
                        route = result.route
                    )

                    _uiEvent.emit(MapUiEvent.ShowError("Accessible route calculated"))
                }
                is RouteResult.NoRouteFound -> {
                    _uiEvent.emit(MapUiEvent.ShowError("No accessible route found"))
                }
                is RouteResult.Error -> {
                    _uiEvent.emit(MapUiEvent.ShowError(result.message))
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * NEW: Toggle between smart pathfinding and simple routing
     */
    fun togglePathfindingMode() {
        _useSmartPathfinding.value = !_useSmartPathfinding.value

        val mode = if (_useSmartPathfinding.value) "Smart" else "Simple"
        viewModelScope.launch {
            _uiEvent.emit(MapUiEvent.ShowError("Switched to $mode routing"))
        }
    }

    /**
     * NEW: Get walking time estimate to building
     */
    suspend fun getWalkingTimeToBuilding(building: Building): Long? {
        val currentLocation = _userLocation.value ?: return null

        if (!_isPathfindingReady.value) return null

        val start = GeoPoint(currentLocation.latitude, currentLocation.longitude)
        val end = GeoPoint(building.location.latitude, building.location.longitude)

        return campusPathfinding.estimateWalkingTime(start, end)
    }

    // ============== EXISTING NAVIGATION METHODS (PRESERVED) ==============

    fun startNavigation() {
        val route = _activeRoute.value ?: return
        val building = _selectedBuilding.value ?: return
        val firstStep = route.steps.firstOrNull() ?: return

        currentStepIndex = 0

        _navigationState.value = NavigationState.Navigating(
            route = route,
            currentStep = firstStep,
            currentStepIndex = 0,
            distanceToNextWaypoint = route.steps.firstOrNull()?.distance ?: 0.0,
            remainingDistance = route.totalDistance,
            remainingTime = route.estimatedTime
        )

        viewModelScope.launch {
            _uiEvent.emit(MapUiEvent.NavigationStarted)
        }
    }

    fun stopNavigation() {
        navigationJob?.cancel()
        currentStepIndex = 0
        _navigationState.value = NavigationState.Idle
        _activeRoute.value = null
        _routePoints.value = null  // NEW: Clear path points
        _selectedBuilding.value = null

        viewModelScope.launch {
            _uiEvent.emit(MapUiEvent.NavigationStopped)
        }
    }

    fun recalculateRoute() {
        val building = _selectedBuilding.value ?: return
        calculateRouteToBuilding(building)
    }

    /**
     * ENHANCED: Now checks if user is on actual path (not just distance)
     */
    private fun updateNavigationProgress(location: CampusLocation, state: NavigationState.Navigating) {
        val route = state.route

        val nextWaypoint = route.waypoints.getOrNull(currentStepIndex + 1)
        if (nextWaypoint != null) {
            val distanceToWaypoint = calculateDistance(location, nextWaypoint.location)

            if (distanceToWaypoint < 10.0) {
                advanceToNextStep(state)
            } else {
                val remainingDistance = calculateRemainingDistance(location)
                val remainingTime = (remainingDistance / 1.4).toLong()

                _navigationState.value = state.copy(
                    distanceToNextWaypoint = distanceToWaypoint,
                    remainingDistance = remainingDistance,
                    remainingTime = remainingTime
                )
            }
        }

        // Enhanced off-route detection
        if (isOffRoute(location)) {
            viewModelScope.launch {
                _uiEvent.emit(MapUiEvent.OffRoute)
            }
        }
    }

    private fun advanceToNextStep(state: NavigationState.Navigating) {
        val route = state.route
        currentStepIndex++

        if (currentStepIndex >= route.steps.size) {
            onArrived()
            return
        }

        val nextStep = route.steps[currentStepIndex]
        val remainingDistance = calculateRemainingDistance(_userLocation.value)
        val remainingTime = (remainingDistance / 1.4).toLong()

        _navigationState.value = state.copy(
            currentStep = nextStep,
            currentStepIndex = currentStepIndex,
            distanceToNextWaypoint = nextStep.distance,
            remainingDistance = remainingDistance,
            remainingTime = remainingTime
        )

        viewModelScope.launch {
            _uiEvent.emit(MapUiEvent.WaypointReached(currentStepIndex))
        }
    }

    private fun onArrived() {
        val building = _selectedBuilding.value
        if (building != null) {
            _navigationState.value = NavigationState.Arrived(building)
        } else {
            _navigationState.value = NavigationState.Idle
        }

        viewModelScope.launch {
            _uiEvent.emit(MapUiEvent.ShowArrivalDialog)
        }
    }

    /**
     * ENHANCED: Uses pathfinding to check if on actual path
     */
    private fun isOffRoute(location: CampusLocation): Boolean {
        if (!_isPathfindingReady.value || !_useSmartPathfinding.value) {
            // Fallback to simple distance check
            val route = _activeRoute.value ?: return false
            val minDistance = route.waypoints.minOfOrNull { waypoint ->
                calculateDistance(location, waypoint.location)
            } ?: return false
            return minDistance > 30.0
        }

        // Smart check: Is user on a campus path?
        val geoPoint = GeoPoint(location.latitude, location.longitude)
        val snappedPoint = campusPathfinding.snapToPath(geoPoint, maxDistance = 30.0)

        return snappedPoint == null  // If can't snap to path, user is off route
    }

    private fun calculateRemainingDistance(location: CampusLocation?): Double {
        if (location == null) return 0.0
        val route = _activeRoute.value ?: return 0.0

        var distance = 0.0

        val nextWaypoint = route.waypoints.getOrNull(currentStepIndex + 1)
        if (nextWaypoint != null) {
            distance += calculateDistance(location, nextWaypoint.location)
        }

        for (i in (currentStepIndex + 1) until route.steps.size) {
            distance += route.steps[i].distance
        }

        return distance
    }

    private fun calculateDistance(from: CampusLocation, to: CampusLocation): Double {
        val earthRadius = 6371000.0

        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = Math.toRadians(to.latitude - from.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)

        val a = kotlin.math.sin(deltaLat / 2) * kotlin.math.sin(deltaLat / 2) +
                kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
                kotlin.math.sin(deltaLon / 2) * kotlin.math.sin(deltaLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return earthRadius * c
    }

    // ============== EXISTING UI METHODS (PRESERVED) ==============

    fun switchToARMode() {
        val route = _activeRoute.value ?: return
        viewModelScope.launch {
            _uiEvent.emit(MapUiEvent.LaunchARNavigation(route))
        }
    }

    fun openSearch() {
        viewModelScope.launch {
            _uiEvent.emit(MapUiEvent.OpenSearch)
        }
    }

    fun setFollowingUser(following: Boolean) {
        _isFollowingUser.value = following
    }

    fun clearSelection() {
        _selectedBuilding.value = null
        _activeRoute.value = null
        _routePoints.value = null  // NEW: Clear path points
        _navigationState.value = NavigationState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
        navigationJob?.cancel()
    }
}

/**
 * Map UI Events (EXISTING - preserved as-is)
 */
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