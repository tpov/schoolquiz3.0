package com.tpov.schoolquiz.domain.repository

import androidx.lifecycle.MutableLiveData
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.data.fierbase.Profile
import kotlinx.coroutines.flow.Flow


interface RepositoryFB {

    fun deleteAllQuiz()

    fun getValSynth(): MutableLiveData<Int>

    fun getPlayersList()

    suspend fun getTranslateFB()

    suspend fun getChatData(): Flow<List<ChatEntity>>

    fun removeChatListener()

    suspend fun getQuiz8Data()

    suspend fun getQuiz7Data()

    suspend fun getQuiz6Data()

    suspend fun getQuiz5Data()

    suspend fun getQuiz4Data()

    suspend fun getQuiz3Data()

    suspend fun getQuiz2Data()

    suspend fun getQuiz1Data()


    suspend fun getQuestion8()

    suspend fun getQuestion7()

    suspend fun getQuestion6()

    suspend fun getQuestion5()

    suspend fun getQuestion4()

    suspend fun getQuestion3()

    suspend fun getQuestion2()

    suspend fun getQuestion1()


    suspend fun getQuestionDetail1()

    suspend fun getQuestionDetail2()

    suspend fun getQuestionDetail3()

    suspend fun getQuestionDetail4()

    suspend fun getQuestionDetail5()

    suspend fun getQuestionDetail6()

    suspend fun getQuestionDetail7()

    suspend fun getQuestionDetail8()

    fun getProfile()

    fun setQuizData()

    fun setQuestionData()

    fun setTpovIdFB()

    fun getTpovIdFB()

    fun setQuestionDetail()

    fun setProfile()

    suspend fun setEvent()

    fun getUserName(): Profile

}