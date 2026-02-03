package com.campus.arnav.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.data.model.Route
import com.campus.arnav.data.repository.CampusRepository
import com.campus.arnav.data.repository.LocationRepository
import com.campus.arnav.data.repository.NavigationRepository
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
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val campusRepository: CampusRepository,
    private val locationRepository: LocationRepository,
    private val navigationRepository: NavigationRepository
) : ViewModel() {

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

    private var locationJob: Job? = null
    private var navigationJob: Job? = null
    private var currentStepIndex = 0

    init {
        loadBuildings()
        startLocationUpdates()
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

    fun onBuildingSelected(building: Building) {
        _selectedBuilding.value = building
        calculateRouteToBuilding(building)
    }

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
                val route = navigationRepository.calculateRoute(currentLocation, building.location)

                if (route != null) {
                    _activeRoute.value = route
                    _navigationState.value = NavigationState.Previewing(
                        destination = building,
                        route = route
                    )
                } else {
                    _uiEvent.emit(MapUiEvent.ShowError("Unable to calculate route"))
                }
            } catch (e: Exception) {
                _uiEvent.emit(MapUiEvent.ShowError("Error calculating route: ${e.message}"))
            }

            _isLoading.value = false
        }
    }

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
        _selectedBuilding.value = null

        viewModelScope.launch {
            _uiEvent.emit(MapUiEvent.NavigationStopped)
        }
    }

    fun recalculateRoute() {
        val building = _selectedBuilding.value ?: return
        calculateRouteToBuilding(building)
    }

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

    private fun isOffRoute(location: CampusLocation): Boolean {
        val route = _activeRoute.value ?: return false

        val minDistance = route.waypoints.minOfOrNull { waypoint ->
            calculateDistance(location, waypoint.location)
        } ?: return false

        return minDistance > 30.0
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
        _navigationState.value = NavigationState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
        navigationJob?.cancel()
    }
}

/**
 * Map UI Events
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