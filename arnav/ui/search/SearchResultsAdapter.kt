package com.campus.arnav.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.campus.arnav.R
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.BuildingType
import com.campus.arnav.databinding.ItemSearchResultBinding

class SearchResultsAdapter(
    private val onItemClick: (Building) -> Unit
) : ListAdapter<Building, SearchResultsAdapter.ViewHolder>(BuildingDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(building: Building) {
            binding.apply {
                tvName.text = building.name
                tvDescription.text = building.description
                ivIcon.setImageResource(getIconForType(building.type))

                root.setOnClickListener {
                    onItemClick(building)
                }
            }
        }

        private fun getIconForType(type: BuildingType): Int {
            return when (type) {
                BuildingType.ACADEMIC -> R.drawable.ic_school
                BuildingType.LIBRARY -> R.drawable.ic_library
                BuildingType.CAFETERIA -> R.drawable.ic_restaurant
                BuildingType.DORMITORY -> R.drawable.ic_home
                BuildingType.SPORTS -> R.drawable.ic_sports
                BuildingType.ADMINISTRATIVE -> R.drawable.ic_business
                BuildingType.PARKING -> R.drawable.ic_parking
                BuildingType.LANDMARK -> R.drawable.ic_landmark
            }
        }
    }

    class BuildingDiffCallback : DiffUtil.ItemCallback<Building>() {
        override fun areItemsTheSame(oldItem: Building, newItem: Building): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Building, newItem: Building): Boolean {
            return oldItem == newItem
        }
    }
}