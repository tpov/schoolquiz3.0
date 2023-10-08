package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.domain.repository.RepositoryFB
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RemoveUseCase @Inject constructor(private val repositoryFB: RepositoryFB) {

    fun deleteAllQuiz() {
        repositoryFB.deleteAllQuiz()
    }

    suspend fun getChat(): Flow<List<ChatEntity>> = repositoryFB.getChatData()

    fun getPlayersList() = repositoryFB.getPlayersList()

    fun getProfileFB() = repositoryFB.getProfile()

    suspend fun getQuiz8() = repositoryFB.getQuiz8FB()

    suspend fun getQuiz() = repositoryFB.getAllQuiz()

    fun getSynth() = repositoryFB.getValSynth()

    fun getTpovIdFB() = repositoryFB.getTpovIdFB()

    fun removeChatListenerChat() = repositoryFB.removeChatListener()

    fun setProfileFB() = repositoryFB.setProfile()

    suspend fun setQuiz() = repositoryFB.setAllQuiz()

    fun setTpovIdFB() = repositoryFB.setTpovIdFB()

}