package com.tpov.schoolquiz.presentation.mainactivity

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.data.fierbase.*
import com.tpov.schoolquiz.domain.*
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@InternalCoroutinesApi
class MainActivityViewModel @Inject constructor(
    private val context: Context,
    private val insertQuizUseCase: InsertQuizUseCase,
    private val getQuizLiveDataUseCase: GetQuizLiveDataUseCase,
    private val getQuiz8FBUseCase: GetQuiz8FBUseCase,
    private val getQuestionDetail8FBUseCase: GetQuestionDetail8FBUseCase,
    private val getQuestion8FBUseCase: GetQuestion8FBUseCase,
    private val getIdQuizByNameQuizUseCase: GetIdQuizByNameQuizUseCase,
    private val insertQuestionUseCase: InsertQuestionUseCase,
    private val setQuizFBUseCase: SetQuizDataFBUseCase,
    private val setQuestionFBUseCase: SetQuestionFBUseCase,
    private val setQuestionDetailFBUseCase: SetQuestionDetailFBUseCase,
    private val getProfileFlowUseCase: GetProfileFlowUseCase,
    private val insertProfileUseCase: InsertProfileUseCase,
    private val getQuestionListUseCase: GetQuestionListUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    private val getAllProfilesDBUseCase: GetAllProfilesDBUseCase,
    private val getPlayersDBUseCase: GetPlayersDBUseCase,
    val getEventLiveDataUseCase: GetEventLiveDataUseCase
) : ViewModel() {

    var tpovId = 0

    fun getAllProfiles() = getAllProfilesDBUseCase()
    fun getPlayers() = getPlayersDBUseCase()

    init {
        SharedPreferencesManager.initialize(context)
    }
    fun init() {
        log("fun init()")
        val sharedPref = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        tpovId = sharedPref?.getInt("tpovId", -1) ?: -1

        log("init() tpovId = $tpovId")
        viewModelScope.launch(Dispatchers.IO) {
            log("init() launch()")
            if (tpovId == -1) insertProfile()
            getProfileFlowUseCase(tpovId)
            getQuiz8FBUseCase()
            getQuestion8FBUseCase()
        }
    }

    fun getQuestionListByIdQuiz(idQuiz: Int): List<QuestionEntity> {
        log("fun getQuestionListByIdQuiz")
        log("fun getQuestionListByIdQuiz list: ${getQuestionListUseCase()}")
        log("fun getQuestionListByIdQuiz list filter: ${getQuestionListUseCase().filter { it.idQuiz == idQuiz }}")
        return getQuestionListUseCase().filter { it.idQuiz == idQuiz }
    }


    private fun insertProfile() {
        log("fun insertProfile()")

        val userLocale: Locale = Locale.getDefault()
        val userLanguageCode: String = userLocale.language
        val sharedPref = context.getSharedPreferences("profile", Context.MODE_PRIVATE)

        with(sharedPref.edit()) {
            putInt("tpovId", 0)
            apply()
        }

        log("set tpovId = 0")
        tpovId = 0

        Log.d("daefdhrt", "insertProfile tpovId $tpovId")
        val profile = Profile(
            0,
            "",
            "",
            "",
            "",
            Points(0, 0, 0, 0),
            "0",
            Buy(1, 0, 1, "0", "0", "0"),
            "0",
            "",
            "",
            0,
            TimeInGames(0,0,0, 0),
            AddPoints(0, 0, 0, 0, ""),
            Dates(
                TimeManager.getCurrentTime(),
                ""
            ),
            "",
            userLanguageCode,
            Qualification(1, 0, 0,0,0,0,0)
        )

        insertProfileUseCase(profile.toProfileEntity())
    }

    fun insertQuiz(quizEntity: QuizEntity) {

        log("fun insertQuiz")
        insertQuizUseCase(quizEntity)
    }

    fun insertQuestion(questionEntity: QuestionEntity) {
        log("fun insertQuestionList")
        insertQuestionUseCase(questionEntity)
    }

    fun insertQuizEvent(quizEntity: QuizEntity) {
        log("fun updateQuizEvent")
        if (quizEntity.event == 1) {
            quizEntity.event++

            log("updateQuizEvent quizEntity.event = ${quizEntity.event}")
            insertQuiz(quizEntity.copy(id = null))
        }

    }

    fun setQuestionsFB() {
        log("fun setQuestionsFB")
        setQuestionFBUseCase()
        setQuestionDetailFBUseCase()
    }

    private fun getQuizList(): LiveData<List<QuizEntity>> {
        log("fun getQuizList")
        val sharedPref = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        tpovId = sharedPref?.getInt("tpovId", -1) ?: -1
        log("getQuizList tpovId: $tpovId")
        return getQuizLiveDataUseCase.getQuizUseCase(tpovId)
    }

    val getQuizLiveData: LiveData<List<QuizEntity>> = getQuizList()
    var getProfileFBLiveData: LiveData<ProfileEntity> =
        getProfileFlowUseCase(this.tpovId).asLiveData()

    val getProfile = getProfileUseCase(tpovId)

    fun getIdQuizByNameQuiz(nameQuiz: String) = getIdQuizByNameQuizUseCase(nameQuiz, tpovId)

    fun log(massage: String) {
        Logcat.log(massage, "MainActivityViewModel", Logcat.LOG_VIEW_MODEL)
    }

    fun deleteQuiz(id: Int) {

    }

    fun getLvlTranslateByQuizId(id: Int): Int {
        var lvlTranslate = 1000
        getQuestionListByIdQuiz(id).forEach {
            if (it.lvlTranslate < lvlTranslate) lvlTranslate = it.lvlTranslate
        }
        return  lvlTranslate
    }
}