package com.campus.arnav.ui.navigation

import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.NavigationStep
import com.campus.arnav.data.model.Route

sealed class NavigationState {

    object Idle : NavigationState()

    data class Previewing(
        val destination: Building,
        val route: Route
    ) : NavigationState()

    data class Navigating(
        val route: Route,
        val currentStep: NavigationStep,
        val currentStepIndex: Int,
        val distanceToNextWaypoint: Double,
        val remainingDistance: Double,
        val remainingTime: Long
    ) : NavigationState()

    data class Arrived(
        val destination: Building
    ) : NavigationState()
}