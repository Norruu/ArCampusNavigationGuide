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
import android.widget.ImageView
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.campus.arnav.data.model.TeamMember
import com.campus.arnav.R

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    // Add your tutorial drawable images here
    private val tutorialImages = listOf(
        R.drawable.ryan, // Replace with R.drawable.tutorial_1
        R.drawable.robel,
        R.drawable.rona,
        R.drawable.roan,
        R.drawable.anika,
    )
    private var currentTutorialIndex = 0

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

        // Give function to btnBack
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        setupDarkModeToggle()
        setupNavigationSettings()
        setupTutorialMode()

        binding.btnAboutUs.setOnClickListener {
            showAboutUsDialog()
        }
    }

    private fun setupDarkModeToggle() {
        val prefs = requireContext().getSharedPreferences("arnav_settings", Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("is_dark_mode", false)
        binding.switchDarkMode.isChecked = isDark

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("is_dark_mode", isChecked).apply()
            (activity as? MainActivity)?.toggleTheme(isChecked)
        }
    }

    private fun setupNavigationSettings() {
        val prefs = requireContext().getSharedPreferences("arnav_settings", Context.MODE_PRIVATE)

        binding.switchVoiceGuidance.isChecked = prefs.getBoolean("voice_enabled", true)
        binding.switchVoiceGuidance.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("voice_enabled", isChecked).apply()
        }

        binding.switchVibration.isChecked = prefs.getBoolean("vibration_enabled", true)
        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("vibration_enabled", isChecked).apply()
        }
    }

    // 🆕 Launch the Tutorial Carousel
    private fun setupTutorialMode() {
        binding.btnTutorialMode.setOnClickListener {
            showTutorialDialog()
        }
    }

    private fun showTutorialDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_tutorial)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val ivTutorial = dialog.findViewById<ImageView>(R.id.ivTutorialImage)
        val btnLeft = dialog.findViewById<ImageView>(R.id.btnLeftArrow)
        val btnRight = dialog.findViewById<ImageView>(R.id.btnRightArrow)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseTutorial)

        currentTutorialIndex = 0
        if (tutorialImages.isNotEmpty()) {
            ivTutorial.setImageResource(tutorialImages[currentTutorialIndex])
        }

        // Function to hide/show arrows based on position
        val updateArrows = {
            btnLeft.visibility = if (currentTutorialIndex > 0) View.VISIBLE else View.INVISIBLE
            btnRight.visibility = if (currentTutorialIndex < tutorialImages.size - 1) View.VISIBLE else View.INVISIBLE
        }
        updateArrows()

        btnLeft.setOnClickListener {
            if (currentTutorialIndex > 0) {
                currentTutorialIndex--
                ivTutorial.setImageResource(tutorialImages[currentTutorialIndex])
                updateArrows()
            }
        }

        btnRight.setOnClickListener {
            if (currentTutorialIndex < tutorialImages.size - 1) {
                currentTutorialIndex++
                ivTutorial.setImageResource(tutorialImages[currentTutorialIndex])
                updateArrows()
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showAboutUsDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_about_us)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val teamMembers = listOf(
            TeamMember("Ryan Jay B. Madayag", "Project Lead", "Full-stack developer specializing in AR and maps.", R.drawable.ryan),
            TeamMember("Robel Andrew J. Ambahan", "Lead Documenter", "Responsible for project documentation and organization.", R.drawable.robel),
            TeamMember("Rona Escalona", "Quality Assurance", "Ensured the app is bug-free and user-friendly through rigorous testing.", R.drawable.rona),
            TeamMember("Anika Tabian", "UI/UX Designer", "Designed the user interface and experience.", R.drawable.anika),
            TeamMember("Roan Ayalin", "UI/UX Designer", "Designed the user interface and experience.", R.drawable.roan)
        )

        val recyclerView = dialog.findViewById<RecyclerView>(R.id.rvTeamMembers)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = TeamAdapter(teamMembers)

        dialog.findViewById<View>(R.id.btnClose).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}