package com.tpov.schoolquiz.domain.repository

import androidx.lifecycle.MutableLiveData
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.data.fierbase.Profile
import kotlinx.coroutines.flow.Flow


interface RepositoryFB {

    fun deleteAllQuiz()

    fun getValSynth(): MutableLiveData<Int>

    fun getPlayersList()

    suspend fun getChatData(): Flow<List<ChatEntity>>

    fun removeChatListener()

    fun getProfile()


    fun setTpovIdFB()

    fun getTpovIdFB()

    fun setProfile()

    fun getUserName(): Profile


    suspend fun getQuiz8FB() //only get quiz8 and question 8

    suspend fun getAllQuiz()

    suspend fun setAllQuiz()

}