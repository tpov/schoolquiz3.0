package com.tpov.schoolquiz.presentation.network.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.domain.LocalUseCase
import com.tpov.schoolquiz.domain.RemoveUseCase
import javax.inject.Inject

class ChatViewModel @Inject constructor(
    val localUseCase: LocalUseCase,
    val removeUseCase: RemoveUseCase
) : ViewModel() {

    suspend fun chatData(): LiveData<List<ChatEntity>> {
        return removeUseCase.getChat().asLiveData()
    }

    fun getProfile(tpovId: Int): ProfileEntity {
        return localUseCase.getProfile(tpovId)
    }
}