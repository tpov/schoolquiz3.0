package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryChat
import javax.inject.Inject

class ChatUseCase @Inject constructor(private val repositoryChat: RepositoryChat) {
    fun getChatFlow() = repositoryChat.getChatFlow()

    fun remoteChatListener() = repositoryChat.remoteChatListener()
}