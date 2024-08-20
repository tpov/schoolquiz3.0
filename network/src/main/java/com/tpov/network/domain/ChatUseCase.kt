package com.tpov.network.domain

import com.tpov.network.domain.repository.RepositoryChat
import javax.inject.Inject

class ChatUseCase @Inject constructor(private val repositoryChat: RepositoryChat) {
    fun getChatFlow() = repositoryChat.getChatFlow()

    fun remoteChatListener() = repositoryChat.remoteChatListener()
}