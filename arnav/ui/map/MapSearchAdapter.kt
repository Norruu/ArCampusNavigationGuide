package com.campus.arnav.ui.map

import android.graphics.Color
import android.graphics.Typeface
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

class MapSearchAdapter(
    private val onClick: (Building) -> Unit
) : ListAdapter<Building, MapSearchAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(
        root: View,
        private val ivIcon: ImageView,
        private val tvName: TextView,
        private val tvType: TextView
    ) : RecyclerView.ViewHolder(root) {

        fun bind(building: Building) {
            tvName.text = building.name
            tvType.text = building.type.name.lowercase().replaceFirstChar { it.uppercase() }
            ivIcon.setImageResource(getIcon(building.type))
            itemView.setOnClickListener { onClick(building) }
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
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
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
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dp(12) }
        }

        val tvName = TextView(ctx).apply {
            setTextColor(ContextCompat.getColor(ctx, R.color.primary_green_dark))
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
        }

        val tvType = TextView(ctx).apply {
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary_light))
            textSize = 12f
        }

        textContainer.addView(tvName)
        textContainer.addView(tvType)

        val arrow = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                marginStart = dp(8)
            }
            setImageResource(R.drawable.ic_arrow_right)
            setColorFilter(ContextCompat.getColor(ctx, R.color.text_secondary_light))
        }

        root.addView(icon)
        root.addView(textContainer)
        root.addView(arrow)

        return ViewHolder(root, icon, tvName, tvType)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<Building>() {
        override fun areItemsTheSame(a: Building, b: Building) = a.id == b.id
        override fun areContentsTheSame(a: Building, b: Building) = a == b
    }
}