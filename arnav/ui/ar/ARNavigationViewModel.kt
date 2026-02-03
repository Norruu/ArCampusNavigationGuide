package com.campus.arnav.ui.ar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.data.model.NavigationStep
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
class ARNavigationViewModel @Inject constructor(
    private val campusRepository: CampusRepository,
    private val locationRepository: LocationRepository,
    private val navigationRepository: NavigationRepository
) : ViewModel() {

    private val _navigationState = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    private val _currentRoute = MutableStateFlow<Route?>(null)
    val currentRoute: StateFlow<Route?> = _currentRoute.asStateFlow()

    private val _userLocation = MutableStateFlow<CampusLocation?>(null)
    val userLocation: StateFlow<CampusLocation?> = _userLocation.asStateFlow()

    private val _userHeading = MutableStateFlow(0f)
    val userHeading: StateFlow<Float> = _userHeading.asStateFlow()

    private val _uiEvent = MutableSharedFlow<ARUiEvent>()
    val uiEvent: SharedFlow<ARUiEvent> = _uiEvent.asSharedFlow()

    private var locationJob: Job? = null
    private var currentStepIndex = 0

    init {
        startLocationUpdates()
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

    fun setRoute(route: Route) {
        _currentRoute.value = route
        val firstStep = route.steps.firstOrNull() ?: return

        currentStepIndex = 0
        _navigationState.value = NavigationState.Navigating(
            route = route,
            currentStep = firstStep,
            currentStepIndex = 0,
            distanceToNextWaypoint = firstStep.distance,
            remainingDistance = route.totalDistance,
            remainingTime = route.estimatedTime
        )
    }

    fun updateHeading(heading: Float) {
        _userHeading.value = heading
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
    }

    private fun advanceToNextStep(state: NavigationState.Navigating) {
        val route = state.route
        currentStepIndex++

        if (currentStepIndex >= route.steps.size) {
            onArrived(state)
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
            _uiEvent.emit(ARUiEvent.WaypointReached(currentStepIndex))
        }
    }

    private fun onArrived(state: NavigationState.Navigating) {
        // Get destination from route waypoints
        viewModelScope.launch {
            _uiEvent.emit(ARUiEvent.Arrived)
        }
        _navigationState.value = NavigationState.Idle
    }

    fun stopNavigation() {
        _navigationState.value = NavigationState.Idle
        _currentRoute.value = null
        currentStepIndex = 0
    }

    private fun calculateRemainingDistance(location: CampusLocation?): Double {
        if (location == null) return 0.0
        val route = _currentRoute.value ?: return 0.0

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

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
    }
}

sealed class ARUiEvent {
    data class WaypointReached(val index: Int) : ARUiEvent()
    object Arrived : ARUiEvent()
    data class ShowError(val message: String) : ARUiEvent()
}