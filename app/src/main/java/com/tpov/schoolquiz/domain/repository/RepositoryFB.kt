package com.tpov.schoolquiz.domain.repository

import androidx.lifecycle.MutableLiveData
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.data.fierbase.Profile
import kotlinx.coroutines.flow.Flow


interface RepositoryFB {

    fun getValSynth(): MutableLiveData<Int>

    fun getChatData(): Flow<List<ChatEntity>>

    fun getQuiz8Data()

    fun getQuiz7Data()

    fun getQuiz6Data()

    fun getQuiz5Data()

    fun getQuiz4Data()

    fun getQuiz3Data()

    fun getQuiz2Data()

    fun getQuiz1Data()


    fun getQuestion8()

    fun getQuestion7()

    fun getQuestion6()

    fun getQuestion5()

    fun getQuestion4()

    fun getQuestion3()

    fun getQuestion2()

    fun getQuestion1()


    fun getQuestionDetail1()

    fun getQuestionDetail2()

    fun getQuestionDetail3()

    fun getQuestionDetail4()

    fun getQuestionDetail5()

    fun getQuestionDetail6()

    fun getQuestionDetail7()

    fun getQuestionDetail8()

    fun getProfile()

    suspend fun setQuizData()

    fun setQuestionData()

    fun setTpovIdFB()

    fun getTpovIdFB()

    fun setQuestionDetail()

    fun setProfile()

    fun setEvent()

    fun getUserName(): Profile

}