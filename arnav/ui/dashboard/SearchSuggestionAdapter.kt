package com.campus.arnav.ui.dashboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.campus.arnav.R
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.BuildingType

class SearchSuggestionAdapter(
    private val onClick: (Building) -> Unit
) : ListAdapter<Building, SearchSuggestionAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        private val ivIcon: ImageView = root.getChildAt(0) as ImageView
        private val textContainer: LinearLayout = root.getChildAt(1) as LinearLayout
        private val tvName: TextView = textContainer.getChildAt(0) as TextView
        private val tvType: TextView = textContainer.getChildAt(1) as TextView

        fun bind(building: Building) {
            tvName.text = building.name
            tvType.text = building.type.name.lowercase().replaceFirstChar { it.uppercase() }
            ivIcon.setImageResource(getIcon(building.type))
            root.setOnClickListener { onClick(building) }
        }

        private fun getIcon(type: BuildingType): Int {
            return when (type) {
                BuildingType.ACADEMIC -> R.drawable.ic_school
                BuildingType.LIBRARY -> R.drawable.ic_library
                BuildingType.CAFETERIA -> R.drawable.ic_restaurant
                BuildingType.SPORTS -> R.drawable.ic_sports
                BuildingType.ADMINISTRATIVE -> R.drawable.ic_business
                BuildingType.LANDMARK -> R.drawable.ic_landmark
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val ctx = parent.context
        val dp = { value: Int -> (value * ctx.resources.displayMetrics.density).toInt() }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            // Ripple effect
            val attrs = intArrayOf(android.R.attr.selectableItemBackground)
            val ta = ctx.obtainStyledAttributes(attrs)
            foreground = ta.getDrawable(0)
            ta.recycle()
        }

        val icon = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            setColorFilter(ContextCompat.getColor(ctx, R.color.primary_green))
        }

        val textContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            }
        }

        val tvName = TextView(ctx).apply {
            setTextColor(ContextCompat.getColor(ctx, R.color.primary_green_dark))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val tvType = TextView(ctx).apply {
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary_light))
            textSize = 12f
        }

        textContainer.addView(tvName)
        textContainer.addView(tvType)
        root.addView(icon)
        root.addView(textContainer)

        // Divider at bottom
        val wrapper = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        wrapper.addView(root)

        val divider = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            ).apply {
                marginStart = dp(52)
            }
        }
        wrapper.addView(divider)

        return ViewHolder(root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<Building>() {
        override fun areItemsTheSame(oldItem: Building, newItem: Building) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Building, newItem: Building) = oldItem == newItem
    }
}