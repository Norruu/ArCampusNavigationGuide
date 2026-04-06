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

            // 1. Format the building name to match Android's drawable rules
            // This takes "Main Library!" and turns it into "main_library"
            val formattedName = building.name
                .lowercase()
                .replace(" ", "_")
                .replace(Regex("[^a-z0-9_]"), "")

            val context = itemView.context

            // 2. Ask Android to find a drawable with that exact formatted name
            val resourceId = context.resources.getIdentifier(
                formattedName,
                "drawable",
                context.packageName
            )

            // 3. If it found a match (resourceId != 0), show the photo!
            // Otherwise, fall back to your default topographic background.
            if (resourceId != 0) {
                ivImage.setImageResource(resourceId)
                ivImage.scaleType = ImageView.ScaleType.CENTER_CROP // Makes it fill the card nicely
            } else {
                ivImage.setImageResource(R.drawable.bg_topographic)
                ivImage.scaleType = ImageView.ScaleType.CENTER_CROP
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