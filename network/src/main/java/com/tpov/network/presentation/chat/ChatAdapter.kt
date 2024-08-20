package com.tpov.network.presentation.chat

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tpov.common.data.core.SharedPreferencesManager.getTpovId
import com.tpov.common.presentation.utils.Values.getColorNickname
import com.tpov.network.R
import com.tpov.network.data.models.local.ChatEntity

class ChatAdapter : ListAdapter<ChatEntity, ChatAdapter.ChatViewHolder>(ChatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.chat_item, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        if (position > 0 && getItem(position).user == getItem(position - 1).user) {
            holder.hideUserInfo()
        } else {
            holder.showUserInfo()
        }

        holder.bind(getItem(position))
    }


    //todo date -> StickyHeader
    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timeTextView: TextView =
            itemView.findViewById(R.id.messageTimeTextView)
        private val userTextView: TextView =
            itemView.findViewById(R.id.messageUserTextView)
        private val messageTextView: TextView =
            itemView.findViewById(R.id.messageTextView)
        private val iconImageView: ImageView =
            itemView.findViewById(R.id.messageIconImageView)
        private val vUserMassage1: View =
            itemView.findViewById(R.id.v_user_massage1)
        private val vUserMassage2: View =
            itemView.findViewById(R.id.v_user_massage2)
        private val textContainer: LinearLayout =
            itemView.findViewById(R.id.textContainer)
        private val main_Linear_layout: LinearLayout =
            itemView.findViewById(R.id.main_Linear_layout)

        fun hideUserInfo() {
            timeTextView.visibility = View.GONE
            userTextView.visibility = View.GONE
            iconImageView.visibility = View.GONE
            messageTextView.setPadding(150, 0, 0, 0)
            main_Linear_layout.setPadding(30, 0, 30, 0)
        }

        fun showUserInfo() {
            timeTextView.visibility = View.VISIBLE
            userTextView.visibility = View.VISIBLE
            iconImageView.visibility = View.VISIBLE
            userTextView.paintFlags = userTextView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            messageTextView.setPadding(0, 0, 0, 0)
            main_Linear_layout.setPadding(30, 30, 30, 0)

        }


        fun bind(chat: ChatEntity) {
            timeTextView.text = chat.time
            userTextView.text = chat.user
            messageTextView.text = chat.msg

            userTextView.setTextColor(getColorNickname(chat.importance))
            if (chat.importance == 7) messageTextView.setTextColor(Color.YELLOW)


            userTextView.setShadowLayer(
                8F, 0F, 0F,
                when (userTextView.currentTextColor) {
                    ContextCompat.getColor(
                        itemView.context,
                        com.tpov.common.R.color.default_nick_color6
                    ) -> Color.WHITE

                    ContextCompat.getColor(
                        itemView.context,
                        com.tpov.common.R.color.default_nick_color7
                    ) -> Color.YELLOW

                    else -> Color.TRANSPARENT
                }
            )
            userTextView.setTypeface(null, Typeface.BOLD)
            vUserMassage1.visibility = if (chat.tpovId == getTpovId()) View.VISIBLE
            else View.GONE
            vUserMassage2.visibility = if (chat.tpovId == getTpovId()) View.VISIBLE
            else View.GONE
            Glide.with(itemView).load(chat.icon).into(iconImageView)
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<ChatEntity>() {
        override fun areItemsTheSame(oldItem: ChatEntity, newItem: ChatEntity): Boolean {
            return oldItem.time == newItem.time && oldItem.user == newItem.user
        }

        override fun areContentsTheSame(oldItem: ChatEntity, newItem: ChatEntity): Boolean {
            return oldItem == newItem
        }
    }

}
