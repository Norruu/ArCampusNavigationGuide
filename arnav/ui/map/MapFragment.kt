package com.campus.arnav.ui.map

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import org.osmdroid.views.overlay.Polygon
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.campus.arnav.R
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.BuildingType
import com.campus.arnav.data.model.Direction
import com.campus.arnav.data.model.Route
import com.campus.arnav.databinding.FragmentMapBinding
import com.campus.arnav.service.NavigationService
import com.campus.arnav.ui.MainActivity
import com.campus.arnav.ui.ar.ARNavigationActivity
import com.campus.arnav.ui.map.components.CampusPathsOverlay
import com.campus.arnav.ui.map.components.CustomMarkerOverlay
import com.campus.arnav.ui.map.components.DestinationConnectorOverlay
import com.campus.arnav.ui.map.components.OffRoutePolyline
import com.campus.arnav.ui.navigation.NavigationState
import com.campus.arnav.util.FirestoreSyncManager
import com.campus.arnav.util.MapCompassManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
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
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import javax.inject.Inject

@AndroidEntryPoint
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MapViewModel by viewModels()

    @Inject lateinit var firestoreSyncManager: FirestoreSyncManager

    private lateinit var mapView: MapView
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private lateinit var routePolyline: Polyline

    private val buildingMarkers = mutableListOf<CustomMarkerOverlay>()
    private val poiMarkers = mutableListOf<Marker>()
    private val geofencePolygons = mutableListOf<Polygon>()
    private lateinit var campusPathsOverlay: CampusPathsOverlay

    private var isDarkMode = false
    private lateinit var mapCompassManager: MapCompassManager

    // Drag Variables
    private var startY = 0f
    private var startHeight = 0
    private var maxPanelHeight = 0

    // Polyline overlays
    private val offRoutePolyline = OffRoutePolyline()
    private val destinationConnectorOverlay = DestinationConnectorOverlay()

    private var isWalkingMode = true

    // Search
    private var etMapSearch: EditText? = null
    private var btnMapClear: ImageView? = null
    private var searchDropdown: MaterialCardView? = null
    private var rvMapSearchResults: RecyclerView? = null
    private lateinit var mapSearchAdapter: MapSearchAdapter
    private var allBuildingsForSearch: List<Building> = emptyList()

    private val roadPolylines = mutableListOf<Polyline>()

    private val mainActivity get() = activity as? MainActivity

    private val arNavigationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val shouldCancel = result.data?.getBooleanExtra("CANCEL_NAVIGATION", false) == true
            if (shouldCancel) {
                viewModel.stopNavigation()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainActivity?.setBottomNavVisibility(false)

        configureOSMDroid()
        campusPathsOverlay = CampusPathsOverlay()
        mapCompassManager = MapCompassManager(requireContext())

        setupMap()
        setupCampusMap(mapView)

        if (!mapView.overlays.contains(routePolyline)) {
            mapView.overlays.add(routePolyline)
        }
        mapView.overlays.add(offRoutePolyline)
        mapView.overlays.add(destinationConnectorOverlay)

        setupLocationOverlay()
        setupUI()
        observeViewModel()

        // Setup map search
        etMapSearch = view.findViewById(R.id.etMapSearch)
        btnMapClear = view.findViewById(R.id.btnMapClearSearch)
        searchDropdown = view.findViewById(R.id.searchDropdown)
        rvMapSearchResults = view.findViewById(R.id.rvMapSearchResults)
        setupMapSearch()

        loadFirestoreMarkers()
        loadFirestoreGeofences()

        handleNavigationFromDashboard()

        setFragmentResultListener("search_request") { _, bundle ->
            val buildingId = bundle.getString("building_id")
            if (buildingId != null) viewModel.selectBuildingById(buildingId)
        }
    }

    private fun handleNavigationFromDashboard() {
        val buildingId = arguments?.getString("navigateToBuildingId")
        if (buildingId != null) {
            viewModel.selectBuildingById(buildingId)

            viewLifecycleOwner.lifecycleScope.launch {
                delay(1500)
                val buildings = viewModel.buildings.value
                val building = buildings.find { it.id == buildingId }
                if (building != null) {
                    val geoPoint = GeoPoint(building.location.latitude, building.location.longitude)
                    mapView.controller.animateTo(geoPoint, 18.5, 1500L)
                }
            }

            arguments?.remove("navigateToBuildingId")
        }
    }

    // ===== MAP SEARCH =====
    private fun setupMapSearch() {
        mapSearchAdapter = MapSearchAdapter { building ->
            // Close search
            etMapSearch?.text?.clear()
            etMapSearch?.clearFocus()
            searchDropdown?.visibility = View.GONE
            btnMapClear?.visibility = View.GONE
            hideMapKeyboard()

            // Show all markers again
            showAllBuildingMarkers()

            // Select and navigate to the building
            viewModel.onBuildingSelected(building)

            // Zoom to it
            val geoPoint = GeoPoint(building.location.latitude, building.location.longitude)
            mapView.controller.animateTo(geoPoint, 18.5, 1500L)
        }

        rvMapSearchResults?.layoutManager = LinearLayoutManager(requireContext())
        rvMapSearchResults?.adapter = mapSearchAdapter

        etMapSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    btnMapClear?.visibility = View.VISIBLE
                    val filtered = allBuildingsForSearch.filter {
                        it.name.contains(query, ignoreCase = true) ||
                                it.shortName.contains(query, ignoreCase = true) ||
                                (it.description?.contains(query, ignoreCase = true) == true)
                    }
                    mapSearchAdapter.submitList(filtered)
                    searchDropdown?.visibility = if (filtered.isNotEmpty()) View.VISIBLE else View.GONE

                    // Filter markers on map
                    filterBuildingMarkers(filtered)
                } else {
                    btnMapClear?.visibility = View.GONE
                    mapSearchAdapter.submitList(emptyList())
                    searchDropdown?.visibility = View.GONE

                    // Show all markers
                    showAllBuildingMarkers()
                }
            }
        })

        btnMapClear?.setOnClickListener {
            etMapSearch?.text?.clear()
            etMapSearch?.clearFocus()
            searchDropdown?.visibility = View.GONE
            btnMapClear?.visibility = View.GONE
            hideMapKeyboard()
            showAllBuildingMarkers()
        }

        // Cache buildings for search
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.buildings.collectLatest { buildings ->
                allBuildingsForSearch = buildings
            }
        }
    }

    private fun filterBuildingMarkers(filtered: List<Building>) {
        buildingMarkers.forEach { marker ->
            val matches = filtered.any { it.id == marker.building.id }
            if (matches) {
                if (!mapView.overlays.contains(marker)) {
                    mapView.overlays.add(marker)
                }
            } else {
                mapView.overlays.remove(marker)
            }
        }
        mapView.invalidate()
    }

    private fun showAllBuildingMarkers() {
        buildingMarkers.forEach { marker ->
            if (!mapView.overlays.contains(marker)) {
                mapView.overlays.add(marker)
            }
        }
        mapView.invalidate()
    }

    private fun hideMapKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etMapSearch?.windowToken, 0)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupUI() {
        setupCategories()

        binding.navigationPanel.root.visibility = View.GONE
        setupActivePanelDrag()

        binding.fabSettings.setOnClickListener {
            findNavController().navigate(R.id.action_mapFragment_to_settingsFragment)
        }

        binding.fabSatellite.setOnClickListener {
            toggleMapLayer()
        }

        binding.fabToggleDashboard.setOnClickListener {
            findNavController().navigate(R.id.action_map_to_dashboard)
        }

        binding.fabRecenter.setOnClickListener {
            myLocationOverlay?.myLocation?.let { location ->
                mapView.controller.animateTo(location)
            }
            viewModel.setFollowingUser(true)
        }

        binding.fabCompass.setOnClickListener { toggleCompassMode() }
        binding.fabArMode.setOnClickListener { viewModel.switchToARMode() }

        binding.navigationPanel.btnStartNavigation.setOnClickListener {
            if (!Settings.canDrawOverlays(requireContext())) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")
                )
                startActivity(intent)
                Toast.makeText(requireContext(), "Please allow overlay permission for background navigation.", Toast.LENGTH_LONG).show()
            } else {
                viewModel.startNavigation()
            }
        }

        binding.navigationPanel.btnClosePanel.setOnClickListener { viewModel.stopNavigation() }
        binding.activeNavigationPanel.btnEndNavigation.setOnClickListener { viewModel.stopNavigation() }

        setupTransportToggle()
    }

    private fun setupTransportToggle() {
        binding.navigationPanel.btnWalking.setOnClickListener {
            if (!isWalkingMode) {
                isWalkingMode = true
                updateTransportUI()

                // 1. Send the mode selection to the ViewModel
                viewModel.setTransportMode(TransportMode.WALKING)
            }
        }

        binding.navigationPanel.btnVehicle.setOnClickListener {
            if (isWalkingMode) {
                isWalkingMode = false
                updateTransportUI()

                // 2. Send the mode selection to the ViewModel
                viewModel.setTransportMode(TransportMode.VEHICLE)
            }
        }
    }

    private fun updateTransportUI() {
        val primaryGreen = ContextCompat.getColor(requireContext(), R.color.primary_green)
        val white = Color.WHITE
        val transparent = Color.TRANSPARENT

        // Convert 2dp to pixels for border width
        val strokeWidthPx = (2 * resources.displayMetrics.density).toInt()

        if (isWalkingMode) {
            // WALKING ACTIVE
            binding.navigationPanel.btnWalking.backgroundTintList = ColorStateList.valueOf(primaryGreen)
            binding.navigationPanel.btnWalking.setTextColor(white)
            binding.navigationPanel.btnWalking.iconTint = ColorStateList.valueOf(white)
            binding.navigationPanel.btnWalking.strokeWidth = 0

            // VEHICLE INACTIVE
            binding.navigationPanel.btnVehicle.backgroundTintList = ColorStateList.valueOf(transparent)
            binding.navigationPanel.btnVehicle.setTextColor(primaryGreen)
            binding.navigationPanel.btnVehicle.iconTint = ColorStateList.valueOf(primaryGreen)
            binding.navigationPanel.btnVehicle.strokeColor = ColorStateList.valueOf(primaryGreen)
            binding.navigationPanel.btnVehicle.strokeWidth = strokeWidthPx

            routePolyline.outlinePaint.apply {
                strokeWidth = 16f
                pathEffect = null
            }
        } else {
            // VEHICLE ACTIVE
            binding.navigationPanel.btnVehicle.backgroundTintList = ColorStateList.valueOf(primaryGreen)
            binding.navigationPanel.btnVehicle.setTextColor(white)
            binding.navigationPanel.btnVehicle.iconTint = ColorStateList.valueOf(white)
            binding.navigationPanel.btnVehicle.strokeWidth = 0

            // WALKING INACTIVE
            binding.navigationPanel.btnWalking.backgroundTintList = ColorStateList.valueOf(transparent)
            binding.navigationPanel.btnWalking.setTextColor(primaryGreen)
            binding.navigationPanel.btnWalking.iconTint = ColorStateList.valueOf(primaryGreen)
            binding.navigationPanel.btnWalking.strokeColor = ColorStateList.valueOf(primaryGreen)
            binding.navigationPanel.btnWalking.strokeWidth = strokeWidthPx

            routePolyline.outlinePaint.apply {
                strokeWidth = 22f
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(30f, 15f), 0f)
            }
        }
        mapView.invalidate()
    }

    private fun setupCategories() {
        val chipGroup = binding.categoryChipGroup
        chipGroup.removeAllViews()

        val bgStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val bgColors = intArrayOf(
            ContextCompat.getColor(requireContext(), R.color.primary_green),
            Color.parseColor("#F0F0F0")
        )
        val chipBackgroundColor = ColorStateList(bgStates, bgColors)
        val contentColors = intArrayOf(Color.WHITE, Color.BLACK)
        val contentColorStateList = ColorStateList(bgStates, contentColors)

        fun applyStyle(chip: com.google.android.material.chip.Chip) {
            chip.isCheckable = true
            chip.isCheckedIconVisible = false
            chip.chipBackgroundColor = chipBackgroundColor
            chip.setTextColor(contentColorStateList)
            chip.chipIconTint = contentColorStateList
        }

        val allChip = com.google.android.material.chip.Chip(
            requireContext(), null, com.google.android.material.R.style.Widget_Material3_Chip_Filter
        )
        allChip.text = "All"
        applyStyle(allChip)
        allChip.isChecked = true
        allChip.setOnClickListener {
            viewModel.filterBuildingsByCategory(null)
            showAllMarkers()
        }
        chipGroup.addView(allChip)

        BuildingType.values().forEach { type ->
            val chip = com.google.android.material.chip.Chip(
                requireContext(), null, com.google.android.material.R.style.Widget_Material3_Chip_Filter
            )
            chip.text = type.name.lowercase().replaceFirstChar { it.uppercase() }
            chip.chipIcon = ContextCompat.getDrawable(requireContext(), getIconForBuildingType(type))
            chip.isChipIconVisible = true
            applyStyle(chip)
            chip.setOnClickListener {
                viewModel.filterBuildingsByCategory(type)
                showAllMarkers()
            }
            chipGroup.addView(chip)
        }

        loadCategoryChips(chipGroup, chipBackgroundColor, contentColorStateList)
    }

    private fun loadCategoryChips(
        chipGroup: com.google.android.material.chip.ChipGroup,
        chipBackgroundColor: ColorStateList,
        contentColorStateList: ColorStateList
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            delay(2500)
            addCategoryChips(chipGroup, chipBackgroundColor, contentColorStateList)
        }
    }

    private fun addCategoryChips(
        chipGroup: com.google.android.material.chip.ChipGroup,
        chipBackgroundColor: ColorStateList,
        contentColorStateList: ColorStateList
    ) {
        val categories = firestoreSyncManager.cachedCategories
        if (categories.isEmpty()) return

        categories.forEach { category ->
            val alreadyExists = (0 until chipGroup.childCount).any { i ->
                (chipGroup.getChildAt(i) as? com.google.android.material.chip.Chip)?.text == category.name
            }
            if (alreadyExists) return@forEach

            val chip = com.google.android.material.chip.Chip(
                requireContext(), null, com.google.android.material.R.style.Widget_Material3_Chip_Filter
            )
            chip.text = category.name
            chip.isCheckable = true
            chip.isCheckedIconVisible = false
            chip.chipBackgroundColor = chipBackgroundColor
            chip.setTextColor(contentColorStateList)
            chip.setOnClickListener { filterMarkersByCategory(category.id) }
            chipGroup.addView(chip)
        }
    }

    private fun filterMarkersByCategory(categoryId: String) {
        val allMarkers = firestoreSyncManager.cachedMarkers
        poiMarkers.forEach { mapView.overlays.remove(it) }
        poiMarkers.clear()

        val filtered = allMarkers.filter { it.categoryId == categoryId }
        filtered.forEach { firestoreMarker ->
            val marker = Marker(mapView)
            marker.position = GeoPoint(firestoreMarker.latitude, firestoreMarker.longitude)
            marker.title = firestoreMarker.title
            marker.snippet = firestoreMarker.description
            marker.icon = ContextCompat.getDrawable(requireContext(), getIconForMarkerType(firestoreMarker.iconType))
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.setOnMarkerClickListener { m, _ ->
                Toast.makeText(requireContext(), "${m.title}\n${m.snippet ?: ""}", Toast.LENGTH_LONG).show()
                true
            }
            poiMarkers.add(marker)
            mapView.overlays.add(marker)
        }
        viewModel.filterBuildingsByCategory(null)
        mapView.invalidate()
    }

    private fun showAllMarkers() {
        poiMarkers.forEach { mapView.overlays.remove(it) }
        poiMarkers.clear()

        val allMarkers = firestoreSyncManager.cachedMarkers
        val categories = firestoreSyncManager.cachedCategories

        allMarkers.forEach { firestoreMarker ->
            val marker = Marker(mapView)
            marker.position = GeoPoint(firestoreMarker.latitude, firestoreMarker.longitude)
            marker.title = firestoreMarker.title
            marker.snippet = firestoreMarker.description
            val category = categories.find { it.id == firestoreMarker.categoryId }
            marker.icon = ContextCompat.getDrawable(requireContext(), getIconForMarkerType(firestoreMarker.iconType))
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.setOnMarkerClickListener { m, _ ->
                val categoryName = category?.name ?: ""
                val info = if (categoryName.isNotEmpty()) "$categoryName\n${m.snippet ?: ""}" else (m.snippet ?: "")
                Toast.makeText(requireContext(), "${m.title}\n$info", Toast.LENGTH_LONG).show()
                true
            }
            poiMarkers.add(marker)
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }

    private fun loadFirestoreMarkers() {
        viewLifecycleOwner.lifecycleScope.launch {
            delay(2000)
            updatePOIMarkers()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                delay(5000)
                updatePOIMarkers()
            }
        }
    }

    private fun updatePOIMarkers() {
        val markers = firestoreSyncManager.cachedMarkers
        val categories = firestoreSyncManager.cachedCategories
        if (markers.size == poiMarkers.size && markers.isNotEmpty()) return

        poiMarkers.forEach { mapView.overlays.remove(it) }
        poiMarkers.clear()

        markers.forEach { firestoreMarker ->
            val marker = Marker(mapView)
            marker.position = GeoPoint(firestoreMarker.latitude, firestoreMarker.longitude)
            marker.title = firestoreMarker.title
            marker.snippet = firestoreMarker.description
            val category = categories.find { it.id == firestoreMarker.categoryId }
            marker.icon = ContextCompat.getDrawable(requireContext(), getIconForMarkerType(firestoreMarker.iconType))
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.setOnMarkerClickListener { m, _ ->
                val title = m.title ?: "Marker"
                val desc = m.snippet ?: ""
                val categoryName = category?.name ?: ""
                val info = if (categoryName.isNotEmpty()) "$categoryName\n$desc" else desc
                Toast.makeText(requireContext(), "$title\n$info", Toast.LENGTH_LONG).show()
                true
            }
            poiMarkers.add(marker)
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
        if (markers.isNotEmpty()) {
            android.util.Log.d("MapFragment", "Added ${markers.size} POI markers to map")
        }
    }

    private fun loadFirestoreGeofences() {
        viewLifecycleOwner.lifecycleScope.launch {
            delay(2500)
            updateGeofencePolygons()
            updateRoads()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                delay(5000)
                updateGeofencePolygons()
                updateRoads()
            }
        }
    }

    private fun updateGeofencePolygons() {
        val geofences = firestoreSyncManager.cachedGeofences
        if (geofences.size == geofencePolygons.size && geofences.isNotEmpty()) return

        geofencePolygons.forEach { mapView.overlays.remove(it) }
        geofencePolygons.clear()

        geofences.forEach { geofence ->
            val polygonOverlay = Polygon(mapView).apply {
                // If the admin created a polygon, draw it. Otherwise, fallback to the old circle.
                if (!geofence.polygonPoints.isNullOrEmpty()) {
                    val pts = geofence.polygonPoints.map {
                        GeoPoint(it["lat"] ?: 0.0, it["lng"] ?: 0.0)
                    }
                    points = pts
                } else {
                    val center = GeoPoint(geofence.latitude, geofence.longitude)
                    points = createCirclePoints(center, geofence.radius)
                }

                // Match the translucent green fill from the admin panel
                fillPaint.color = Color.argb(80, 45, 106, 79)
                outlinePaint.color = Color.argb(255, 45, 106, 79)
                outlinePaint.strokeWidth = 3f
                outlinePaint.isAntiAlias = true
            }
            geofencePolygons.add(polygonOverlay)
            mapView.overlays.add(polygonOverlay)
        }

        // Ensure the user's location dot stays on top of the geofence colors
        myLocationOverlay?.let {
            mapView.overlays.remove(it)
            mapView.overlays.add(it)
        }

        mapView.invalidate()
        if (geofences.isNotEmpty()) {
            android.util.Log.d("MapFragment", "Added ${geofences.size} geofence polygons to map")
        }
    }

    private fun createCirclePoints(center: GeoPoint, radiusMeters: Double): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        val earthRadius = 6371000.0
        for (i in 0..360 step 5) {
            val angle = Math.toRadians(i.toDouble())
            val lat = Math.asin(
                Math.sin(Math.toRadians(center.latitude)) * Math.cos(radiusMeters / earthRadius) +
                        Math.cos(Math.toRadians(center.latitude)) * Math.sin(radiusMeters / earthRadius) * Math.cos(angle)
            )
            val lon = Math.toRadians(center.longitude) + Math.atan2(
                Math.sin(angle) * Math.sin(radiusMeters / earthRadius) * Math.cos(Math.toRadians(center.latitude)),
                Math.cos(radiusMeters / earthRadius) - Math.sin(Math.toRadians(center.latitude)) * Math.sin(lat)
            )
            points.add(GeoPoint(Math.toDegrees(lat), Math.toDegrees(lon)))
        }
        return points
    }

    private fun updateRoads() {
        val roads = firestoreSyncManager.cachedRoads

        roadPolylines.forEach { mapView.overlays.remove(it) }
        roadPolylines.clear()

        roads.forEach { road ->
            if (road.roadNodes.isNotEmpty()) {
                val polyline = Polyline(mapView).apply {
                    outlinePaint.color = Color.parseColor("#42B89F") // Admin Theme Green
                    outlinePaint.strokeWidth = 10f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    setPoints(road.roadNodes.map { GeoPoint(it["lat"] ?: 0.0, it["lng"] ?: 0.0) })
                }
                roadPolylines.add(polyline)
                mapView.overlays.add(0, polyline) // Draw at bottom layer
            }
        }
        mapView.invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupActivePanelDrag() {
        binding.activeNavigationPanel.dragHandleContainer.setOnTouchListener { view, event ->
            val container = binding.activeNavigationPanel.infoContainer
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
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
                    startY = event.rawY
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
                    animator.addUpdateListener {
                        container.layoutParams.height = it.animatedValue as Int
                        container.requestLayout()
                    }
                    animator.start()
                    if (!shouldOpen) {
                        view.postDelayed({ if (container.layoutParams.height == 0) container.visibility = View.GONE }, 250)
                    } else {
                        view.postDelayed({ container.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT }, 250)
                    }
                    view.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch { viewModel.buildings.collectLatest { updateBuildingMarkers(it) } }
        viewLifecycleOwner.lifecycleScope.launch { viewModel.activeRoute.collectLatest { route -> if (route != null) displayRoute(route) else clearRoute() } }
        viewLifecycleOwner.lifecycleScope.launch { viewModel.navigationState.collectLatest { handleNavigationState(it) } }
        viewLifecycleOwner.lifecycleScope.launch { viewModel.uiEvent.collectLatest { handleUiEvent(it) } }
        viewLifecycleOwner.lifecycleScope.launch { viewModel.isSatelliteView.collectLatest { isSat -> if (isSat) enableSatellite() else disableSatellite() } }
        viewLifecycleOwner.lifecycleScope.launch { viewModel.isCompassMode.collectLatest { isCompass -> if (isCompass) startCompassMode() else stopCompassMode() } }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.userLocation.collectLatest { location ->
                val routePts = viewModel.routePoints.value
                val user = location?.let { GeoPoint(it.latitude, it.longitude) }
                offRoutePolyline.update(user, routePts)
                mapView.invalidate()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.routePoints.collectLatest { routePts ->
                val location = viewModel.userLocation.value
                val user = location?.let { GeoPoint(it.latitude, it.longitude) }
                offRoutePolyline.update(user, routePts)
                if (routePts != null) routePolyline.setPoints(routePts)
                mapView.invalidate()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.destinationConnector.collectLatest { connector ->
                if (connector != null) destinationConnectorOverlay.set(connector.first, connector.second)
                else destinationConnectorOverlay.clear()
                mapView.invalidate()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                if (isLoading) {
                    binding.loadingOverlay.apply {
                        alpha = 0f
                        visibility = View.VISIBLE
                        animate().alpha(1f).setDuration(200).start()
                    }
                } else {
                    binding.loadingOverlay.animate().alpha(0f).setDuration(200).withEndAction {
                        binding.loadingOverlay.visibility = View.GONE
                    }.start()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun handleNavigationState(state: NavigationState) {
        when (state) {
            is NavigationState.Idle -> {
                binding.navigationPanel.root.visibility = View.GONE
                binding.activeNavigationPanel.root.visibility = View.GONE
                binding.categoryContainer.visibility = View.VISIBLE
                binding.fabArMode.visibility = View.GONE
                binding.fabToggleDashboard.visibility = View.VISIBLE
                binding.fabSatellite.visibility = View.VISIBLE
                clearRoute()
                stopNavigationService()
            }
            is NavigationState.Previewing -> {
                binding.navigationPanel.root.visibility = View.VISIBLE
                binding.activeNavigationPanel.root.visibility = View.GONE
                binding.categoryContainer.visibility = View.VISIBLE
                binding.fabArMode.visibility = View.GONE
                binding.fabToggleDashboard.visibility = View.GONE
                binding.fabSatellite.visibility = View.VISIBLE

                binding.navigationPanel.tvDestinationName.text = state.destination.name
                binding.navigationPanel.tvDestinationDescription.text = state.destination.description ?: ""
                binding.navigationPanel.ivDestinationIcon.setImageResource(getIconForBuildingType(state.destination.type))

                state.route?.let { route -> updateRouteInfo(route) } ?: run {
                    binding.navigationPanel.tvDistance.text = "..."
                    binding.navigationPanel.tvEta.text = "..."
                }
                clearRoute()
                stopNavigationService()
            }
            is NavigationState.Navigating -> {
                binding.navigationPanel.root.visibility = View.GONE
                binding.activeNavigationPanel.root.visibility = View.VISIBLE
                binding.categoryContainer.visibility = View.GONE
                binding.fabArMode.visibility = View.VISIBLE
                binding.fabToggleDashboard.visibility = View.GONE
                binding.fabSatellite.visibility = View.GONE

                binding.activeNavigationPanel.infoContainer.visibility = View.VISIBLE
                displayRoute(state.route)
                updateActiveNavigation(state)
            }
            is NavigationState.Arrived -> {
                binding.navigationPanel.root.visibility = View.GONE
                binding.activeNavigationPanel.root.visibility = View.GONE
                binding.categoryContainer.visibility = View.GONE
                binding.fabArMode.visibility = View.GONE
                binding.fabToggleDashboard.visibility = View.VISIBLE
                binding.fabSatellite.visibility = View.VISIBLE
                clearRoute()
                stopNavigationService()
            }
        }
    }

    private fun handleUiEvent(event: MapUiEvent) {
        when (event) {
            is MapUiEvent.LaunchARNavigation -> {
                val intent = Intent(requireContext(), ARNavigationActivity::class.java)
                val dest = event.route.waypoints.last().location
                intent.putExtra("TARGET_LAT", dest.latitude)
                intent.putExtra("TARGET_LON", dest.longitude)
                intent.putExtra("TARGET_NAME", event.destinationName)

                val points = viewModel.routePoints.value ?: event.route.waypoints.map { GeoPoint(it.location.latitude, it.location.longitude) }
                val lats = points.map { it.latitude }.toDoubleArray()
                val lons = points.map { it.longitude }.toDoubleArray()
                intent.putExtra("ROUTE_LATS", lats)
                intent.putExtra("ROUTE_LONS", lons)

                arNavigationLauncher.launch(intent)
            }
            is MapUiEvent.MoveCameraTo -> {
                mapView.controller.animateTo(event.location, 18.5, 1500L)
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(3500)
                    myLocationOverlay?.myLocation?.let { myLoc ->
                        mapView.controller.animateTo(myLoc, 18.5, 1500L)
                        viewModel.setFollowingUser(true)
                    }
                }
            }
            is MapUiEvent.ShowError -> Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
            is MapUiEvent.OffRoute -> Snackbar.make(binding.root, "You are off route", Snackbar.LENGTH_SHORT).show()
            is MapUiEvent.ShowArrivalDialog -> showArrivalDialog(event.buildingName)
            else -> {}
        }
    }

    private fun showArrivalDialog(buildingName: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_arrival, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<TextView>(R.id.tvArrivalMessage).text = "You have successfully reached $buildingName."
        dialogView.findViewById<Button>(R.id.btnFinishNavigation).setOnClickListener {
            dialog.dismiss()
            viewModel.stopNavigation()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun configureOSMDroid() {
        Configuration.getInstance().load(requireContext(), androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext()))
        Configuration.getInstance().userAgentValue = requireContext().packageName
    }

    private fun setupMap() {
        mapView = binding.mapView
        isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(17.0)
            controller.setCenter(GeoPoint(9.8515, 122.8867))
            minZoomLevel = 14.0
            maxZoomLevel = 20.0
            isTilesScaledToDpi = true
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            if (isDarkMode) applyDarkModeFilter()
        }

        routePolyline = Polyline(mapView).apply {
            outlinePaint.color = ContextCompat.getColor(requireContext(), R.color.primary_green)
            outlinePaint.strokeWidth = 16f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            outlinePaint.isAntiAlias = true
        }
    }

    private fun applyDarkModeFilter() {
        val inverseMatrix = floatArrayOf(
            -1.0f, 0.0f, 0.0f, 0.0f, 255f,
            0.0f, -1.0f, 0.0f, 0.0f, 255f,
            0.0f, 0.0f, -1.0f, 0.0f, 255f,
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        )
        mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(inverseMatrix))
    }

    fun setupCampusMap(mapView: MapView) {
        val campusNorth = 9.857365352521331
        val campusSouth = 9.843818207123961
        val campusEast = 122.89307299341952
        val campusWest = 122.88305672555643
        mapView.setScrollableAreaLimitDouble(BoundingBox(campusNorth, campusEast, campusSouth, campusWest))
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

    private fun updateRouteInfo(route: Route) {
        val dist = formatDistance(route.totalDistance)
        val time = formatTime(route.estimatedTime)
        binding.navigationPanel.tvDistance.text = dist
        binding.navigationPanel.tvEta.text = time
        binding.navigationPanel.tvRouteInfoMini.text = "$dist • $time"
    }

    private fun displayRoute(route: Route) {
        val allPoints = viewModel.routePoints.value ?: route.waypoints.map { GeoPoint(it.location.latitude, it.location.longitude) }
        routePolyline.setPoints(allPoints)
        mapView.invalidate()
    }

    private fun clearRoute() {
        routePolyline.setPoints(emptyList())
        offRoutePolyline.clear()
        destinationConnectorOverlay.clear()
        mapView.invalidate()
    }

    private fun updateActiveNavigation(state: NavigationState.Navigating) {
        val step = state.currentStep
        val distText = formatDistance(state.distanceToNextWaypoint)

        binding.activeNavigationPanel.tvCurrentInstruction.text = step.instruction
        binding.activeNavigationPanel.tvDistanceToNext.text = "in $distText"
        binding.activeNavigationPanel.tvRemainingDistance.text = formatDistance(state.remainingDistance)
        binding.activeNavigationPanel.tvRemainingTime.text = formatTime(state.remainingTime)
        binding.activeNavigationPanel.ivDirectionIcon.setImageResource(getDirectionIcon(step.direction))

        updateNavigationService(step.instruction, distText, getDirectionCode(step.direction))
    }

    private fun updateNavigationService(instruction: String, distance: String, directionCode: String) {
        if (!Settings.canDrawOverlays(requireContext())) return
        val intent = Intent(requireContext(), NavigationService::class.java).apply {
            action = NavigationService.ACTION_UPDATE
            putExtra(NavigationService.EXTRA_INSTRUCTION, instruction)
            putExtra(NavigationService.EXTRA_DISTANCE, distance)
            putExtra(NavigationService.EXTRA_DIRECTION, directionCode)
        }
        ContextCompat.startForegroundService(requireContext(), intent)
    }

    private fun stopNavigationService() {
        val intent = Intent(requireContext(), NavigationService::class.java)
        requireContext().stopService(intent)
    }

    private fun getDirectionCode(direction: Direction): String {
        return when (direction) {
            Direction.RIGHT, Direction.SHARP_RIGHT, Direction.SLIGHT_RIGHT -> "right"
            Direction.LEFT, Direction.SHARP_LEFT, Direction.SLIGHT_LEFT -> "left"
            else -> "straight"
        }
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

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun toggleMapLayer() { viewModel.setSatelliteView(!(viewModel.isSatelliteView.value)) }

    private fun toggleCompassMode() { viewModel.setCompassMode(!(viewModel.isCompassMode.value)) }

    private fun enableSatellite() {
        try {
            mapView.overlayManager.tilesOverlay.setColorFilter(null)
            val googleSatellite = object : OnlineTileSourceBase(
                "Google-Satellite", 0, 20, 256, ".png", arrayOf("https://mt0.google.com/vt/lyrs=s&hl=en&x=")
            ) {
                override fun getTileURLString(pMapTileIndex: Long): String =
                    "${baseUrl}${MapTileIndex.getX(pMapTileIndex)}&y=${MapTileIndex.getY(pMapTileIndex)}&z=${MapTileIndex.getZoom(pMapTileIndex)}"
            }
            mapView.setTileSource(googleSatellite)
            campusPathsOverlay.addPathsToMap(mapView)

            mapView.overlays.remove(routePolyline)
            mapView.overlays.add(routePolyline)
            myLocationOverlay?.let { mapView.overlays.remove(it); mapView.overlays.add(it) }
            buildingMarkers.forEach { marker -> mapView.overlays.remove(marker); mapView.overlays.add(marker) }

            binding.fabSatellite.setImageResource(R.drawable.ic_map)

            Toast.makeText(requireContext(), "Satellite View", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            viewModel.setSatelliteView(false)
        }
        mapView.invalidate()
        mapView.overlays.remove(offRoutePolyline)
        mapView.overlays.add(offRoutePolyline)
        mapView.overlays.remove(destinationConnectorOverlay)
        mapView.overlays.add(destinationConnectorOverlay)
    }

    private fun disableSatellite() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        campusPathsOverlay.clearPaths(mapView)
        if (isDarkMode) applyDarkModeFilter() else mapView.overlayManager.tilesOverlay.setColorFilter(null)

        binding.fabSatellite.setImageResource(R.drawable.ic_satellite)
    }

    private fun startCompassMode() {
        binding.fabCompass.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.primary_green))
        binding.fabCompass.setImageResource(R.drawable.ic_compass)
        Toast.makeText(requireContext(), "Compass Mode: On", Toast.LENGTH_SHORT).show()

        mapCompassManager.start { mapRotation -> mapView.mapOrientation = mapRotation }
        viewModel.setFollowingUser(true)
        myLocationOverlay?.enableFollowLocation()
    }

    private fun stopCompassMode() {
        mapCompassManager.stop()
        binding.fabCompass.imageTintList = ColorStateList.valueOf(
            com.google.android.material.color.MaterialColors.getColor(binding.fabCompass, com.google.android.material.R.attr.colorOnSurface)
        )
        val currentOrientation = mapView.mapOrientation
        if (currentOrientation != 0f) {
            val animator = ValueAnimator.ofFloat(currentOrientation, 0f)
            animator.duration = 500
            animator.addUpdateListener { mapView.mapOrientation = it.animatedValue as Float }
            animator.start()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        if (myLocationOverlay == null && hasLocationPermission()) setupLocationOverlay()
        myLocationOverlay?.enableMyLocation()
        mainActivity?.setBottomNavVisibility(false)

        updatePOIMarkers()
        updateGeofencePolygons()

        if (viewModel.navigationState.value is NavigationState.Navigating) {
            val hideIntent = Intent(requireContext(), NavigationService::class.java).apply {
                action = NavigationService.ACTION_HIDE_OVERLAY
            }
            ContextCompat.startForegroundService(requireContext(), hideIntent)
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        myLocationOverlay?.disableMyLocation()
        mapCompassManager.stop()

        if (viewModel.navigationState.value is NavigationState.Navigating) {
            val showIntent = Intent(requireContext(), NavigationService::class.java).apply {
                action = NavigationService.ACTION_SHOW_OVERLAY
            }
            ContextCompat.startForegroundService(requireContext(), showIntent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        campusPathsOverlay.clearPaths(mapView)
        mapView.onDetach()
        etMapSearch = null
        btnMapClear = null
        searchDropdown = null
        rvMapSearchResults = null
        _binding = null
    }

    // --- HELPER FUNCTIONS ---

    private fun getIconForBuildingType(type: BuildingType): Int {
        return when (type) {
            BuildingType.ACADEMIC -> R.drawable.ic_school
            BuildingType.LIBRARY -> R.drawable.ic_library
            BuildingType.CAFETERIA -> R.drawable.ic_restaurant
            BuildingType.SPORTS -> R.drawable.ic_sports
            BuildingType.ADMINISTRATIVE -> R.drawable.ic_business
            BuildingType.LANDMARK -> R.drawable.ic_landmark
        }
    }

    private fun getIconForMarkerType(iconType: String): Int {
        return when (iconType.uppercase()) {
            "ENTRANCE" -> R.drawable.ic_landmark
            "ATM" -> R.drawable.ic_business
            "RESTROOM" -> R.drawable.ic_landmark
            "PARKING" -> R.drawable.ic_landmark
            "INFO" -> R.drawable.ic_business
            "FOOD" -> R.drawable.ic_restaurant
            "LIBRARY" -> R.drawable.ic_library
            "LAB" -> R.drawable.ic_school
            "OFFICE" -> R.drawable.ic_business
            else -> R.drawable.ic_landmark
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
}