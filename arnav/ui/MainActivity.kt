package com.campus.arnav.ui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.campus.arnav.service.NavigationService
import com.campus.arnav.ui.map.MapFragment
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var campusPathfinding: CampusPathfinding
    @Inject lateinit var campusRepository: CampusRepository
    @Inject lateinit var pathfindingEngine: PathfindingEngine

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    // 1. Variable to hold the splash screen open
    private var keepSplash = true

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
        // 2. Install the Splash Screen and capture the object
        val splashScreen = installSplashScreen()

        // 3. Keep it on screen for 1.5 seconds so users see the logo
        splashScreen.setKeepOnScreenCondition { keepSplash }
        lifecycleScope.launch {
            delay(1500L)
            keepSplash = false
        }

        // 4. The Premium Exit Animation (Fade out + Smooth Zoom)
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val alpha = ObjectAnimator.ofFloat(splashScreenView.view, View.ALPHA, 1f, 0f)
            val scaleX = ObjectAnimator.ofFloat(splashScreenView.view, View.SCALE_X, 1f, 1.15f)
            val scaleY = ObjectAnimator.ofFloat(splashScreenView.view, View.SCALE_Y, 1f, 1.15f)

            AnimatorSet().apply {
                playTogether(alpha, scaleX, scaleY)
                duration = 500L // Half a second smooth transition
                interpolator = AccelerateDecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        splashScreenView.remove() // Remove it once the animation finishes
                    }
                })
                start()
            }
        }

        // --- Original Setup Code Continues Below ---
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean(KEY_DARK_MODE, false)

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

        // Setup Bottom Nav
        binding.bottomNavigation.setupWithNavController(navController)

        // Handle Satellite Mode & Navigation
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_satellite -> {
                    val mapFragment = navHostFragment.childFragmentManager.fragments
                        .find { it is MapFragment } as? MapFragment

                    mapFragment?.toggleMapLayer()
                    false
                }
                else -> {
                    if (item.itemId != binding.bottomNavigation.selectedItemId) {
                        navController.navigate(item.itemId)
                    }
                    true
                }
            }
        }

        // Prevent reloading the same fragment
        binding.bottomNavigation.setOnItemReselectedListener { }

        checkPermissions()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val buildings = campusRepository.getAllBuildings()
                campusPathfinding.initializeFromCampusPaths()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setBottomNavVisibility(visible: Boolean) {
        binding.bottomNavigation.visibility = if (visible) View.VISIBLE else View.GONE
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

    fun toggleTheme(enableDarkMode: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK_MODE, enableDarkMode).apply()

        if (enableDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        }
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
    var isCurrentlyNavigating = false

    override fun onResume() {
        super.onResume()
        // When user returns to app, hide the floating window!
        val intent = Intent(this, NavigationService::class.java).apply {
            action = NavigationService.ACTION_HIDE_OVERLAY
        }
        startService(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // When user presses Home button, ask the service to pop the floating window!
        if (isCurrentlyNavigating) {
            if (Settings.canDrawOverlays(this)) {
                val intent = Intent(this, NavigationService::class.java).apply {
                    action = NavigationService.ACTION_SHOW_OVERLAY
                }
                startService(intent)
            } else {
                // Ask for permission the first time they try to float it
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }
    }
}