package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.domain.repository.RepositoryFB
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetChatUseCase @Inject constructor(private val repository: RepositoryFB) {
    fun getChatUseCase(): Flow<List<ChatEntity>> {
        return repository.getChatData()
    }
}