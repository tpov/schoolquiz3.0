package com.tpov.schoolquiz.presentation.network.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.domain.GetChatUseCase
import com.tpov.schoolquiz.domain.GetProfileUseCase
import com.tpov.schoolquiz.domain.RemoveChatListenerChatUseCase
import javax.inject.Inject

class ChatViewModel @Inject constructor(
    private val getChatDataUseCase: GetChatUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    val removeChatListener: RemoveChatListenerChatUseCase
) : ViewModel() {

    suspend fun chatData(): LiveData<List<ChatEntity>> {
        return getChatDataUseCase.getChatUseCase().asLiveData()
    }

    fun getProfile(tpovId: Int) = getProfileUseCase(tpovId)
}