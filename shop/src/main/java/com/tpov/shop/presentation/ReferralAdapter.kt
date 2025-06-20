package com.tpov.shop.presentation

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tpov.shop.R
import com.tpov.shop.domain.ReferralUser

class ReferralAdapter(
    private val context: Context
) : RecyclerView.Adapter<ReferralAdapter.ReferralViewHolder>() {

    private var displayItems: List<ReferralUser> = List(6) { ReferralUser.placeholder("initial_$it") }

    fun submitList(referralUsers: List<ReferralUser>) {
        val sortedRealUsers = referralUsers.sortedByDescending { it.allOpenBox } // Or seasonBoxCount for sorting? User requested allOpenBox initially.

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

        // Labels (can be hidden for placeholders if needed)
        private val allOpenBoxLabelTextView: TextView = itemView.findViewById(R.id.tv_all_open_box_label)
        private val seasonBoxLabelTextView: TextView = itemView.findViewById(R.id.tv_season_box_label)
        private val newBonusBoxLabelTextView: TextView = itemView.findViewById(R.id.tv_new_bonus_box_label)


        fun bind(user: ReferralUser, context: Context) {
            if (ReferralUser.isPlaceholder(user)) {
                nicknameTextView.text = user.nickname // "Empty Slot"
                allOpenBoxValueTextView.text = "-"
                seasonBoxValueTextView.text = "-/-"
                newBonusBoxValueTextView.text = "-"

                // Optionally hide labels or set them to a dimmer white if needed, for now, they stay.
                // Ensure text color is white for placeholder text too, but it's less prominent.
                nicknameTextView.setTextColor(Color.LTGRAY) // Slightly dimmer for "Empty Slot"
                allOpenBoxValueTextView.setTextColor(Color.LTGRAY)
                seasonBoxValueTextView.setTextColor(Color.LTGRAY)
                newBonusBoxValueTextView.setTextColor(Color.LTGRAY)

                allOpenBoxLabelTextView.setTextColor(Color.LTGRAY)
                seasonBoxLabelTextView.setTextColor(Color.LTGRAY)
                newBonusBoxLabelTextView.setTextColor(Color.LTGRAY)


                itemBackgroundLayout.background = ContextCompat.getDrawable(context, R.drawable.referral_item_background_inactive)
            } else {
                nicknameTextView.text = user.nickname
                allOpenBoxValueTextView.text = user.allOpenBox.toString()
                seasonBoxValueTextView.text = "${user.seasonBoxCount}/100" // Assuming seasonBoxCount is 0-100
                newBonusBoxValueTextView.text = user.newBonusBox.toString()

                // Ensure all text is bright white for real users
                nicknameTextView.setTextColor(Color.WHITE)
                allOpenBoxValueTextView.setTextColor(Color.WHITE)
                seasonBoxValueTextView.setTextColor(Color.WHITE)
                newBonusBoxValueTextView.setTextColor(Color.WHITE)

                allOpenBoxLabelTextView.setTextColor(Color.WHITE)
                seasonBoxLabelTextView.setTextColor(Color.WHITE)
                newBonusBoxLabelTextView.setTextColor(Color.WHITE)

                itemBackgroundLayout.background = ContextCompat.getDrawable(context, R.drawable.referral_item_progress_fill)
                val progressDrawable = itemBackgroundLayout.background as LayerDrawable
                val progressClipDrawable = progressDrawable.findDrawableByLayerId(android.R.id.progress) as android.graphics.drawable.ClipDrawable

                val progressPercentage = user.seasonBoxCount.coerceIn(0, 100)
                progressClipDrawable.level = progressPercentage * 100 // Level is 0-10000
            }
        }
    }
}
