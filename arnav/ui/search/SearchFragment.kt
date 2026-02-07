package com.campus.arnav.ui.search

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.campus.arnav.data.model.Building
import com.campus.arnav.databinding.FragmentSearchBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels()

    private lateinit var searchResultsAdapter: SearchResultsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSearchBar()
        setupRecyclerView()
        observeViewModel()
        showInitialState()
    }

    private fun setupSearchBar() {
        // Text change listener with clear button
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                viewModel.search(query)

                // Show/hide clear button
                binding.btnClear.isVisible = query.isNotEmpty()
            }
        })

        // Clear button
        binding.btnClear.setOnClickListener {
            binding.etSearch.text?.clear()
            viewModel.clearSearch()
        }

        // Handle search action on keyboard
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else {
                false
            }
        }

        // Auto-focus and show keyboard
        binding.etSearch.postDelayed({
            binding.etSearch.requestFocus()
            showKeyboard()
        }, 100)
    }

    private fun setupRecyclerView() {
        searchResultsAdapter = SearchResultsAdapter { building ->
            onBuildingSelected(building)
        }

        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchResultsAdapter

            // Add spacing between items
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    outRect.bottom = 8.dpToPx()
                }
            })
        }
    }

    private fun observeViewModel() {
        // Search results
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.searchResults.collectLatest { results ->
                searchResultsAdapter.submitList(results)
                updateUIState(results)
            }
        }

        // Loading state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isSearching.collectLatest { isSearching ->
                // Show/hide loading if you add a progress bar
                // binding.progressBar?.isVisible = isSearching
            }
        }
    }

    private fun updateUIState(results: List<Building>) {
        val searchQuery = binding.etSearch.text?.toString() ?: ""
        val isSearching = searchQuery.isNotEmpty()

        when {
            !isSearching -> {
                // Show empty state (start searching)
                showEmptyState()
            }
            isSearching && results.isEmpty() -> {
                // Show no results found
                showNoResults()
            }
            else -> {
                // Show results
                showResults()
            }
        }
    }

    private fun showInitialState() {
        binding.apply {
            rvSearchResults.isVisible = false
            layoutEmptyState?.isVisible = true
            layoutNoResults?.isVisible = false
            progressBar?.isVisible = false
        }
    }

    private fun showEmptyState() {
        binding.apply {
            rvSearchResults.isVisible = false
            layoutEmptyState?.isVisible = true
            layoutNoResults?.isVisible = false
            progressBar?.isVisible = false
        }
    }

    private fun showNoResults() {
        binding.apply {
            rvSearchResults.isVisible = false
            layoutEmptyState?.isVisible = false
            layoutNoResults?.isVisible = true
            progressBar?.isVisible = false
        }
    }

    private fun showResults() {
        binding.apply {
            rvSearchResults.isVisible = true
            layoutEmptyState?.isVisible = false
            layoutNoResults?.isVisible = false
            progressBar?.isVisible = false
        }
    }

    private fun onBuildingSelected(building: Building) {
        hideKeyboard()
        viewModel.addToRecentSearches(building)

        // TODO: Navigate to map with selected building
        // Option 1: Using Safe Args (recommended)
        // val action = SearchFragmentDirections.actionSearchFragmentToMapFragment(building)
        // findNavController().navigate(action)

        // Option 2: Using Bundle
        // val bundle = Bundle().apply {
        //     putParcelable("selected_building", building)
        //     putString("building_id", building.id)
        // }
        // findNavController().navigate(R.id.action_searchFragment_to_mapFragment, bundle)

        // For now, just go back
        findNavController().navigateUp()
    }

    private fun showKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hideKeyboard()
        _binding = null
    }
}