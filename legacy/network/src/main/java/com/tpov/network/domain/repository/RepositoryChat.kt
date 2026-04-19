package com.tpov.network.domain.repository

import com.tpov.network.data.models.local.ChatEntity
import kotlinx.coroutines.flow.Flow

interface RepositoryChat {
    fun getChatFlow(): Flow<List<ChatEntity>>

    fun remoteChatListener()
}