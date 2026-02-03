package com.campus.arnav.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.campus.arnav.R
import com.campus.arnav.data.model.Building
import com.campus.arnav.data.model.BuildingType
import com.campus.arnav.databinding.ItemRecentSearchBinding

class RecentSearchesAdapter(
    private val onItemClick: (Building) -> Unit,
    private val onRemoveClick: (Building) -> Unit
) : ListAdapter<Building, RecentSearchesAdapter.ViewHolder>(BuildingDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentSearchBinding.inflate(
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
        private val binding: ItemRecentSearchBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(building: Building) {
            binding.apply {
                tvName.text = building.name
                tvDescription.text = building.description
                ivIcon.setImageResource(R.drawable.ic_history)

                root.setOnClickListener {
                    onItemClick(building)
                }

                btnRemove.setOnClickListener {
                    onRemoveClick(building)
                }
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