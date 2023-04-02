/*
package com.tpov.schoolquiz.presentation.network.chat

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Database
import com.google.firebase.database.FirebaseDatabase
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.RepositoryFBImpl
import com.tpov.schoolquiz.data.database.QuizDao
import com.tpov.schoolquiz.data.database.QuizDatabase
import com.tpov.schoolquiz.domain.GetChatUseCase
import com.tpov.schoolquiz.domain.repository.RepositoryFB
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory

class ChatFragment : Fragment() {
    private lateinit var chatViewModel: ChatViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Инициализация ViewModel
        val chatRepository = RepositoryFBImpl(
            FirebaseDatabase.getInstance(),
            QuizDatabase
        )
        val chatDataUseCase = GetChatUseCase(chatRepository)
        chatViewModel = ViewModelProvider(this, ViewModelFactory(chatDataUseCase))
            .get(ChatViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_chat, container, false)
        val chatAdapter = ChatAdapter()
        val chatRecyclerView: RecyclerView = root.findViewById(R.id.chatRecyclerView)
        chatRecyclerView.adapter = chatAdapter
        chatViewModel.chatData.observe(viewLifecycleOwner) { chatList ->
            chatAdapter.submitList(chatList)
        }
        return root
    }
}*/
