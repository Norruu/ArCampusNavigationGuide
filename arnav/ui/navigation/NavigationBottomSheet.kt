package com.campus.arnav.ui.navigation

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.campus.arnav.R
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.BuildingType
import com.campus.arnav.data.model.Direction
import com.campus.arnav.data.model.Route
import com.campus.arnav.databinding.BottomSheetNavigationBinding
import com.campus.arnav.ui.ar.ARNavigationActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView

@AndroidEntryPoint
class NavigationBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetNavigationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NavigationViewModel by activityViewModels()

    companion object {
        const val TAG = "NavigationBottomSheet"
        private const val ARG_BUILDING_ID = "building_id"

        fun newInstance(building: Building): NavigationBottomSheet {
            return NavigationBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_BUILDING_ID, building.id)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetNavigationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.apply {
            // Close button
            btnClose.setOnClickListener {
                dismiss()
            }

            // Start navigation button
            btnStartNavigation.setOnClickListener {
                viewModel.startNavigation()
            }

            // AR mode button
            btnArMode.setOnClickListener {
                viewModel.switchToARMode()
            }

            // End navigation button
            btnEndNavigation.setOnClickListener {
                viewModel.stopNavigation()
                showPreviewState()
            }

            // Recalculate route button
            btnRecalculate.setOnClickListener {
                viewModel.recalculateRoute()
            }

            // Accessible route toggle
            chipAccessible.setOnCheckedChangeListener { _, isChecked ->
                // Recalculate with accessible preference
                // viewModel.setAccessibleRoute(isChecked)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.destination.collectLatest { building ->
                building?.let { updateDestinationInfo(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.route.collectLatest { route ->
                route?.let { updateRouteInfo(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.navigationState.collectLatest { state ->
                handleNavigationState(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                binding.btnStartNavigation.isEnabled = !isLoading
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collectLatest { error ->
                error?.let {
                    showError(it)
                    viewModel.clearError()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiEvent.collectLatest { event ->
                handleUiEvent(event)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.alternativeRoutes.collectLatest { routes ->
                updateAlternativeRoutes(routes)
            }
        }
    }

    private fun updateDestinationInfo(building: Building) {
        binding.apply {
            tvDestinationName.text = building.name
            tvDestinationDescription.text = building.description
            ivDestinationIcon.setImageResource(getIconForBuildingType(building.type))

            // Update accessibility chip visibility
            chipAccessible.visibility = if (building.isAccessible) View.VISIBLE else View.GONE
        }
    }

    private fun updateRouteInfo(route: Route) {
        binding.apply {
            tvDistance.text = formatDistance(route.totalDistance)
            tvEta.text = formatTime(route.estimatedTime)
            tvStepsCount.text = "${route.steps.size} steps"
        }
    }

    /**
     * Handle all navigation states - MUST be exhaustive
     */
    private fun handleNavigationState(state: NavigationState) {
        when (state) {
            is NavigationState.Idle -> {
                showPreviewState()
            }
            is NavigationState.Previewing -> {
                showPreviewState()
                updateDestinationInfo(state.destination)
                updateRouteInfo(state.route)
            }
            is NavigationState.Navigating -> {
                showActiveNavigationState()
                updateActiveNavigation(state)
            }
            is NavigationState.Arrived -> {
                showArrivedState()
            }
            else -> {
                // Handle any future states
                showPreviewState()
            }
        }
    }

    private fun showPreviewState() {
        binding.apply {
            layoutPreview.visibility = View.VISIBLE
            layoutActiveNavigation.visibility = View.GONE
            layoutArrived.visibility = View.GONE
        }
    }

    private fun showActiveNavigationState() {
        binding.apply {
            layoutPreview.visibility = View.GONE
            layoutActiveNavigation.visibility = View.VISIBLE
            layoutArrived.visibility = View.GONE
        }
    }

    private fun showArrivedState() {
        binding.apply {
            layoutPreview.visibility = View.GONE
            layoutActiveNavigation.visibility = View.GONE
            layoutArrived.visibility = View.VISIBLE
        }
    }

    private fun updateActiveNavigation(state: NavigationState.Navigating) {
        binding.apply {
            // Current instruction
            tvCurrentInstruction.text = state.currentStep.instruction
            tvDistanceToNext.text = "in ${formatDistance(state.distanceToNextWaypoint)}"
            ivDirectionIcon.setImageResource(getDirectionIcon(state.currentStep.direction))

            // Progress
            val totalDistance = state.route.totalDistance
            val remaining = state.remainingDistance
            val progress = if (totalDistance > 0) {
                ((totalDistance - remaining) / totalDistance * 100).toInt()
            } else {
                0
            }
            progressRoute.progress = progress
            tvRemainingDistance.text = formatDistance(state.remainingDistance)
            tvRemainingTime.text = formatTime(state.remainingTime)

            // Step counter
            tvStepProgress.text = "${state.currentStepIndex + 1}/${state.route.steps.size}"
        }
    }

    private fun updateAlternativeRoutes(routes: List<Route>) {
        binding.chipGroupRoutes.visibility = if (routes.size > 1) View.VISIBLE else View.GONE

        if (routes.size > 1) {
            binding.tvAlternatives.text = "${routes.size} routes available"
            binding.tvAlternatives.visibility = View.VISIBLE
        } else {
            binding.tvAlternatives.visibility = View.GONE
        }
    }

    private fun handleUiEvent(event: NavigationUiEvent) {
        when (event) {
            is NavigationUiEvent.NavigationStarted -> {
                showActiveNavigationState()
            }
            is NavigationUiEvent.NavigationStopped -> {
                showPreviewState()
            }
            is NavigationUiEvent.WaypointReached -> {
                // Vibrate or show feedback
            }
            is NavigationUiEvent.Arrived -> {
                showArrivedState()
            }
            is NavigationUiEvent.OffRoute -> {
                showOffRouteWarning()
            }
            is NavigationUiEvent.RouteRecalculated -> {
                Snackbar.make(binding.root, "Route recalculated", Snackbar.LENGTH_SHORT).show()
            }
            is NavigationUiEvent.LaunchARMode -> {
                launchARNavigation(event.route)
            }
            is NavigationUiEvent.ShowError -> {
                showError(event.message)
            }
        }
    }

    private fun showOffRouteWarning() {
        binding.layoutOffRoute.visibility = View.VISIBLE

        binding.root.postDelayed({
            binding.layoutOffRoute.visibility = View.GONE
        }, 5000)
    }

    private fun launchARNavigation(route: Route) {
        val intent = Intent(requireContext(), ARNavigationActivity::class.java).apply {
            putExtra(ARNavigationActivity.EXTRA_ROUTE, route)
        }
        startActivity(intent)
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun getIconForBuildingType(type: BuildingType): Int {
        return when (type) {
            BuildingType.ACADEMIC -> R.drawable.ic_school
            BuildingType.LIBRARY -> R.drawable.ic_library
            BuildingType.CAFETERIA -> R.drawable.ic_restaurant
            BuildingType.DORMITORY -> R.drawable.ic_home
            BuildingType.SPORTS -> R.drawable.ic_sports
            BuildingType.ADMINISTRATIVE -> R.drawable.ic_business
            BuildingType.PARKING -> R.drawable.ic_parking
            BuildingType.LANDMARK -> R.drawable.ic_landmark
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}