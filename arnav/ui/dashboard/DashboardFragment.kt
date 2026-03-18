package com.campus.arnav.ui.dashboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.campus.arnav.R
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.BuildingType
import com.campus.arnav.data.repository.CampusRepository
import com.campus.arnav.databinding.FragmentDashboardBinding
import com.campus.arnav.ui.MainActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import javax.inject.Inject

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var campusRepository: CampusRepository

    private var allBuildings: List<Building> = emptyList()
    private lateinit var buildingAdapter: BuildingCardAdapter

    private var etSearch: EditText? = null
    private var btnClear: ImageView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find nested views
        etSearch = view.findViewById(R.id.etSearch)
        btnClear = view.findViewById(R.id.btnClearSearch)

        (activity as? MainActivity)?.setBottomNavVisibility(false)

        setupMiniMap()
        setupCategoryChips()
        setupBuildingList()
        setupSearch()
        setupToggleButton()

        loadBuildings()
    }

    private fun setupMiniMap() {
        Configuration.getInstance().load(
            requireContext(),
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        )

        binding.miniMapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(9.8515, 122.8867))
            isClickable = false
            isFocusable = false
        }

        try {
            val locationOverlay = MyLocationNewOverlay(
                GpsMyLocationProvider(requireContext()), binding.miniMapView
            )
            locationOverlay.enableMyLocation()
            binding.miniMapView.overlays.add(locationOverlay)
        } catch (_: Exception) {}
    }

    private fun setupCategoryChips() {
        val chipGroup = binding.dashboardChipGroup
        chipGroup.removeAllViews()

        val bgStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val bgColors = intArrayOf(
            ContextCompat.getColor(requireContext(), R.color.primary_green),
            Color.parseColor("#E8E8E8")
        )
        val chipBg = ColorStateList(bgStates, bgColors)
        val textColors = intArrayOf(Color.WHITE, Color.BLACK)
        val textColorList = ColorStateList(bgStates, textColors)

        val allChip = com.google.android.material.chip.Chip(
            requireContext(), null, com.google.android.material.R.style.Widget_Material3_Chip_Filter
        ).apply {
            text = "All"
            isCheckable = true
            isCheckedIconVisible = false
            chipBackgroundColor = chipBg
            setTextColor(textColorList)
            isChecked = true
            setOnClickListener { filterBuildings(null) }
        }
        chipGroup.addView(allChip)

        val shortNames = mapOf(
            BuildingType.ACADEMIC to "Acad.",
            BuildingType.LIBRARY to "Lib.",
            BuildingType.CAFETERIA to "Food",
            BuildingType.SPORTS to "Sport",
            BuildingType.ADMINISTRATIVE to "Admin",
            BuildingType.LANDMARK to "Land."
        )

        BuildingType.values().forEach { type ->
            val chip = com.google.android.material.chip.Chip(
                requireContext(), null, com.google.android.material.R.style.Widget_Material3_Chip_Filter
            ).apply {
                text = shortNames[type] ?: type.name
                isCheckable = true
                isCheckedIconVisible = false
                chipBackgroundColor = chipBg
                setTextColor(textColorList)
                setOnClickListener { filterBuildings(type) }
            }
            chipGroup.addView(chip)
        }
    }

    private fun setupBuildingList() {
        buildingAdapter = BuildingCardAdapter { building ->
            showBuildingDetail(building)
        }

        binding.rvBuildings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = buildingAdapter
        }
    }

    private fun setupSearch() {
        etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    btnClear?.visibility = View.VISIBLE
                    val filtered = allBuildings.filter {
                        it.name.contains(query, ignoreCase = true) ||
                                it.shortName.contains(query, ignoreCase = true) ||
                                (it.description?.contains(query, ignoreCase = true) == true)
                    }
                    buildingAdapter.submitList(filtered)
                    binding.tvExploreTitle.text = "Search Results (${filtered.size})"
                } else {
                    btnClear?.visibility = View.GONE
                    buildingAdapter.submitList(allBuildings)
                    binding.tvExploreTitle.text = "Explore Places"
                }
            }
        })

        btnClear?.setOnClickListener {
            etSearch?.text?.clear()
            etSearch?.clearFocus()
            btnClear?.visibility = View.GONE
            buildingAdapter.submitList(allBuildings)
            binding.tvExploreTitle.text = "Explore Places"
            hideKeyboard()
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch?.windowToken, 0)
    }

    private fun setupToggleButton() {
        binding.fabToggleMap.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_map)
        }
    }

    private fun loadBuildings() {
        viewLifecycleOwner.lifecycleScope.launch {
            allBuildings = campusRepository.getAllBuildings()
            buildingAdapter.submitList(allBuildings)
        }
    }

    private fun filterBuildings(type: BuildingType?) {
        // Clear search when switching category
        etSearch?.text?.clear()
        btnClear?.visibility = View.GONE
        binding.tvExploreTitle.text = "Explore Places"

        val filtered = if (type == null) allBuildings
        else allBuildings.filter { it.type == type }
        buildingAdapter.submitList(filtered)
    }

    private fun showBuildingDetail(building: Building) {
        val dialog = BottomSheetDialog(requireContext(), R.style.Theme_CampusARNav_BottomSheet)
        val sheetView = LayoutInflater.from(requireContext())
            .inflate(R.layout.bottom_sheet_building_detail, null)

        val tvName = sheetView.findViewById<TextView>(R.id.tvDetailName)
        val tvDesc = sheetView.findViewById<TextView>(R.id.tvDetailDescription)
        val tvNavName = sheetView.findViewById<TextView>(R.id.tvNavigateToName)
        val btnNo = sheetView.findViewById<View>(R.id.btnNo)
        val btnYes = sheetView.findViewById<View>(R.id.btnYes)

        tvName?.text = building.name
        tvDesc?.text = building.description ?: "No description available"
        tvNavName?.text = "${building.name}?"

        btnNo?.setOnClickListener { dialog.dismiss() }

        btnYes?.setOnClickListener {
            dialog.dismiss()
            val bundle = Bundle().apply {
                putString("navigateToBuildingId", building.id)
            }
            findNavController().navigate(R.id.action_dashboard_to_map, bundle)
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        binding.miniMapView.onResume()
        (activity as? MainActivity)?.setBottomNavVisibility(false)
    }

    override fun onPause() {
        super.onPause()
        binding.miniMapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.miniMapView.onDetach()
        etSearch = null
        btnClear = null
        _binding = null
    }
}