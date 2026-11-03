package com.tpov.network.presentation.chat

import androidx.lifecycle.ViewModel
import com.tpov.network.data.models.local.ChatEntity
import com.tpov.network.domain.ChatUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ChatViewModel @Inject constructor(
    val localUseCase: ChatUseCase
) : ViewModel() {

    suspend fun chatData(): Flow<List<ChatEntity>> {
        return localUseCase.getChatFlow()
    }

}