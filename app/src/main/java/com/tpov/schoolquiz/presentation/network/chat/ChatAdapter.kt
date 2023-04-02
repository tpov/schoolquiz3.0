//package com.tpov.schoolquiz.presentation.network.chat
//
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import androidx.recyclerview.widget.DiffUtil
//import androidx.recyclerview.widget.ListAdapter
//import androidx.recyclerview.widget.RecyclerView
//import com.tpov.schoolquiz.R
//import com.tpov.schoolquiz.data.database.entities.ChatEntity
//
//class ChatAdapter : ListAdapter<ChatEntity, ChatViewHolder>(ChatDiffCallback()) {
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
//        val view = LayoutInflater.from(parent.context)
//            .inflate(R.layout.chat_item, parent, false)
//        return ChatViewHolder(view)
//    }
//
//    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
//        val chatEntity = getItem(position)
//        holder.bind(chatEntity)
//    }
//}
//
//class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//    fun bind(chatEntity: ChatEntity) {
//        itemView.timeTextView.text = chatEntity.time
//        itemView.userTextView.text = chatEntity.user
//        itemView.msgTextView.text = chatEntity.msg
//    }
//}
//
//class ChatDiffCallback : DiffUtil.ItemCallback<ChatEntity>() {
//    override fun areItemsTheSame(oldItem: ChatEntity, newItem: ChatEntity): Boolean {
//        return oldItem.id == newItem.id
//    }
//
//    override fun areContentsTheSame(oldItem: ChatEntity, newItem: ChatEntity): Boolean {
//        return oldItem == newItem
//    }
//}
