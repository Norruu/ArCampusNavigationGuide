package com.campus.arnav.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.campus.arnav.R
import com.campus.arnav.data.model.Building

class BuildingCardAdapter(
    private val onBuildingClick: (Building) -> Unit
) : ListAdapter<Building, BuildingCardAdapter.ViewHolder>(BuildingDiffCallback()) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivImage: ImageView = view.findViewById(R.id.ivBuildingImage)
        val tvName: TextView = view.findViewById(R.id.tvBuildingName)
        val tvDesc: TextView = view.findViewById(R.id.tvBuildingShortDesc)
        val tvType: TextView = view.findViewById(R.id.tvBuildingType)

        fun bind(building: Building) {
            tvName.text = building.name
            tvDesc.text = building.description ?: building.shortName
            tvType.text = building.type.name

            if (building.imageUrl != null) {
                // If you have Glide/Coil:
                // Glide.with(ivImage).load(building.imageUrl).into(ivImage)
                ivImage.setImageResource(R.drawable.bg_topographic)
            } else {
                ivImage.setImageResource(R.drawable.bg_topographic)
            }

            itemView.setOnClickListener { onBuildingClick(building) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_building_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class BuildingDiffCallback : DiffUtil.ItemCallback<Building>() {
        override fun areItemsTheSame(oldItem: Building, newItem: Building) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Building, newItem: Building) = oldItem == newItem
    }
}