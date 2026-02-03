package com.campus.arnav.ui.ar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.campus.arnav.databinding.FragmentArNavigationBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ARNavigationFragment : Fragment() {

    private var _binding: FragmentArNavigationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ARNavigationViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArNavigationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Setup AR navigation
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}