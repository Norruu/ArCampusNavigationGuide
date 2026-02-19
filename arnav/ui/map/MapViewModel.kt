package com.campus.arnav.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.NavigationStep
import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.data.model.Route
import com.campus.arnav.data.model.BuildingType
import com.campus.arnav.data.repository.CampusRepository
import com.campus.arnav.data.repository.LocationRepository
import com.campus.arnav.data.repository.NavigationRepository
import com.campus.arnav.domain.pathfinding.CampusPathfinding
import com.campus.arnav.domain.pathfinding.RouteResult
import com.campus.arnav.ui.navigation.NavigationState
import com.campus.arnav.util.NavigationFeedbackManager
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
    private val campusPathfinding: CampusPathfinding,
    private val feedbackManager: NavigationFeedbackManager
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

    private val _routePoints = MutableStateFlow<List<GeoPoint>?>(null)
    val routePoints: StateFlow<List<GeoPoint>?> = _routePoints.asStateFlow()

    // --- NEW: Persistent View States ---
    private val _isSatelliteView = MutableStateFlow(false)
    val isSatelliteView: StateFlow<Boolean> = _isSatelliteView.asStateFlow()

    private val _isCompassMode = MutableStateFlow(false)
    val isCompassMode: StateFlow<Boolean> = _isCompassMode.asStateFlow()
    // -----------------------------------

    private val _isPathfindingReady = MutableStateFlow(false)
    private val _useSmartPathfinding = MutableStateFlow(true)

    private var locationJob: Job? = null
    private var navigationJob: Job? = null
    private var currentStepIndex = 0
    private var currentCategory: BuildingType? = null
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
                campusPathfinding.initializeFromCampusPaths()
                _isPathfindingReady.value = true
            } catch (e: Exception) {
                _uiEvent.emit(MapUiEvent.ShowError("Failed to initialize pathfinding"))
            }
        }
    }

    private fun loadBuildings() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = campusRepository.getAllBuildings()
                allBuildingsCache = result
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

        if (currentState is NavigationState.Navigating) return

        if (currentState is NavigationState.Previewing && currentState.destination.id == building.id) return

        _selectedBuilding.value = building
        _buildings.value = listOf(building)
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
        val destination = building.entrances.minByOrNull { calculateDistance(currentLocation, it) } ?: building.location
        val end = GeoPoint(destination.latitude, destination.longitude)

        when (val routeResult = campusPathfinding.findRoute(start, end)) {
            is RouteResult.Success -> handleRouteSuccess(routeResult.route, building)
            is RouteResult.NoRouteFound -> calculateSimpleRoute(currentLocation, building)
            is RouteResult.Error -> _uiEvent.emit(MapUiEvent.ShowError(routeResult.message))
        }
    }

    private suspend fun calculateSimpleRoute(currentLocation: CampusLocation, building: Building) {
        when (val result = navigationRepository.calculateRoute(currentLocation, building.location)) {
            is RouteResult.Success -> handleRouteSuccess(result.route, building)
            is RouteResult.Error -> _uiEvent.emit(MapUiEvent.ShowError("Route Error: ${result.message}"))
            is RouteResult.NoRouteFound -> _uiEvent.emit(MapUiEvent.ShowError("No route found: ${result.message}"))
        }
    }

    // --- UPDATED: Use instruction generator here ---
    private fun handleRouteSuccess(route: Route, building: Building) {
        // Calculate the "Turn Left/Right" instructions
        val improvedRoute = generateRouteInstructions(route)

        _activeRoute.value = improvedRoute
        val points = improvedRoute.waypoints.map { GeoPoint(it.location.latitude, it.location.longitude) }
        _routePoints.value = points
        _navigationState.value = NavigationState.Previewing(destination = building, route = improvedRoute)
    }

    // --- NEW: Instruction Generator Functions ---
    private fun generateRouteInstructions(route: Route): Route {
        if (route.waypoints.size < 3) return route

        val newSteps = route.steps.toMutableList()
        val waypoints = route.waypoints

        for (i in 0 until min(newSteps.size, waypoints.size - 1)) {
            if (i > 0 && i < waypoints.size - 1) {
                val prev = waypoints[i-1]
                val current = waypoints[i]
                val next = waypoints[i+1]

                val bearing1 = calculateBearing(prev.location, current.location)
                val bearing2 = calculateBearing(current.location, next.location)

                val instructionText = getTurnDescription(bearing1, bearing2)

                // Update instruction
                newSteps[i] = newSteps[i].copy(instruction = instructionText)
            } else if (i == 0) {
                newSteps[i] = newSteps[i].copy(instruction = "Start navigation")
            }
        }
        return route.copy(steps = newSteps)
    }

    private fun calculateBearing(start: CampusLocation, end: CampusLocation): Double {
        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)
        val lat2 = Math.toRadians(end.latitude)
        val lon2 = Math.toRadians(end.longitude)
        val dLon = lon2 - lon1
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return Math.toDegrees(atan2(y, x))
    }

    private fun getTurnDescription(prevBearing: Double, currBearing: Double): String {
        var diff = currBearing - prevBearing
        while (diff < -180) diff += 360
        while (diff > 180) diff -= 360
        return when {
            diff in -20.0..20.0 -> "Go Straight"
            diff in -45.0..-20.0 -> "Slight Left"
            diff < -45.0 -> "Turn Left"
            diff in 20.0..45.0 -> "Slight Right"
            else -> "Turn Right"
        }
    }
    // ---------------------------------------------

    // ============== NAVIGATION CONTROL ==============

    fun startNavigation() {
        val route = _activeRoute.value ?: return
        val building = _selectedBuilding.value ?: return

        currentStepIndex = 0
        val firstStep = route.steps.first()

        feedbackManager.speak("Starting navigation to ${building.name}. ${firstStep.instruction}")

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

    fun stopNavigation() {
        navigationJob?.cancel()

        filterBuildingsByCategory(currentCategory)

        _selectedBuilding.value = null
        _activeRoute.value = null
        _routePoints.value = null
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

        if (isOffRoute(location, route)) {
            viewModelScope.launch { _uiEvent.emit(MapUiEvent.OffRoute) }
            return
        }

        val finalDestination = route.waypoints.lastOrNull()?.location
        val distanceToFinish = if (finalDestination != null) calculateDistance(location, finalDestination) else 0.0

        if (distanceToFinish < 10.0 && distanceToFinish > 0.0) {
            feedbackManager.vibrateForArrival()
            feedbackManager.speak("You have arrived at your destination.")
            onArrived()
            return
        }

        val nextWaypoint = route.waypoints.getOrNull(currentStepIndex + 1)
        if (nextWaypoint != null) {
            val distToNext = calculateDistance(location, nextWaypoint.location)
            if (distToNext < 10.0) {
                advanceToNextStep(state)
            } else {
                _navigationState.value = state.copy(distanceToNextWaypoint = distToNext, remainingDistance = distanceToFinish)
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
        feedbackManager.vibrateForTurn()
        feedbackManager.speak(nextStep.instruction)

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
            _navigationState.value = NavigationState.Idle
        }
    }

    private fun isOffRoute(location: CampusLocation, route: Route): Boolean {
        val minDist = route.waypoints.minOfOrNull { calculateDistance(location, it.location) } ?: 0.0
        return minDist > 40.0
    }

    // ============== HELPER METHODS ==============

    fun filterBuildingsByCategory(type: BuildingType?) {
        currentCategory = type
        if (type == null) {
            _buildings.value = allBuildingsCache
        } else {
            _buildings.value = allBuildingsCache.filter { it.type == type }
        }

        val currentState = _navigationState.value

        if (currentState !is NavigationState.Navigating && currentState !is NavigationState.Previewing) {
            _selectedBuilding.value = null
            _navigationState.value = NavigationState.Idle
        }
    }

    fun selectBuildingById(buildingId: String) {
        val building = allBuildingsCache.find { it.id == buildingId }
        if (building != null) {
            onSearchResultClicked(building)
        }
    }

    fun onSearchResultClicked(building: Building) {
        onBuildingSelected(building)
        viewModelScope.launch {
            val target = GeoPoint(building.location.latitude, building.location.longitude)
            _uiEvent.emit(MapUiEvent.MoveCameraTo(target))
        }
    }

    private fun calculateDistance(p1: CampusLocation, p2: CampusLocation): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    fun switchToARMode() {
        val route = _activeRoute.value ?: return

        val buildingName = _selectedBuilding.value?.name ?: "Destination"

        viewModelScope.launch {
            _uiEvent.emit(MapUiEvent.LaunchARNavigation(route, buildingName))
        }
    }

    fun openSearch() {
        viewModelScope.launch { _uiEvent.emit(MapUiEvent.OpenSearch) }
    }

    fun setFollowingUser(following: Boolean) {
        _isFollowingUser.value = following
    }

    // --- NEW: State Setters ---
    fun setSatelliteView(enabled: Boolean) {
        _isSatelliteView.value = enabled
    }

    fun setCompassMode(enabled: Boolean) {
        _isCompassMode.value = enabled
    }
    // --------------------------

    override fun onCleared() {
        super.onCleared()
        feedbackManager.shutdown()
    }
}

sealed class MapUiEvent {
    object OpenSearch : MapUiEvent()
    object NavigationStarted : MapUiEvent()
    object NavigationStopped : MapUiEvent()
    object OffRoute : MapUiEvent()
    object ShowArrivalDialog : MapUiEvent()
    data class WaypointReached(val index: Int) : MapUiEvent()
    data class LaunchARNavigation(val route: Route, val destinationName: String) : MapUiEvent()
    data class ShowError(val message: String) : MapUiEvent()
    data class MoveCameraTo(val location: GeoPoint) : MapUiEvent()
}