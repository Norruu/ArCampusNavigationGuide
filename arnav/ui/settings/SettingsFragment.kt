package com.campus.arnav.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.campus.arnav.databinding.FragmentSettingsBinding
import com.campus.arnav.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.campus.arnav.data.model.TeamMember
import com.campus.arnav.R

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDarkModeToggle()
        setupNavigationSettings()
        binding.btnAboutUs.setOnClickListener {
            showAboutUsDialog()
        }
    }

    private fun setupDarkModeToggle() {
        // 1. Read Saved State
        val prefs = requireContext().getSharedPreferences("arnav_settings", Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("is_dark_mode", false)

        // 2. Set Switch State
        binding.switchDarkMode.isChecked = isDark

        // 3. Handle Changes
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            (activity as? MainActivity)?.toggleTheme(isChecked)
        }
    }

    private fun showAboutUsDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_about_us)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // --- DEFINE YOUR TEAM HERE ---
        val teamMembers = listOf(
            TeamMember(
                "Ryan Jay B. Madayag",
                "Project Lead",
                "Full-stack developer specializing in AR and maps.",
                R.drawable.ryan
            ),
            TeamMember(
                "Robel Andrew J. Ambahan",
                "Lead Documnenter",
                "Responsible for project documentation and organization.",
                R.drawable.robel
            ),
            TeamMember(
                "Rona Escalona",
                "Quality Assurance",
                "Ensured the app is bug-free and user-friendly through rigorous testing.",
                R.drawable.rona
            ),
            TeamMember(
                "Anika Tabian",
                "UI/UX Designer",
                "Designed the user interface and experience.",
                R.drawable.anika
            ),
            TeamMember(
                "Roan Ayalin",
                "UI/UX Designer",
                "Designed the user interface and experience.",
                R.drawable.roan
            )
        )

        val recyclerView = dialog.findViewById<RecyclerView>(R.id.rvTeamMembers)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = TeamAdapter(teamMembers)

        dialog.findViewById<View>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupNavigationSettings() {
        val prefs = requireContext().getSharedPreferences("arnav_settings", Context.MODE_PRIVATE)

        // 1. Setup Voice Switch
        // Default to 'true' if not set yet
        binding.switchVoiceGuidance.isChecked = prefs.getBoolean("voice_enabled", true)

        binding.switchVoiceGuidance.setOnCheckedChangeListener { _, isChecked ->
            // Save the new state immediately
            prefs.edit().putBoolean("voice_enabled", isChecked).apply()
        }

        // 2. Setup Vibration Switch
        binding.switchVibration.isChecked = prefs.getBoolean("vibration_enabled", true)

        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("vibration_enabled", isChecked).apply()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}