package com.tpov.schoolquiz.domain.repository

import com.tpov.schoolquiz.data.database.entities.ChatEntity
import kotlinx.coroutines.flow.Flow

interface RepositoryChat {
    fun getChatFlow(): Flow<List<ChatEntity>>

    fun remoteChatListener()
}