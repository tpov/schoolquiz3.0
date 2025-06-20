package com.tpov.shop.presentation

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tpov.shop.R
import com.tpov.shop.domain.ReferralUser // Assumes this path is correct after domain class creation

class ReferralAdapter(
    private val context: Context
) : RecyclerView.Adapter<ReferralAdapter.ReferralViewHolder>() {

    private var displayItems: List<ReferralUser> = List(6) { ReferralUser.placeholder("initial_$it") }

    fun submitList(referralUsers: List<ReferralUser>) {
        val sortedRealUsers = referralUsers.sortedByDescending { it.allOpenBox }

        val newDisplayItems = mutableListOf<ReferralUser>()
        for (i in 0 until 6) {
            if (i < sortedRealUsers.size) {
                newDisplayItems.add(sortedRealUsers[i])
            } else {
                newDisplayItems.add(ReferralUser.placeholder("empty_$i"))
            }
        }
        displayItems = newDisplayItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReferralViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_referral_user, parent, false)
        return ReferralViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReferralViewHolder, position: Int) {
        val item = displayItems[position]
        holder.bind(item, context)
    }

    override fun getItemCount(): Int {
        return displayItems.size // Should always be 6
    }

    class ReferralViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val itemBackgroundLayout: LinearLayout = itemView.findViewById(R.id.ll_item_background)
        private val nicknameTextView: TextView = itemView.findViewById(R.id.tv_nickname)
        private val allOpenBoxValueTextView: TextView = itemView.findViewById(R.id.tv_all_open_box_value)
        private val seasonBoxValueTextView: TextView = itemView.findViewById(R.id.tv_season_box_value)
        private val newBonusBoxValueTextView: TextView = itemView.findViewById(R.id.tv_new_bonus_box_value)

        private val allOpenBoxLabelTextView: TextView = itemView.findViewById(R.id.tv_all_open_box_label)
        private val seasonBoxLabelTextView: TextView = itemView.findViewById(R.id.tv_season_box_label)
        private val newBonusBoxLabelTextView: TextView = itemView.findViewById(R.id.tv_new_bonus_box_label)

        fun bind(user: ReferralUser, context: Context) {
            val calculatedBonus = if (ReferralUser.isPlaceholder(user)) 0 else user.seasonBoxCount / 100

            if (ReferralUser.isPlaceholder(user)) {
                nicknameTextView.text = user.nickname // "Empty Slot"
                allOpenBoxValueTextView.text = "-"
                seasonBoxValueTextView.text = "-"
                newBonusBoxValueTextView.text = "-"

                val placeholderTextColor = Color.LTGRAY
                nicknameTextView.setTextColor(placeholderTextColor)
                allOpenBoxValueTextView.setTextColor(placeholderTextColor)
                seasonBoxValueTextView.setTextColor(placeholderTextColor)
                newBonusBoxValueTextView.setTextColor(placeholderTextColor)
                allOpenBoxLabelTextView.setTextColor(placeholderTextColor)
                seasonBoxLabelTextView.setTextColor(placeholderTextColor)
                newBonusBoxLabelTextView.setTextColor(placeholderTextColor)

                itemBackgroundLayout.background = ContextCompat.getDrawable(context, R.drawable.referral_item_placeholder_background)
            } else {
                nicknameTextView.text = user.nickname
                allOpenBoxValueTextView.text = user.allOpenBox.toString()
                seasonBoxValueTextView.text = user.seasonBoxCount.toString() // Display raw seasonBoxCount
                newBonusBoxValueTextView.text = calculatedBonus.toString()

                val realUserTextColor = Color.WHITE
                nicknameTextView.setTextColor(realUserTextColor)
                allOpenBoxValueTextView.setTextColor(realUserTextColor)
                seasonBoxValueTextView.setTextColor(realUserTextColor)
                newBonusBoxValueTextView.setTextColor(realUserTextColor)
                allOpenBoxLabelTextView.setTextColor(realUserTextColor)
                seasonBoxLabelTextView.setTextColor(realUserTextColor)
                newBonusBoxLabelTextView.setTextColor(realUserTextColor)

                itemBackgroundLayout.background = ContextCompat.getDrawable(context, R.drawable.referral_item_progress_fill_inverted)
                val progressDrawable = itemBackgroundLayout.background as LayerDrawable
                val progressClipDrawable = progressDrawable.findDrawableByLayerId(android.R.id.progress) as android.graphics.drawable.ClipDrawable

                val progressPercentage = if (user.allOpenBox >= 100) 100 else user.allOpenBox % 100
                progressClipDrawable.level = progressPercentage * 100 // Level is 0-10000
            }
        }
    }
}
