package com.tpov.shop.presentation

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import android.util.Log // Added for logging
import com.tpov.shop.R
import com.tpov.shop.domain.ReferralUser

class ReferralAdapter(
    private val context: Context
) : RecyclerView.Adapter<ReferralAdapter.ReferralViewHolder>() {

    private var displayItems: List<ReferralUser> = List(6) { ReferralUser.placeholder("Empty Slot") }

    fun submitList(referralUsers: List<ReferralUser>) {
        val sortedRealUsers = referralUsers.sortedByDescending { it.allOpenBox }

        val newDisplayItems = mutableListOf<ReferralUser>()
        for (i in 0 until 6) {
            if (i < sortedRealUsers.size) {
                newDisplayItems.add(sortedRealUsers[i])
            } else {
                newDisplayItems.add(ReferralUser.placeholder("Empty Slot"))
            }
        }
        displayItems = newDisplayItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReferralViewHolder {
        Log.d("ReferralAdapter", "onCreateViewHolder called")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_referral_user, parent, false)
        return ReferralViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReferralViewHolder, position: Int) {
        val item = displayItems[position]
        Log.d("ReferralAdapter", "onBindViewHolder called for position: $position, item ID: ${item.id}")
        holder.bind(item, context)
    }

    override fun getItemCount(): Int {
        Log.d("ReferralAdapter", "getItemCount called, returning: ${displayItems.size}")
        return displayItems.size // Should always be 6
    }

    class ReferralViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val itemBackgroundLayout: LinearLayout = itemView.findViewById(R.id.ll_item_background)
        private val nicknameTextView: TextView = itemView.findViewById(R.id.tv_nickname)
        private val allOpenBoxValueTextView: TextView = itemView.findViewById(R.id.tv_all_open_box_value)
        private val newBonusBoxValueTextView: TextView = itemView.findViewById(R.id.tv_new_bonus_box_value)
        private val allOpenBoxLabelTextView: TextView = itemView.findViewById(R.id.tv_all_open_box_label)
        private val newBonusBoxLabelTextView: TextView = itemView.findViewById(R.id.tv_new_bonus_box_label)
        private val userAvatarImageView: ImageView = itemView.findViewById(R.id.iv_user_avatar)
        
        // New progress UI elements
        private val progressSection: LinearLayout = itemView.findViewById(R.id.ll_progress_section)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progress_bar_boxes)
        private val progressPercentText: TextView = itemView.findViewById(R.id.tv_progress_percent)
        private val referralStatusText: TextView = itemView.findViewById(R.id.tv_referral_status)
        private val referralActivatedText: TextView = itemView.findViewById(R.id.tv_referral_activated)

        fun bind(user: ReferralUser, context: Context) {
            Log.d("ReferralViewHolder", "Binding item: ${user.nickname}, isPlaceholder: ${ReferralUser.isPlaceholder(user)}, allOpenBox: ${user.allOpenBox}, seasonBox: ${user.seasonBoxCount}")
            val calculatedBonus = if (ReferralUser.isPlaceholder(user)) 0.0 else user.seasonBoxCount / 100.0
            val isActivated = !ReferralUser.isPlaceholder(user) && user.allOpenBox >= 100

            if (ReferralUser.isPlaceholder(user)) {
                nicknameTextView.text = user.nickname
                allOpenBoxValueTextView.text = "-"
                newBonusBoxValueTextView.text = "-"

                val placeholderTextColor = Color.LTGRAY
                nicknameTextView.setTextColor(placeholderTextColor)
                allOpenBoxValueTextView.setTextColor(placeholderTextColor)
                newBonusBoxValueTextView.setTextColor(placeholderTextColor)
                allOpenBoxLabelTextView.setTextColor(placeholderTextColor)
                newBonusBoxLabelTextView.setTextColor(placeholderTextColor)
                userAvatarImageView.alpha = 0.5f

                // Hide the progress section for placeholders
                progressSection.visibility = View.GONE

                itemBackgroundLayout.background = ContextCompat.getDrawable(context, R.drawable.referral_item_placeholder_background)
            } else {
                nicknameTextView.text = user.nickname
                allOpenBoxValueTextView.text = user.allOpenBox.toString()
                newBonusBoxValueTextView.text = String.format("%.1f", calculatedBonus)
                userAvatarImageView.alpha = 1.0f

                val realUserTextColor = Color.WHITE
                nicknameTextView.setTextColor(realUserTextColor)
                allOpenBoxValueTextView.setTextColor(realUserTextColor)
                newBonusBoxValueTextView.setTextColor(ContextCompat.getColor(context, R.color.bonus_text_color))
                allOpenBoxLabelTextView.setTextColor(realUserTextColor)
                newBonusBoxLabelTextView.setTextColor(realUserTextColor)
                
                // Set progress bar and display activation status
                progressSection.visibility = View.VISIBLE
                
                // Calculate progress percentage (max 100%)
                val progressPercentage = if (user.allOpenBox >= 100) 100 else user.allOpenBox
                progressBar.progress = progressPercentage
                progressPercentText.text = "$progressPercentage%"
                
                if (isActivated) {
                    // User has reached 100 boxes - show activation message
                    referralStatusText.visibility = View.GONE
                    referralActivatedText.visibility = View.VISIBLE
                    progressPercentText.visibility = View.GONE  // Hide percent when activated
                    
                    // Optionally highlight the background to indicate activation
                    itemBackgroundLayout.background = ContextCompat.getDrawable(context, R.drawable.referral_item_progress_fill_inverted)
                    // Set progress to max
                    progressBar.progress = 100
                } else {
                    // User still needs more boxes
                    referralStatusText.visibility = View.VISIBLE
                    referralActivatedText.visibility = View.GONE
                    progressPercentText.visibility = View.VISIBLE  // Show percent when not activated
                    
                    val boxesNeeded = 100 - user.allOpenBox
                    referralStatusText.text = "$boxesNeeded boxes to activate"
                    
                    // Set the progress background
                    itemBackgroundLayout.background = ContextCompat.getDrawable(context, R.drawable.referral_item_progress_fill_inverted)
                    val progressDrawable = itemBackgroundLayout.background as LayerDrawable
                    val progressClipDrawable = progressDrawable.findDrawableByLayerId(android.R.id.progress) as android.graphics.drawable.ClipDrawable
                    progressClipDrawable.level = progressPercentage * 100 // Level is 0-10000
                }
            }
        }
    }
}
