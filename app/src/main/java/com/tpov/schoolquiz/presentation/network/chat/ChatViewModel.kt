package com.tpov.schoolquiz.presentation.network.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.domain.GetChatUseCase
import javax.inject.Inject

class ChatViewModel @Inject constructor(
    private val getChatDataUseCase: GetChatUseCase
) : ViewModel() {

    val chatData: LiveData<List<ChatEntity>> = getChatDataUseCase.getChatUseCase().asLiveData()

}