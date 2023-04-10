package com.tpov.schoolquiz.presentation.network.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.fierbase.Chat

class ChatAdapter : ListAdapter<Chat, ChatAdapter.ChatViewHolder>(ChatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.chat_item, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timeTextView: TextView = itemView.findViewById(R.id.messageTimeTextView)
        private val userTextView: TextView = itemView.findViewById(R.id.messageUserTextView)
        private val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)
        private val iconImageView: ImageView = itemView.findViewById(R.id.messageIconImageView)

        fun bind(chat: Chat) {
            timeTextView.text = chat.time
            userTextView.text = chat.user
            messageTextView.text = chat.msg
            // Здесь вы можете загрузить изображение иконки с помощью библиотеки, например, Glide или Picasso
            // Glide.with(itemView).load(chat.icon).into(iconImageView)
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
