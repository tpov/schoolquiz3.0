package com.tpov.schoolquiz.presentation.network.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.RadarData
import com.github.mikephil.charting.data.RadarDataSet
import com.github.mikephil.charting.data.RadarEntry
import com.github.mikephil.charting.interfaces.datasets.IRadarDataSet
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.shoppinglist.utils.TimeManager

class ProfileAdapter(
    private val profiles: List<ProfileEntity>
) : RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {

    inner class ProfileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: TextView = view.findViewById(R.id.iv_icon)
        val tvTpovId: TextView = view.findViewById(R.id.tv_tpov_id)
        val tvNickname: TextView = view.findViewById(R.id.tv_nickname)
        val imvPremium: ImageView = view.findViewById(R.id.imv_premium)
        val tvLanguage: TextView = view.findViewById(R.id.tv_language)
        val imvTrophy: TextView = view.findViewById(R.id.imv_trophy)
        val radarChart: RadarChart = view.findViewById(R.id.radar_chart)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.profile_item, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val profile = profiles[position]

        holder.ivIcon.text = profile.logo.toString()
        holder.tvTpovId.text = profile.tpovId.toString()
        holder.tvNickname.text = profile.nickname
        if (profile.datePremium > TimeManager.getCurrentTime()) holder.imvPremium.visibility =
            View.VISIBLE
        else holder.imvPremium.visibility = View.GONE
        holder.tvLanguage.text = profile.languages
        holder.imvTrophy.text = profile.trophy
        val profiles = listOf(4f, 3f, 2f, 5f, 4f, 3f, 2f)


        // Настройка RadarChart
        val entries = profiles.map { RadarEntry(it) }
        val radarDataSet = RadarDataSet(entries, "Label")
        val radarData = RadarData(listOf<IRadarDataSet>(radarDataSet))
        holder.radarChart.data = radarData
        holder.radarChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        holder.radarChart.invalidate()
    }

    override fun getItemCount(): Int {
        return profiles.size
    }
}
