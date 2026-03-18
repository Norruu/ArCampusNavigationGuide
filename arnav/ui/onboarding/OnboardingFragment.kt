package com.campus.arnav.ui.onboarding

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.campus.arnav.R
import com.campus.arnav.ui.MainActivity
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Skip onboarding if already seen
        val prefs = requireContext().getSharedPreferences("arnav_settings", Context.MODE_PRIVATE)
        val hasSeenOnboarding = prefs.getBoolean("has_seen_onboarding", false)

        if (hasSeenOnboarding) {
            // Navigate directly to dashboard
            findNavController().navigate(R.id.action_onboarding_to_dashboard)
            return View(requireContext()) // Return empty view (won't be shown)
        }

        return inflater.inflate(R.layout.fragment_onboarding, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide bottom nav on onboarding
        (activity as? MainActivity)?.setBottomNavVisibility(false)

        view.findViewById<MaterialButton>(R.id.btnStart)?.setOnClickListener {
            // Mark as seen
            val prefs = requireContext().getSharedPreferences("arnav_settings", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("has_seen_onboarding", true).apply()

            findNavController().navigate(R.id.action_onboarding_to_dashboard)
        }
    }
}