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

    private var users: List<ReferralUser> = emptyList()

    fun submitList(referralUsers: List<ReferralUser>) {
        users = referralUsers.sortedByDescending { it.allOpenBox }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReferralViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_referral_user, parent, false)
        return ReferralViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReferralViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user, context)
    }

    override fun getItemCount(): Int {
        return users.size
    }

    class ReferralViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val allOpenBoxTextView: TextView = itemView.findViewById(R.id.tv_all_open_box)
        private val newBonusBoxTextView: TextView = itemView.findViewById(R.id.tv_new_bonus_box)
        private val newBonusBoxLabelTextView: TextView = itemView.findViewById(R.id.tv_new_bonus_box_label)
        private val itemBackgroundLayout: LinearLayout = itemView.findViewById(R.id.ll_item_background)

        fun bind(user: ReferralUser, context: Context) {
            allOpenBoxTextView.text = user.allOpenBox.toString()
            newBonusBoxTextView.text = user.newBonusBox.toString()

            val isItemFullyInactive = user.allOpenBox == 0

            if (isItemFullyInactive) {
                allOpenBoxTextView.setTextColor(ContextCompat.getColor(context, R.color.referral_item_text_inactive))
                newBonusBoxTextView.visibility = View.INVISIBLE
                newBonusBoxLabelTextView.visibility = View.INVISIBLE
                // Use the predefined inactive background drawable
                itemBackgroundLayout.background = ContextCompat.getDrawable(context, R.drawable.referral_item_background_inactive)
            } else {
                allOpenBoxTextView.setTextColor(Color.WHITE) // Active text color
                newBonusBoxTextView.visibility = View.VISIBLE
                newBonusBoxLabelTextView.visibility = View.VISIBLE

                val progress = user.allOpenBox.coerceIn(0, 100) // Progress from 0 to 100 for color calculation

                // Colors for interpolation when active (allOpenBox > 0)
                // At progress 0, it's light. At progress 100, it's dark.
                val colorAtMinProgress = ContextCompat.getColor(context, R.color.referral_item_bg_progress_0) // Lightest
                val colorAtMaxProgress = ContextCompat.getColor(context, R.color.referral_item_bg_progress_100) // Darkest (target)

                val normalizedProgress = progress / 100f

                val red = Color.red(colorAtMinProgress) + (Color.red(colorAtMaxProgress) - Color.red(colorAtMinProgress)) * normalizedProgress
                val green = Color.green(colorAtMinProgress) + (Color.green(colorAtMaxProgress) - Color.green(colorAtMinProgress)) * normalizedProgress
                val blue = Color.blue(colorAtMinProgress) + (Color.blue(colorAtMaxProgress) - Color.blue(colorAtMinProgress)) * normalizedProgress
                val interpolatedColor = Color.rgb(red.toInt(), green.toInt(), blue.toInt())

                val backgroundDrawable = GradientDrawable()
                backgroundDrawable.setColor(interpolatedColor)
                val cornerRadius = context.resources.getDimensionPixelSize(R.dimen.referral_item_corner_radius).toFloat()
                backgroundDrawable.cornerRadius = cornerRadius

                itemBackgroundLayout.background = backgroundDrawable
            }
        }
    }
}
