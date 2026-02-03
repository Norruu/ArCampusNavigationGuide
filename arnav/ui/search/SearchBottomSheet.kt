package com.campus.arnav.ui.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.campus.arnav.R
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.BuildingType
import com.campus.arnav.databinding.BottomSheetSearchBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels()

    private var searchResultsAdapter: SearchResultsAdapter? = null
    private var recentSearchesAdapter: RecentSearchesAdapter? = null

    private var onBuildingSelected: ((Building) -> Unit)? = null

    companion object {
        const val TAG = "SearchBottomSheet"

        fun newInstance(): SearchBottomSheet {
            return SearchBottomSheet()
        }
    }

    fun setOnBuildingSelectedListener(listener: (Building) -> Unit) {
        onBuildingSelected = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupRecyclerViews()
        setupCategoryChips()
        observeViewModel()
    }

    private fun setupUI() {
        // Search input
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                viewModel.search(query)
                updateUIState(query)
            }
        })

        // Clear button
        binding.btnClear.setOnClickListener {
            binding.etSearch.text?.clear()
            viewModel.clearSearch()
        }

        // Close button
        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    private fun setupRecyclerViews() {
        // Search results
        searchResultsAdapter = SearchResultsAdapter { building ->
            viewModel.addToRecentSearches(building)
            onBuildingSelected?.invoke(building)
            dismiss()
        }

        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchResultsAdapter
        }

        // Recent searches
        recentSearchesAdapter = RecentSearchesAdapter(
            onItemClick = { building ->
                onBuildingSelected?.invoke(building)
                dismiss()
            },
            onRemoveClick = { building ->
                viewModel.removeFromRecentSearches(building)
            }
        )

        binding.rvRecentSearches.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentSearchesAdapter
        }
    }

    private fun setupCategoryChips() {
        val categories = listOf(
            CategoryItem("Academic", BuildingType.ACADEMIC, R.drawable.ic_school),
            CategoryItem("Library", BuildingType.LIBRARY, R.drawable.ic_library),
            CategoryItem("Food", BuildingType.CAFETERIA, R.drawable.ic_restaurant),
            CategoryItem("Sports", BuildingType.SPORTS, R.drawable.ic_sports),
            CategoryItem("Parking", BuildingType.PARKING, R.drawable.ic_parking)
        )

        binding.chipGroupCategories.removeAllViews()

        categories.forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = category.name
                isCheckable = true
                setChipIconResource(category.iconRes)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        viewModel.filterByType(category.type)
                    } else {
                        viewModel.clearFilter()
                    }
                }
            }
            binding.chipGroupCategories.addView(chip)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.searchResults.collectLatest { results ->
                searchResultsAdapter?.submitList(results)
                binding.layoutNoResults.visibility =
                    if (results.isEmpty() && viewModel.isSearching.value) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.recentSearches.collectLatest { recent ->
                recentSearchesAdapter?.submitList(recent)
                binding.layoutRecentSearches.visibility =
                    if (recent.isNotEmpty() && !viewModel.isSearching.value) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isSearching.collectLatest { isSearching ->
                binding.rvSearchResults.visibility = if (isSearching) View.VISIBLE else View.GONE
                binding.layoutCategories.visibility = if (!isSearching) View.VISIBLE else View.GONE
            }
        }
    }

    private fun updateUIState(query: String) {
        val isSearching = query.isNotEmpty()
        binding.btnClear.visibility = if (isSearching) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class CategoryItem(
        val name: String,
        val type: BuildingType,
        val iconRes: Int
    )
}