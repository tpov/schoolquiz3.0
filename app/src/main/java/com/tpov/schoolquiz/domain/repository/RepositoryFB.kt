package com.tpov.schoolquiz.domain.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.data.fierbase.Profile
import kotlinx.coroutines.flow.Flow


interface RepositoryFB {

    fun getValSynth(): MutableLiveData<Int>

    fun getChatData(): Flow<List<ChatEntity>>

    fun getQuiz8Data(context: Context)

    fun getQuiz7Data(context: Context)

    fun getQuiz6Data()

    fun getQuiz5Data()

    fun getQuiz4Data()

    fun getQuiz3Data()

    fun getQuiz2Data()

    fun getQuiz1Data(tpovId: Int)


    fun getQuestion8Data()

    fun getQuestion7Data()

    fun getQuestion6Data()

    fun getQuestion5Data()

    fun getQuestion4Data()

    fun getQuestion3Data()

    fun getQuestion2Data()

    fun getQuestion1Data(tpovId: Int)


    fun getQuestionDetail1(tpovId: Int)

    fun getQuestionDetail2()

    fun getQuestionDetail3()

    fun getQuestionDetail4()

    fun getQuestionDetail5()

    fun getQuestionDetail6()

    fun getQuestionDetail7()

    fun getQuestionDetail8()

    fun getProfile(context: Context)

    suspend fun setQuizData(tpovId: Int)

    fun setQuestionData(tpovId: Int)

    fun setTpovIdFB(context: Context)

    fun getTpovIdFB( context: Context)

    fun setQuestionDetail(tpovId: Int)

    fun setProfile(context: Context)

    fun setEvent(position: Int)

    fun getUserName(tpovId: Int): Profile
}