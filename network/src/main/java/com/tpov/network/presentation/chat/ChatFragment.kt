package com.tpov.network.presentation.chat

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase
import com.tpov.common.DEFAULT_DATA_IN_GET_CHAT
import com.tpov.common.DEFAULT_DATA_IN_SEND_CHAT
import com.tpov.common.DEFAULT_LOCAL_IN_GET_CHAT
import com.tpov.common.data.core.SharedPreferencesManager
import com.tpov.common.data.core.SharedPreferencesManager.getTpovId
import com.tpov.network.data.models.local.ChatEntity
import com.tpov.network.databinding.FragmentChatBinding
import com.tpov.userguide.Options
import com.tpov.userguide.UserGuide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ChatFragment : Fragment() {

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

        chatViewModel = ViewModelProvider(this)[ChatViewModel::class.java]
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

            showGuide(versionCode)

        } catch (e: PackageManager.NameNotFoundException) {
        }

        binding?.sendMessageButton?.setOnClickListener {
            val message = binding?.messageEditText?.text.toString().trim()
            if (message.isNotEmpty()) {
                val currentTime = getCurrentTimeKiev()


                binding?.messageEditText?.setText("")
            }
        }
    }

    private fun showGuide(versionCode: Int?) {
        val guide = UserGuide(requireContext()).guideBuilder()
        guide.setTitleText("Правила чата")
            .setText("1 - ")
            .setOptions(Options(countRepeat = 3, showDot = false, exactMatchKey = versionCode))
    }

    private fun getCurrentTimeKiev(): String {
        val timeZone = TimeZone.getTimeZone(DEFAULT_LOCAL_IN_GET_CHAT)
        val dateFormat = SimpleDateFormat(DEFAULT_DATA_IN_GET_CHAT, Locale.getDefault())
        dateFormat.timeZone = timeZone
        return dateFormat.format(Date())
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onAttach(context: Context) {
        super.onAttach(context)
    }

    private fun sendMessage(chat: ChatEntity) {
        val chatRef = FirebaseDatabase.getInstance().getReference("chat")

        val dateFormat = SimpleDateFormat(DEFAULT_DATA_IN_SEND_CHAT, Locale.getDefault())
        val currentDate = dateFormat.format(Date())

        val chatDateRef = chatRef.child(currentDate)
        val chatId = chatDateRef.push().key

        if (chatId != null) {
            chatDateRef.child(chatId).setValue(chat).addOnSuccessListener {
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
    }


    private suspend fun observeChatData() {
        chatAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                val layoutManager = binding?.chatRecyclerView?.layoutManager as LinearLayoutManager
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                if (chatAdapter.itemCount != 0 && lastVisibleItemPosition >= chatAdapter.itemCount - 6) {
                    binding?.chatRecyclerView?.smoothScrollToPosition(chatAdapter.itemCount - 1)
                }
            }
        })

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
