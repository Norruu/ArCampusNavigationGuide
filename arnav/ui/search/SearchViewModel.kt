package com.campus.arnav.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.BuildingType
import com.campus.arnav.data.repository.CampusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val campusRepository: CampusRepository
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<Building>>(emptyList())
    val searchResults: StateFlow<List<Building>> = _searchResults.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<Building>>(emptyList())
    val recentSearches: StateFlow<List<Building>> = _recentSearches.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null
    private var currentFilter: BuildingType? = null

    init {
        loadRecentSearches()
    }

    fun search(query: String) {
        searchJob?.cancel()

        if (query.isBlank()) {
            _isSearching.value = false
            _searchResults.value = emptyList()
            return
        }

        _isSearching.value = true

        searchJob = viewModelScope.launch {
            // Debounce
            delay(300)

            val results = campusRepository.searchBuildings(query)

            // Apply filter if set
            val filteredResults = if (currentFilter != null) {
                results.filter { it.type == currentFilter }
            } else {
                results
            }

            _searchResults.value = filteredResults
        }
    }

    fun filterByType(type: BuildingType) {
        currentFilter = type
        viewModelScope.launch {
            val buildings = campusRepository.getAllBuildings()
            _searchResults.value = buildings.filter { it.type == type }
            _isSearching.value = true
        }
    }

    fun clearFilter() {
        currentFilter = null
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _isSearching.value = false
        currentFilter = null
    }

    fun addToRecentSearches(building: Building) {
        val current = _recentSearches.value.toMutableList()
        current.removeAll { it.id == building.id }
        current.add(0, building)

        // Keep only last 10
        if (current.size > 10) {
            current.removeAt(current.lastIndex)
        }

        _recentSearches.value = current
        // TODO: Save to local storage
    }

    fun removeFromRecentSearches(building: Building) {
        val current = _recentSearches.value.toMutableList()
        current.removeAll { it.id == building.id }
        _recentSearches.value = current
        // TODO: Save to local storage
    }

    private fun loadRecentSearches() {
        // TODO: Load from local storage
        _recentSearches.value = emptyList()
    }
}