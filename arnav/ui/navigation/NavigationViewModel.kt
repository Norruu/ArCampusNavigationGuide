package com.campus.arnav.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.data.model.Route
import com.campus.arnav.data.repository.LocationRepository
import com.campus.arnav.data.repository.NavigationRepository
import com.campus.arnav.domain.pathfinding.RouteResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// 1. Define Transport Modes with their respective speeds in meters/second
enum class TransportMode(val speedMetersPerSecond: Double) {
    WALKING(1.4),       // ~5 km/h
    VEHICLE(6.9)        // ~25 km/h (Campus speed limit)
}

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val navigationRepository: NavigationRepository
) : ViewModel() {

    private val _destination = MutableStateFlow<Building?>(null)
    val destination: StateFlow<Building?> = _destination.asStateFlow()

    private val _route = MutableStateFlow<Route?>(null)
    val route: StateFlow<Route?> = _route.asStateFlow()

    private val _alternativeRoutes = MutableStateFlow<List<Route>>(emptyList())
    val alternativeRoutes: StateFlow<List<Route>> = _alternativeRoutes.asStateFlow()

    private val _navigationState = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _uiEvent = MutableSharedFlow<NavigationUiEvent>()
    val uiEvent: SharedFlow<NavigationUiEvent> = _uiEvent.asSharedFlow()

    private val _userLocation = MutableStateFlow<CampusLocation?>(null)
    val userLocation: StateFlow<CampusLocation?> = _userLocation.asStateFlow()

    // 2. Track the current transport mode
    private val _transportMode = MutableStateFlow(TransportMode.WALKING)
    val transportMode: StateFlow<TransportMode> = _transportMode.asStateFlow()

    private var navigationJob: Job? = null
    private var locationTrackingJob: Job? = null
    private var currentStepIndex = 0

    init {
        startLocationTracking()
    }

    private fun startLocationTracking() {
        locationTrackingJob?.cancel()
        locationTrackingJob = viewModelScope.launch {
            locationRepository.locationUpdates.collect { location ->
                _userLocation.value = location

                val state = _navigationState.value
                if (state is NavigationState.Navigating) {
                    updateNavigationProgress(location, state)
                }
            }
        }
    }

    // 3. Allow UI to change transport mode and immediately update ETA
    fun setTransportMode(mode: TransportMode) {
        _transportMode.value = mode
        val state = _navigationState.value

        // Dynamically update time if currently navigating
        if (state is NavigationState.Navigating) {
            val updatedTime = (state.remainingDistance / mode.speedMetersPerSecond).toLong()
            _navigationState.value = state.copy(remainingTime = updatedTime)
        }
        // Or recalculate the base route ETA if in preview mode
        else if (state is NavigationState.Previewing && state.route != null) {
            val updatedRoute = state.route.copy(
                estimatedTime = (state.route.totalDistance / mode.speedMetersPerSecond).toLong()
            )
            _route.value = updatedRoute
            _navigationState.value = state.copy(route = updatedRoute)
        }
    }

    fun setDestination(building: Building) {
        _destination.value = building
        calculateRoute(building)
    }

    private fun calculateRoute(building: Building) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val currentLocation = _userLocation.value
            if (currentLocation == null) {
                _error.value = "Unable to get current location"
                _isLoading.value = false
                return@launch
            }

            try {
                val result = navigationRepository.calculateRoute(currentLocation, building.location)

                when (result) {
                    is RouteResult.Success -> {
                        // Adjust the initial estimated time based on the current transport mode
                        val adjustedRoute = result.route.copy(
                            estimatedTime = (result.route.totalDistance / _transportMode.value.speedMetersPerSecond).toLong()
                        )
                        _route.value = adjustedRoute
                        _navigationState.value = NavigationState.Previewing(
                            destination = building,
                            route = adjustedRoute
                        )
                    }
                    is RouteResult.Error -> _error.value = result.message
                    is RouteResult.NoRouteFound -> _error.value = result.message
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            }

            _isLoading.value = false
        }
    }

    fun selectRoute(route: Route) {
        val adjustedRoute = route.copy(
            estimatedTime = (route.totalDistance / _transportMode.value.speedMetersPerSecond).toLong()
        )
        _route.value = adjustedRoute
        val dest = _destination.value ?: return
        _navigationState.value = NavigationState.Previewing(
            destination = dest,
            route = adjustedRoute
        )
    }

    fun startNavigation() {
        val currentRoute = _route.value ?: return
        val dest = _destination.value ?: return

        currentStepIndex = 0

        val firstStep = currentRoute.steps.firstOrNull()
        if (firstStep == null) {
            _error.value = "Route has no steps"
            return
        }

        val remainingDist = currentRoute.totalDistance
        _navigationState.value = NavigationState.Navigating(
            route = currentRoute,
            currentStep = firstStep,
            currentStepIndex = 0,
            distanceToNextWaypoint = calculateDistanceToWaypoint(0),
            remainingDistance = remainingDist,
            remainingTime = (remainingDist / _transportMode.value.speedMetersPerSecond).toLong() // Use dynamic speed
        )

        viewModelScope.launch {
            _uiEvent.emit(NavigationUiEvent.NavigationStarted)
        }
    }

    private fun updateNavigationProgress(location: CampusLocation, state: NavigationState.Navigating) {
        val route = state.route

        val currentWaypoint = route.waypoints.getOrNull(currentStepIndex + 1)
        if (currentWaypoint != null) {
            val distanceToWaypoint = calculateDistance(location, currentWaypoint.location)

            if (distanceToWaypoint < 10.0) {
                onWaypointReached(state)
            } else {
                val remainingDistance = calculateRemainingDistance(location, currentStepIndex)
                // 4. Use the dynamic speed based on selected Transport Mode
                val remainingTime = (remainingDistance / _transportMode.value.speedMetersPerSecond).toLong()

                _navigationState.value = state.copy(
                    distanceToNextWaypoint = distanceToWaypoint,
                    remainingDistance = remainingDistance,
                    remainingTime = remainingTime
                )
            }
        }

        if (isOffRoute(location)) {
            viewModelScope.launch {
                _uiEvent.emit(NavigationUiEvent.OffRoute)
            }
        }
    }

    private fun onWaypointReached(state: NavigationState.Navigating) {
        val route = state.route

        currentStepIndex++

        if (currentStepIndex >= route.steps.size - 1) {
            onArrived()
            return
        }

        val nextStep = route.steps.getOrNull(currentStepIndex) ?: return
        val distanceToNext = calculateDistanceToWaypoint(currentStepIndex)
        val remainingDistance = calculateRemainingDistance(_userLocation.value, currentStepIndex)
        // 5. Use the dynamic speed based on selected Transport Mode
        val remainingTime = (remainingDistance / _transportMode.value.speedMetersPerSecond).toLong()

        _navigationState.value = state.copy(
            currentStep = nextStep,
            currentStepIndex = currentStepIndex,
            distanceToNextWaypoint = distanceToNext,
            remainingDistance = remainingDistance,
            remainingTime = remainingTime
        )

        viewModelScope.launch {
            _uiEvent.emit(NavigationUiEvent.WaypointReached(currentStepIndex))
        }
    }

    private fun onArrived() {
        val dest = _destination.value ?: return
        _navigationState.value = NavigationState.Arrived(dest)
        viewModelScope.launch { _uiEvent.emit(NavigationUiEvent.Arrived) }
    }

    private fun isOffRoute(location: CampusLocation): Boolean {
        val route = _route.value ?: return false
        val minDistance = route.waypoints.minOfOrNull { waypoint ->
            calculateDistance(location, waypoint.location)
        } ?: return false
        return minDistance > 30.0
    }

    fun recalculateRoute() {
        val dest = _destination.value ?: return
        calculateRoute(dest)
    }

    fun stopNavigation() {
        navigationJob?.cancel()
        currentStepIndex = 0
        _navigationState.value = NavigationState.Idle
        _route.value = null
        _destination.value = null
        _alternativeRoutes.value = emptyList()

        viewModelScope.launch {
            _uiEvent.emit(NavigationUiEvent.NavigationStopped)
        }
    }

    fun switchToARMode() {
        val route = _route.value ?: return
        viewModelScope.launch {
            _uiEvent.emit(NavigationUiEvent.LaunchARMode(route))
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun calculateDistanceToWaypoint(waypointIndex: Int): Double {
        val location = _userLocation.value ?: return 0.0
        val route = _route.value ?: return 0.0
        val waypoint = route.waypoints.getOrNull(waypointIndex + 1) ?: return 0.0
        return calculateDistance(location, waypoint.location)
    }

    // 6. Ensure distance follows the path of connected nodes
    private fun calculateRemainingDistance(location: CampusLocation?, fromStepIndex: Int): Double {
        if (location == null) return 0.0
        val route = _route.value ?: return 0.0

        var distance = 0.0

        // Distance from current GPS location to the upcoming node/waypoint
        val nextWaypoint = route.waypoints.getOrNull(fromStepIndex + 1)
        if (nextWaypoint != null) {
            distance += calculateDistance(location, nextWaypoint.location)
        }

        // Add the exact distance of all remaining steps (following the A* connected nodes)
        // rather than taking a straight-line displacement to the destination.
        for (i in (fromStepIndex + 1) until route.steps.size) {
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

    override fun onCleared() {
        super.onCleared()
        navigationJob?.cancel()
        locationTrackingJob?.cancel()
    }
}

/**
 * Navigation UI Events
 */
sealed class NavigationUiEvent {
    object NavigationStarted : NavigationUiEvent()
    object NavigationStopped : NavigationUiEvent()
    data class WaypointReached(val index: Int) : NavigationUiEvent()
    object Arrived : NavigationUiEvent()
    object OffRoute : NavigationUiEvent()
    object RouteRecalculated : NavigationUiEvent()
    data class LaunchARMode(val route: Route) : NavigationUiEvent()
    data class ShowError(val message: String) : NavigationUiEvent()
}