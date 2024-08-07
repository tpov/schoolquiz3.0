package com.tpov.network.network.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.domain.ChatUseCase
import javax.inject.Inject

class ChatViewModel @Inject constructor(
    val localUseCase: ChatUseCase
) : ViewModel() {

    suspend fun chatData(): LiveData<List<ChatEntity>> {
        return removeUseCase.getChat().asLiveData()
    }

    fun getProfile(tpovId: Int): ProfileEntity {
        return localUseCase.getProfile(tpovId)
    }
}