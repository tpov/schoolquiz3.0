package com.tpov.schoolquiz.presentation.network.chat

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.fierbase.Chat
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.custom.Values.context
import com.tpov.schoolquiz.presentation.custom.Values.getColorNickname

class ChatAdapter : ListAdapter<Chat, ChatAdapter.ChatViewHolder>(ChatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.chat_item, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    //todo date -> StickyHeader
    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timeTextView: TextView = itemView.findViewById(R.id.messageTimeTextView)
        private val userTextView: TextView = itemView.findViewById(R.id.messageUserTextView)
        private val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)
        private val iconImageView: ImageView = itemView.findViewById(R.id.messageIconImageView)
        private val vUserMassage1: View = itemView.findViewById(R.id.v_user_massage1)
        private val vUserMassage2: View = itemView.findViewById(R.id.v_user_massage2)

        fun bind(chat: Chat) {
            timeTextView.text = chat.time
            userTextView.text = chat.user
            messageTextView.text = chat.msg

            userTextView.setTextColor(getColorNickname(chat.importance))
            if (chat.importance == 7) messageTextView.setTextColor(Color.YELLOW)

            userTextView.setShadowLayer(4F, 0F, 0F,
                when (userTextView.currentTextColor) {
                    ContextCompat.getColor(context, R.color.default_nick_color6) -> Color.WHITE
                    ContextCompat.getColor(context, R.color.default_nick_color7) -> Color.YELLOW
                    else -> Color.TRANSPARENT
                }
            )
            userTextView.setTypeface(null, Typeface.BOLD)
            vUserMassage1.visibility = if (chat.tpovId == getTpovId()) View.VISIBLE
            else View.GONE
            vUserMassage2.visibility = if (chat.tpovId == getTpovId()) View.VISIBLE
            else View.GONE
            // Здесь вы можете загрузить изображение иконки с помощью библиотеки, например, Glide или Picasso
             Glide.with(itemView).load(chat.icon).into(iconImageView)
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<Chat>() {
        override fun areItemsTheSame(oldItem: Chat, newItem: Chat): Boolean {
            return oldItem.time == newItem.time && oldItem.user == newItem.user
        }

        override fun areContentsTheSame(oldItem: Chat, newItem: Chat): Boolean {
            return oldItem == newItem
        }
    }

}
