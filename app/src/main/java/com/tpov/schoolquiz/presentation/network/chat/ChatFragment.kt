package com.tpov.schoolquiz.presentation.network.chat

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.data.fierbase.Chat
import com.tpov.schoolquiz.databinding.FragmentChatBinding
import com.tpov.schoolquiz.presentation.*
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.custom.Values.getImportance
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import com.tpov.schoolquiz.presentation.fragment.BaseFragment
import com.tpov.schoolquiz.presentation.network.event.log
import com.tpov.userguide.Options
import com.tpov.userguide.UserGuide
import kotlinx.coroutines.*
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

    private lateinit var message: String
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var chatAdapter: ChatAdapter
    private var binding: FragmentChatBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onResume() {
        super.onResume()

        CoroutineScope(Dispatchers.IO).launch {
            observeChatData()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatViewModel = ViewModelProvider(this, viewModelFactory)[ChatViewModel::class.java]
        setupRecyclerView()

        val tpovId = getTpovId()
        try {
            val pInfo = context?.packageManager?.getPackageInfo(requireContext().packageName, 0)
            val versionName = pInfo?.versionName
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo?.longVersionCode?.toInt() // Use longVersionCode for Android P and later
            } else {
                pInfo?.versionCode
            }
            UserGuide(requireContext()).addNotification(
                id = this.getString(R.string.userguide_chat_rules_text).hashCode(),
                text = this.getString(R.string.userguide_chat_rules_text),
                titleText = this.getString(R.string.userguide_chat_rules_title),
                options = Options(countKeyVersion = versionCode ?: 0),
                icon = resources.getDrawable(R.drawable.nav_chat)
            )

        } catch (e: PackageManager.NameNotFoundException) {
        }

        binding?.sendMessageButton?.setOnClickListener {
            val message = binding?.messageEditText?.text.toString().trim()
            if (message.isNotEmpty()) {
                val currentTime = getCurrentTimeKiev()

                val chatMessage = Chat(
                    time = currentTime,
                    user = chatViewModel.getProfile(tpovId).nickname ?: getString(R.string.error_get_profile),
                    msg = message,
                    importance = getImportance(chatViewModel.getProfile(tpovId)),
                    personalSms = DEFAULT_PERSONAL_SMS_IN_CHAT,
                    icon = chatViewModel.getProfile(tpovId).logo.toString(),
                    rating = DEFAULT_RATING_IN_CHAT,
                    reaction = DEFAULT_REACTION_IN_CHAT,
                    tpovId = getTpovId()
                )
                sendMessage(chatMessage)
                binding?.messageEditText?.setText("")
            }
        }
    }

    private fun getCurrentTimeKiev(): String {
        val timeZone = TimeZone.getTimeZone(DEFAULT_LOCAL_IN_GET_CHAT)
        val dateFormat = SimpleDateFormat(DEFAULT_DATA_IN_GET_CHAT, Locale.getDefault())
        dateFormat.timeZone = timeZone
        return dateFormat.format(Date())
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onAttach(context: Context) {
        component.inject(this)
        super.onAttach(context)
    }

    private fun sendMessage(chat: Chat) {
        val chatRef = FirebaseDatabase.getInstance().getReference("chat")

        val dateFormat = SimpleDateFormat(DEFAULT_DATA_IN_SEND_CHAT, Locale.getDefault())
        val currentDate = dateFormat.format(Date())

        val chatDateRef = chatRef.child(currentDate)
        val chatId = chatDateRef.push().key

        if (chatId != null) {
            chatDateRef.child(chatId).setValue(chat).addOnSuccessListener {
                log("awdawdawdaw add")
                SharedPreferencesManager.addCountSendMassage()
            }
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding!!.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
        }
    }

    override fun onPause() {
        super.onPause()
        chatViewModel.removeChatListener()
    }


    private suspend fun observeChatData() {
        chatAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                val layoutManager = binding?.chatRecyclerView?.layoutManager as LinearLayoutManager
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                // Проверяем, находится ли пользователь ниже 5 последних элементов
                if (chatAdapter.itemCount != 0 && lastVisibleItemPosition >= chatAdapter.itemCount - 6) {
                    binding?.chatRecyclerView?.smoothScrollToPosition(chatAdapter.itemCount - 1)
                }
            }
        })
        withContext(Dispatchers.Main) {
            chatViewModel.chatData().observe(viewLifecycleOwner) { chatEntityList ->
                val chatList = convertChatEntityListToChatList(chatEntityList)

                if (chatList.isNotEmpty()) {
                    val lastChat = chatList.last()
                    val lastMessage = lastChat.msg

                    // Побуквенное добавление последнего сообщения в список чата в основном потоке
                    val handler = Handler(Looper.getMainLooper())
                    val charList = mutableListOf<Char>()
                    lastMessage.forEachIndexed { index, char ->
                        charList.add(char)
                        val updatedChatList = chatList.toMutableList()
                        updatedChatList[updatedChatList.indexOf(lastChat)] =
                            lastChat.copy(msg = charList.joinToString(""))
                        handler.postDelayed(
                            {
                                chatAdapter.submitList(updatedChatList.toList())
                                binding?.chatRecyclerView?.scrollToPosition(updatedChatList.size - 1)
                            },
                            index * DELAY_SHOW_TEXT_IN_CHAT
                        )
                    }
                } else {
                    chatAdapter.submitList(chatList)
                }
            }
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
                rating = chatEntity.rating,
                reaction = chatEntity.reaction,
                tpovId = chatEntity.tpovId
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = ChatFragment()
    }
}
