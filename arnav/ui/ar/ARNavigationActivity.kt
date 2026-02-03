package com.campus.arnav.ui.ar

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.campus.arnav.R
import com.campus.arnav.data.model.Route
import com.campus.arnav.databinding.ActivityArNavigationBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ARNavigationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArNavigationBinding
    private var route: Route? = null

    companion object {
        const val EXTRA_ROUTE = "extra_route"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get route from intent
        route = intent.getParcelableExtra(EXTRA_ROUTE)

        setupUI()
    }

    private fun setupUI() {
        binding.apply {
            // Back button
            fabBack.setOnClickListener {
                finish()
            }

            // Show placeholder message
            tvInstruction.text = "AR Navigation"
            tvDistance.text = "AR feature coming soon"

            // Display route info if available
            route?.let { r ->
                tvInstruction.text = "Navigate to destination"
                tvDistance.text = "${r.totalDistance.toInt()}m remaining"
            }
        }

        Toast.makeText(this, "AR Navigation - Coming Soon", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }
}