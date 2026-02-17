package com.campus.arnav.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.campus.arnav.R
import com.campus.arnav.data.repository.CampusRepository
import com.campus.arnav.databinding.ActivityMainBinding
import com.campus.arnav.domain.pathfinding.CampusPathfinding
import com.campus.arnav.domain.pathfinding.PathfindingEngine
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.campus.arnav.ui.map.MapFragment
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var campusPathfinding: CampusPathfinding
    @Inject lateinit var campusRepository: CampusRepository
    @Inject lateinit var pathfindingEngine: PathfindingEngine

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    // PREFERENCE KEYS
    companion object {
        private const val PREFS_NAME = "arnav_settings"
        private const val KEY_DARK_MODE = "is_dark_mode"
    }

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            setupNavigation()
        } else {
            onPermissionsDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        // --- 1. LOAD SAVED THEME (Before super.onCreate) ---
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean(KEY_DARK_MODE, false) // Default to Light (false)

        if (isDark) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)

        binding.bottomNavigation.setupWithNavController(navController)

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_satellite -> {
                    // 1. Find the active MapFragment
                    val mapFragment = navHostFragment.childFragmentManager.fragments
                        .find { it is MapFragment } as? MapFragment

                    // 2. Trigger the toggle
                    mapFragment?.toggleMapLayer()

                    false // Return false: don't "switch" tabs, just run the toggle
                }
                else -> {
                    // Standard navigation for other items
                    navController.navigate(item.itemId)
                    true
                }
            }
        }

        checkPermissions()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val buildings = campusRepository.getAllBuildings()
                campusPathfinding.initializeFromCampusPaths()
                android.util.Log.d("ArNav", "System Initialized: ${buildings.size} buildings loaded.")
            } catch (e: Exception) {
                android.util.Log.e("ArNav", "Initialization Failed", e)
            }
        }
    }

    /**
     * Call this from SettingsFragment to switch themes manually
     */
    fun toggleTheme(enableDarkMode: Boolean) {
        // --- 2. SAVE PREFERENCE ---
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK_MODE, enableDarkMode).apply()

        // Apply immediately
        if (enableDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun checkPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions)
        } else {
            setupNavigation()
        }
    }

    private fun setupNavigation() {
        // Permissions granted
    }

    private fun onPermissionsDenied() {
        Snackbar.make(
            binding.root,
            "Location and camera permissions are required for navigation",
            Snackbar.LENGTH_INDEFINITE
        ).setAction("Grant") {
            checkPermissions()
        }.show()
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestLocationPermission() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    fun requestCameraPermission() {
        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}