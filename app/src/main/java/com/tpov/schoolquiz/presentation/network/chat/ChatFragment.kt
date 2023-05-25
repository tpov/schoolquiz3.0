package com.tpov.schoolquiz.presentation.network.chat

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.data.fierbase.Chat
import com.tpov.schoolquiz.databinding.FragmentChatBinding
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import com.tpov.schoolquiz.presentation.fragment.BaseFragment
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.coroutines.InternalCoroutinesApi
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class ChatFragment : BaseFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @OptIn(InternalCoroutinesApi::class)
    private val component by lazy {
        (requireActivity().application as MainApp).component
    }

    private lateinit var chatViewModel: ChatViewModel
    private lateinit var chatAdapter: ChatAdapter
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatViewModel = ViewModelProvider(this, viewModelFactory)[ChatViewModel::class.java]
        setupRecyclerView()
        observeChatData()

        val sharedPref = context?.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val tpovId = sharedPref?.getInt("tpovId", 0) ?: 0

        binding.sendMessageButton.setOnClickListener {
            val message = binding.messageEditText.text.toString().trim()
            if (message.isNotEmpty()) {
                val currentTime = TimeManager.getCurrentTime()
                        val chatMessage = Chat(
                            time = currentTime,
                            user = chatViewModel.getProfile(tpovId).nickname ?: "",
                            msg = message,
                            importance = 0,
                            personalSms = 0,
                            icon = chatViewModel.getProfile(tpovId).logo.toString(),
                            0
                        )
                        sendMessage(chatMessage)
                binding.messageEditText.setText("")
            }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onAttach(context: Context) {
        component.inject(this)
        super.onAttach(context)
    }

    private fun sendMessage(chat: Chat) {
        val chatRef = FirebaseDatabase.getInstance().getReference("chat")
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val chatDateRef = chatRef.child(currentDate)
        val chatId = chatDateRef.push().key
        if (chatId != null) {
            chatDateRef.child(chatId).setValue(chat)
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
        }
    }

    override fun onStop() {
        super.onStop()
        chatViewModel.removeChatListener()
    }

    private fun observeChatData() {
        chatAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                val layoutManager = binding.chatRecyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                // Проверяем, находится ли пользователь ниже 5 последних элементов
                if (chatAdapter.itemCount != 0 && lastVisibleItemPosition >= chatAdapter.itemCount - 6) {
                    binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                }
            }
        })

        chatViewModel.chatData().observe(viewLifecycleOwner) { chatEntityList ->
            val chatList = convertChatEntityListToChatList(chatEntityList)
            chatAdapter.submitList(chatList)
            if (chatList.isNotEmpty()) binding.chatRecyclerView.scrollToPosition(chatList.size - 1)
        }
    }

    private fun convertChatEntityListToChatList(chatEntityList: List<ChatEntity>): List<Chat> {
        return chatEntityList.map { chatEntity ->
            Chat(
                time = chatEntity.time,
                user = chatEntity.user,
                msg = chatEntity.msg,
                importance = chatEntity.importance,
                personalSms = chatEntity.personalSms,
                icon = chatEntity.icon,
                rating = chatEntity.rating
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = ChatFragment()
    }
}
