package com.tpov.network.data

import android.app.Application
import com.google.firebase.database.ValueEventListener
import com.tpov.network.data.database.ChatDao
import com.tpov.network.data.models.local.ChatEntity
import com.tpov.network.domain.repository.RepositoryChat
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RepositoryChatImpl @Inject constructor(
    private val application: Application,
    private val chatDao: ChatDao
) : RepositoryChat {
    private lateinit var chatValueEventListener: ValueEventListener

    @OptIn(DelicateCoroutinesApi::class)
    override fun getChatFlow(): Flow<List<ChatEntity>> {

        return chatDao.getChat()
    }

    override fun remoteChatListener() {
        TODO("Not yet implemented")
    }


    private fun getPersonalMassage() {

    }

}