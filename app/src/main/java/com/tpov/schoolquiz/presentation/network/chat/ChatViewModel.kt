package com.tpov.schoolquiz.presentation.network.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.domain.ChatUseCase
import com.tpov.schoolquiz.domain.ProfileUseCase
import javax.inject.Inject

class ChatViewModel @Inject constructor(
    val chatUseCase: ChatUseCase,
    val profileUseCase: ProfileUseCase
) : ViewModel() {

    suspend fun chatData(): LiveData<List<ChatEntity>> {
        return chatUseCase.getChatFlow().asLiveData()
    }

    fun getProfile(tpovId: Int): ProfileEntity {
        return profileUseCase.getProfile(tpovId)
    }
}