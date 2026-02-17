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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}