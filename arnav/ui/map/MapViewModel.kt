package com.campus.arnav.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.Direction
import com.campus.arnav.data.model.CampusLocation
import com.campus.arnav.data.model.Route
import com.campus.arnav.data.model.BuildingType
import com.campus.arnav.data.model.Waypoint
import com.campus.arnav.data.model.WaypointType
import com.campus.arnav.data.model.NavigationStep
import com.campus.arnav.data.repository.CampusRepository
import com.campus.arnav.data.repository.LocationRepository
import com.campus.arnav.data.repository.NavigationRepository
import com.campus.arnav.domain.graph.UnifiedGraphManager
import com.campus.arnav.domain.graph.UnifiedPathfinder
import com.campus.arnav.domain.graph.UnifiedRouteOptions
import com.campus.arnav.domain.graph.UnifiedRouteResult
import com.campus.arnav.domain.pathfinding.CampusPathfinding
import com.campus.arnav.domain.pathfinding.RouteOptions
import com.campus.arnav.domain.pathfinding.RouteResult
import com.campus.arnav.ui.navigation.NavigationState
import com.campus.arnav.util.NavigationFeedbackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import javax.inject.Inject
import kotlin.math.*

enum class TransportMode(val speedMetersPerSecond: Double) {
    WALKING(1.4),
    VEHICLE(6.9)
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val campusRepository: CampusRepository,
    private val locationRepository: LocationRepository,
    private val navigationRepository: NavigationRepository,
    private val campusPathfinding: CampusPathfinding,
    private val feedbackManager: NavigationFeedbackManager,
    // ── Unified graph (NEW) ───────────────────────────────────────────────────
    private val unifiedGraphManager: UnifiedGraphManager,
    private val unifiedPathfinder: UnifiedPathfinder
) : ViewModel() {

    // ── State ─────────────────────────────────────────────────────────────────

    private val _buildings          = MutableStateFlow<List<Building>>(emptyList())
    val buildings: StateFlow<List<Building>> = _buildings.asStateFlow()

    private val _selectedBuilding   = MutableStateFlow<Building?>(null)
    val selectedBuilding: StateFlow<Building?> = _selectedBuilding.asStateFlow()

    private val _activeRoute        = MutableStateFlow<Route?>(null)
    val activeRoute: StateFlow<Route?> = _activeRoute.asStateFlow()

    private val _navigationState    = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    private val _userLocation       = MutableStateFlow<CampusLocation?>(null)
    val userLocation: StateFlow<CampusLocation?> = _userLocation.asStateFlow()

    private val _isFollowingUser    = MutableStateFlow(true)
    val isFollowingUser: StateFlow<Boolean> = _isFollowingUser.asStateFlow()

    private val _isLoading          = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiEvent            = MutableSharedFlow<MapUiEvent>()
    val uiEvent: SharedFlow<MapUiEvent> = _uiEvent.asSharedFlow()

    private val _routePoints        = MutableStateFlow<List<GeoPoint>?>(null)
    val routePoints: StateFlow<List<GeoPoint>?> = _routePoints.asStateFlow()

    private val _destinationConnector = MutableStateFlow<Pair<GeoPoint, GeoPoint>?>(null)
    val destinationConnector: StateFlow<Pair<GeoPoint, GeoPoint>?> = _destinationConnector.asStateFlow()

    private val _isSatelliteView    = MutableStateFlow(false)
    val isSatelliteView: StateFlow<Boolean> = _isSatelliteView.asStateFlow()

    private val _isCompassMode      = MutableStateFlow(false)
    val isCompassMode: StateFlow<Boolean> = _isCompassMode.asStateFlow()

    private val _transportMode      = MutableStateFlow(TransportMode.WALKING)
    val transportMode: StateFlow<TransportMode> = _transportMode.asStateFlow()

    private val _snappedLocation = MutableStateFlow<GeoPoint?>(null)
    val snappedLocation: StateFlow<GeoPoint?> = _snappedLocation.asStateFlow()

    // ── Internal ──────────────────────────────────────────────────────────────

    private var locationJob:  Job? = null
    private var navigationJob: Job? = null
    private var currentStepIndex = 0
    private var currentCategory: BuildingType? = null
    private var allBuildingsCache: List<Building> = emptyList()
    private var lastLocationUpdate = 0L
    private var lastLocation: CampusLocation? = null
    private val SNAP_REDRAW_THRESHOLD_METRES   = 3.0
    private var lastSnapLocation: CampusLocation? = null
    private val DIRECT_ROUTING_THRESHOLD_METRES = 50.0

    // ─────────────────────────────────────────────────────────────────────────
    // Init
    // ─────────────────────────────────────────────────────────────────────────

    init {
        loadBuildings()
        startLocationUpdates()
        initializePathfinding()
    }

    /**
     * FIX: MapViewModel no longer calls unifiedGraphManager.buildFromHardcodedPaths().
     *
     * Why removed?  FirestoreSyncManager.startSync() already calls it in a
     * sequenced coroutine BEFORE AdminRoadSyncAdapter.startSync(), guaranteeing
     * the correct build order:
     *
     *   buildFromHardcodedPaths()  →  adminRoadSyncAdapter.startSync()
     *
     * If MapViewModel also called buildFromHardcodedPaths(), two things could go wrong:
     *   a) It races with FirestoreSyncManager and wipes admin roads just added.
     *   b) It builds the graph before Firestore roads arrive, then never rebuilds.
     *
     * The isBuilt guard in UnifiedGraphManager makes the second call a no-op,
     * but calling it from here is still wrong because it could run BEFORE
     * FirestoreSyncManager has a chance to complete step 1 and move to step 2.
     *
     * MapViewModel only needs to initialise the legacy CampusPathfinding engine
     * (used as the OSM hybrid fallback for routes outside the campus boundary).
     */
    private fun initializePathfinding() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Legacy engine — still needed for HybridCampusPathfinding (OSM outside campus)
                campusPathfinding.initializeFromCampusPaths()
                android.util.Log.d("MapViewModel", "Legacy CampusPathfinding initialised")
            } catch (e: Exception) {
                android.util.Log.e("MapViewModel", "Legacy pathfinding init failed: ${e.message}")
            }
        }
        // UnifiedGraphManager is built and kept up-to-date by FirestoreSyncManager.
        // We just watch its isReady flag before routing.
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Buildings
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadBuildings() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                allBuildingsCache = campusRepository.getAllBuildings()
                _buildings.value  = allBuildingsCache
            } catch (e: Exception) {
                _uiEvent.emit(MapUiEvent.ShowError("Failed to load buildings"))
            }
            _isLoading.value = false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Location
    // ─────────────────────────────────────────────────────────────────────────

    private fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationRepository.locationUpdates
                .catch { e: Throwable ->
                    android.util.Log.e("MapViewModel", "Location error: ${e.message}")
                }
                .collect { location ->
                    _userLocation.value = location

                    val state = _navigationState.value
                    if (state is NavigationState.Navigating) {
                        val now  = System.currentTimeMillis()
                        val dist = lastLocation?.let { calculateDistance(it, location) } ?: 100.0
                        if (now - lastLocationUpdate > 3000 || dist > 5.0) {
                            lastLocationUpdate = now
                            lastLocation       = location
                            try { updateNavigationProgress(location, state) }
                            catch (e: Exception) {
                                android.util.Log.e("MapViewModel", "Nav progress error: ${e.message}")
                            }
                        }
                        val snapDist = lastSnapLocation?.let { calculateDistance(it, location) } ?: Double.MAX_VALUE
                        if (snapDist >= SNAP_REDRAW_THRESHOLD_METRES) {
                            lastSnapLocation = location
                            trimRouteFromCurrentPosition(location)
                        }
                    }
                }
        }
    }

    private fun trimRouteFromCurrentPosition(location: CampusLocation) {
        val points = _routePoints.value?.toMutableList() ?: return
        if (points.size < 2) return
        val userGeo = GeoPoint(location.latitude, location.longitude)

        if (_activeRoute.value?.id?.startsWith("direct") == true || points.size == 2) {
            _routePoints.value = listOf(userGeo, points.last()); return
        }

        val nearestIdx = points.indices.minByOrNull { i ->
            val p = points[i]
            (p.latitude - userGeo.latitude).pow(2) + (p.longitude - userGeo.longitude).pow(2)
        } ?: 0

        _routePoints.value = buildList {
            add(userGeo)
            addAll(points.subList(nearestIdx.coerceAtLeast(1), points.size))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Routing — unified graph is primary, legacy is fallback
    // ─────────────────────────────────────────────────────────────────────────

    fun onBuildingSelected(building: Building) {
        val state = _navigationState.value
        if (state is NavigationState.Navigating) return
        if (state is NavigationState.Previewing && state.destination.id == building.id) return

        _selectedBuilding.value = building
        _buildings.value        = listOf(building)
        _navigationState.value  = NavigationState.Previewing(building, null)
        calculateRouteToBuilding(building)
    }

    private fun calculateRouteToBuilding(building: Building) {
        viewModelScope.launch {
            _isLoading.value = true
            val loc = _userLocation.value
            if (loc == null) {
                _uiEvent.emit(MapUiEvent.ShowError("Waiting for GPS location…"))
                _isLoading.value = false
                return@launch
            }

            val dist = calculateDistance(loc, building.location)
            if (dist <= DIRECT_ROUTING_THRESHOLD_METRES) {
                createDirectDisplacementRoute(loc, building, dist)
            } else {
                calculateSmartRoute(loc, building)
            }
            _isLoading.value = false
        }
    }

    /**
     * Route priority:
     *
     * 1. UnifiedPathfinder (hardcoded + admin roads merged into one graph).
     *    This is the primary engine and will follow admin-drawn roads.
     *
     * 2. CampusPathfinding (legacy, OSM hybrid for outside-campus segments).
     *    Fallback when unified graph returns no route.
     *
     * 3. NavigationRepository (straight-line calculation).
     *    Last graph-based fallback.
     *
     * 4. Direct displacement line.
     *    Absolute last resort.
     */
    private suspend fun calculateSmartRoute(loc: CampusLocation, building: Building) {
        val start = GeoPoint(loc.latitude, loc.longitude)
        val end   = GeoPoint(building.location.latitude, building.location.longitude)

        // ── 1. Unified graph ──────────────────────────────────────────────────
        //
        // We check isReady so we don't attempt routing on an empty graph and
        // immediately fall through to legacy, masking the unified graph entirely.
        if (unifiedGraphManager.isReady) {
            val options = UnifiedRouteOptions(
                walkingSpeedMps = _transportMode.value.speedMetersPerSecond
            )
            when (val r = unifiedPathfinder.findRoute(start, end, options)) {
                is UnifiedRouteResult.Success -> {
                    handleRouteSuccess(r.route, building)
                    return
                }
                is UnifiedRouteResult.NoRoute ->
                    android.util.Log.w("MapViewModel", "Unified: ${r.reason}")
                is UnifiedRouteResult.Error   ->
                    android.util.Log.e("MapViewModel", "Unified error: ${r.message}")
            }
        } else {
            android.util.Log.w("MapViewModel", "Unified graph not ready yet — using legacy fallback")
        }

        // ── 2. Legacy CampusPathfinding ───────────────────────────────────────
        val entrance   = building.entrances.firstOrNull()
            ?.let { GeoPoint(it.latitude, it.longitude) } ?: end
        val legacyOpts = RouteOptions(walkingSpeed = _transportMode.value.speedMetersPerSecond)

        when (val r = campusPathfinding.findRoute(start, entrance, end, legacyOpts)) {
            is RouteResult.Success      -> { handleRouteSuccess(r.route, building); return }
            is RouteResult.NoRouteFound -> android.util.Log.w("MapViewModel", "Legacy: ${r.message}")
            is RouteResult.Error        -> android.util.Log.e("MapViewModel", "Legacy error: ${r.message}")
        }

        // ── 3. Simple repository route ────────────────────────────────────────
        calculateSimpleRoute(loc, building)
    }

    private suspend fun calculateSimpleRoute(loc: CampusLocation, building: Building) {
        when (val r = navigationRepository.calculateRoute(loc, building.location)) {
            is RouteResult.Success      -> handleRouteSuccess(r.route, building)
            is RouteResult.Error,
            is RouteResult.NoRouteFound -> createDirectDisplacementRoute(loc, building, calculateDistance(loc, building.location))
        }
    }

    private fun createDirectDisplacementRoute(start: CampusLocation, building: Building, distance: Double) {
        val speed = _transportMode.value.speedMetersPerSecond
        handleRouteSuccess(Route(
            id            = "direct_${System.currentTimeMillis()}",
            origin        = start,
            destination   = building.location,
            waypoints     = listOf(
                Waypoint(start,            WaypointType.START),
                Waypoint(building.location, WaypointType.END)
            ),
            steps         = listOf(NavigationStep(
                instruction   = "Walk directly to ${building.name}",
                distance      = distance,
                direction     = Direction.FORWARD,
                startLocation = start,
                endLocation   = building.location
            )),
            totalDistance = distance,
            estimatedTime = (distance / speed).toLong()
        ), building)
    }

    private fun handleRouteSuccess(route: Route, building: Building) {
        val speed   = _transportMode.value.speedMetersPerSecond
        val adjusted = route.copy(estimatedTime = (route.totalDistance / speed).toLong())
        val improved = generateRouteInstructions(adjusted)

        _activeRoute.value          = improved
        _routePoints.value          = improved.waypoints.map { GeoPoint(it.location.latitude, it.location.longitude) }
        _destinationConnector.value = null
        _navigationState.value      = NavigationState.Previewing(building, improved)
    }

    private fun generateRouteInstructions(route: Route): Route {
        if (route.steps.size < 2) return route
        val steps = route.steps.toMutableList()
        for (i in steps.indices) {
            when {
                i == 0                  -> {}
                i == steps.lastIndex    -> steps[i] = steps[i].copy(instruction = "Arrive at destination")
                else                    -> steps[i] = steps[i].copy(instruction = directionToInstruction(steps[i].direction))
            }
        }
        return route.copy(steps = steps)
    }

    fun setSnappedLocation(latitude: Double, longitude: Double) {
        _snappedLocation.value = GeoPoint(latitude, longitude)
    }

    fun setSnappedLocation(point: GeoPoint?) {
        _snappedLocation.value = point
    }

    fun onOffRouteDetected(gapMeters: Float) {
        // Keep this lightweight; UI event emission only.
        viewModelScope.launch {
            _uiEvent.emit(MapUiEvent.OffRoute)
        }
    }

    private fun directionToInstruction(d: Direction) = when (d) {
        Direction.FORWARD      -> "Go straight"
        Direction.SLIGHT_LEFT  -> "Slight left"
        Direction.LEFT         -> "Turn left"
        Direction.SHARP_LEFT   -> "Sharp left"
        Direction.SLIGHT_RIGHT -> "Slight right"
        Direction.RIGHT        -> "Turn right"
        Direction.SHARP_RIGHT  -> "Sharp right"
        Direction.U_TURN       -> "Make a U-turn"
        Direction.ARRIVE       -> "Arrive at destination"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Transport mode
    // ─────────────────────────────────────────────────────────────────────────

    fun setTransportMode(mode: TransportMode) {
        _transportMode.value = mode
        val route = _activeRoute.value ?: return
        val updated = route.copy(estimatedTime = (route.totalDistance / mode.speedMetersPerSecond).toLong())
        _activeRoute.value = updated
        val state = _navigationState.value
        when (state) {
            is NavigationState.Navigating  -> _navigationState.value = state.copy(
                route = updated,
                remainingTime = (state.remainingDistance / mode.speedMetersPerSecond).toLong()
            )
            is NavigationState.Previewing  -> _navigationState.value = state.copy(route = updated)
            else -> {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation control
    // ─────────────────────────────────────────────────────────────────────────

    fun startNavigation() {
        val route    = _activeRoute.value     ?: return
        val building = _selectedBuilding.value ?: return
        currentStepIndex = 0
        val first = route.steps.first()
        feedbackManager.speak("Starting navigation to ${building.name}. ${first.instruction}")
        _navigationState.value = NavigationState.Navigating(
            route = route, currentStep = first, currentStepIndex = 0,
            distanceToNextWaypoint = first.distance,
            remainingDistance = route.totalDistance, remainingTime = route.estimatedTime
        )
        viewModelScope.launch { _uiEvent.emit(MapUiEvent.NavigationStarted) }
    }

    fun stopNavigation() {
        navigationJob?.cancel()
        filterBuildingsByCategory(currentCategory)
        _selectedBuilding.value = null
        _activeRoute.value = null
        _routePoints.value = null
        _destinationConnector.value = null
        _snappedLocation.value = null
        _navigationState.value = NavigationState.Idle
        lastLocationUpdate = 0
        lastLocation = null
        lastSnapLocation = null
        currentStepIndex = 0
        viewModelScope.launch { _uiEvent.emit(MapUiEvent.NavigationStopped) }
    }

    fun recalculateRoute() {
        _selectedBuilding.value?.let { calculateRouteToBuilding(it) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation progress (unchanged logic)
    // ─────────────────────────────────────────────────────────────────────────

    private fun updateNavigationProgress(loc: CampusLocation, state: NavigationState.Navigating) {
        val route = state.route
        val speed = _transportMode.value.speedMetersPerSecond
        val distToFinish = route.waypoints.lastOrNull()?.location?.let { calculateDistance(loc, it) } ?: 0.0

        if (distToFinish < 10.0 && distToFinish > 0.0) {
            feedbackManager.vibrateForArrival()
            feedbackManager.speak("You have arrived at your destination.")
            onArrived(); return
        }

        if (distToFinish <= DIRECT_ROUTING_THRESHOLD_METRES && !route.id.startsWith("direct")) {
            _selectedBuilding.value?.let { building ->
                val step = NavigationStep("Walk directly to ${building.name}", distToFinish,
                    Direction.FORWARD, loc, building.location)
                val direct = Route(
                    id = "direct_${System.currentTimeMillis()}", origin = loc,
                    destination = building.location,
                    waypoints = listOf(Waypoint(loc, WaypointType.START), Waypoint(building.location, WaypointType.END)),
                    steps = listOf(step), totalDistance = distToFinish,
                    estimatedTime = (distToFinish / speed).toLong()
                )
                _routePoints.value = listOf(
                    GeoPoint(loc.latitude, loc.longitude),
                    GeoPoint(building.location.latitude, building.location.longitude)
                )
                _activeRoute.value = direct
                _navigationState.value = state.copy(route = direct, currentStep = step,
                    currentStepIndex = 0, distanceToNextWaypoint = distToFinish,
                    remainingDistance = distToFinish, remainingTime = direct.estimatedTime)
                return
            }
        }

        if (isOffRoute(loc, route)) {
            viewModelScope.launch { _uiEvent.emit(MapUiEvent.OffRoute) }; return
        }

        route.waypoints.getOrNull(currentStepIndex + 1)?.let { next ->
            val distToNext = calculateDistance(loc, next.location)
            if (distToNext < 10.0) advanceToNextStep(state)
            else _navigationState.value = state.copy(
                distanceToNextWaypoint = distToNext,
                remainingDistance = distToFinish,
                remainingTime = (distToFinish / speed).toLong()
            )
        }
    }

    private fun advanceToNextStep(state: NavigationState.Navigating) {
        currentStepIndex++
        if (currentStepIndex >= state.route.steps.size) { onArrived(); return }
        val next = state.route.steps[currentStepIndex]
        feedbackManager.vibrateForTurn(); feedbackManager.speak(next.instruction)
        _navigationState.value = state.copy(currentStep = next, currentStepIndex = currentStepIndex,
            distanceToNextWaypoint = next.distance)
        viewModelScope.launch { _uiEvent.emit(MapUiEvent.WaypointReached(currentStepIndex)) }
    }

    private fun onArrived() {
        val building = _selectedBuilding.value
        if (building != null) {
            _navigationState.value = NavigationState.Arrived(building)
            viewModelScope.launch { _uiEvent.emit(MapUiEvent.ShowArrivalDialog(building.name)) }
        } else {
            _navigationState.value = NavigationState.Idle
        }
    }

    private fun isOffRoute(loc: CampusLocation, route: Route) =
        (route.waypoints.minOfOrNull { calculateDistance(loc, it.location) } ?: 0.0) > 40.0

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    fun filterBuildingsByCategory(type: BuildingType?) {
        currentCategory  = type
        _buildings.value = if (type == null) allBuildingsCache
        else allBuildingsCache.filter { it.type == type }
        val state = _navigationState.value
        if (state !is NavigationState.Navigating && state !is NavigationState.Previewing) {
            _selectedBuilding.value = null; _navigationState.value = NavigationState.Idle
        }
    }

    fun selectBuildingById(id: String) {
        allBuildingsCache.find { it.id == id }?.let { onSearchResultClicked(it) }
    }

    fun onSearchResultClicked(building: Building) {
        onBuildingSelected(building)
        viewModelScope.launch {
            _uiEvent.emit(MapUiEvent.MoveCameraTo(GeoPoint(building.location.latitude, building.location.longitude)))
        }
    }

    fun switchToARMode() {
        val route = _activeRoute.value ?: return
        viewModelScope.launch {
            _uiEvent.emit(MapUiEvent.LaunchARNavigation(route, _selectedBuilding.value?.name ?: "Destination"))
        }
    }

    fun openSearch()                     { viewModelScope.launch { _uiEvent.emit(MapUiEvent.OpenSearch) } }
    fun setFollowingUser(v: Boolean)     { _isFollowingUser.value = v  }
    fun setSatelliteView(v: Boolean)     { _isSatelliteView.value = v  }
    fun setCompassMode(v: Boolean)       { _isCompassMode.value   = v  }

    private fun calculateDistance(p1: CampusLocation, p2: CampusLocation): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(p2.latitude  - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat/2).pow(2) + cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) * sin(dLon/2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel(); navigationJob?.cancel(); feedbackManager.shutdown()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI Events
// ─────────────────────────────────────────────────────────────────────────────

sealed class MapUiEvent {
    object OpenSearch        : MapUiEvent()
    object NavigationStarted : MapUiEvent()
    object NavigationStopped : MapUiEvent()
    object OffRoute          : MapUiEvent()
    data class ShowArrivalDialog(val buildingName: String)                       : MapUiEvent()
    data class WaypointReached(val index: Int)                                   : MapUiEvent()
    data class LaunchARNavigation(val route: Route, val destinationName: String) : MapUiEvent()
    data class ShowError(val message: String)                                    : MapUiEvent()
    data class MoveCameraTo(val location: GeoPoint)                              : MapUiEvent()
}