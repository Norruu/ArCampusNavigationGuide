package com.campus.arnav.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.campus.arnav.R
import com.campus.arnav.data.model.TeamMember

class TeamAdapter(private val team: List<TeamMember>) :
    RecyclerView.Adapter<TeamAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.ivTeamMember)
        val name: TextView = view.findViewById(R.id.tvName)
        val role: TextView = view.findViewById(R.id.tvRole)
        val desc: TextView = view.findViewById(R.id.tvDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_team_member, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = team[position]
        holder.name.text = member.name
        holder.role.text = member.role
        holder.desc.text = member.description
        holder.image.setImageResource(member.imageResId)
    }

    override fun getItemCount() = team.size
}