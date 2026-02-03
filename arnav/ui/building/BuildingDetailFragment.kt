package com.campus.arnav.ui.building

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.campus.arnav.databinding.FragmentBuildingDetailBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BuildingDetailFragment : Fragment() {

    private var _binding: FragmentBuildingDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BuildingDetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBuildingDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Setup building detail UI
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}