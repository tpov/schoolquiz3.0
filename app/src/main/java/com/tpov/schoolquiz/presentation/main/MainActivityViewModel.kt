package com.tpov.schoolquiz.presentation.main

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.data.fierbase.AddPoints
import com.tpov.schoolquiz.data.fierbase.Box
import com.tpov.schoolquiz.data.fierbase.Buy
import com.tpov.schoolquiz.data.fierbase.Dates
import com.tpov.schoolquiz.data.fierbase.Life
import com.tpov.schoolquiz.data.fierbase.Points
import com.tpov.schoolquiz.data.fierbase.Profile
import com.tpov.schoolquiz.data.fierbase.Qualification
import com.tpov.schoolquiz.data.fierbase.TimeInGames
import com.tpov.schoolquiz.data.fierbase.toProfileEntity
import com.tpov.schoolquiz.domain.GetAllProfilesDBUseCase
import com.tpov.schoolquiz.domain.GetEventLiveDataUseCase
import com.tpov.schoolquiz.domain.GetIdQuizByNameQuizUseCase
import com.tpov.schoolquiz.domain.GetPlayersDBUseCase
import com.tpov.schoolquiz.domain.GetProfileFlowUseCase
import com.tpov.schoolquiz.domain.GetProfileUseCase
import com.tpov.schoolquiz.domain.GetQuestion8FBUseCase
import com.tpov.schoolquiz.domain.GetQuestionDetail8FBUseCase
import com.tpov.schoolquiz.domain.GetQuestionListUseCase
import com.tpov.schoolquiz.domain.GetQuiz8FBUseCase
import com.tpov.schoolquiz.domain.GetQuizByIdUseCase
import com.tpov.schoolquiz.domain.GetQuizListUseCase
import com.tpov.schoolquiz.domain.GetQuizLiveDataUseCase
import com.tpov.schoolquiz.domain.InsertProfileUseCase
import com.tpov.schoolquiz.domain.InsertQuestionUseCase
import com.tpov.schoolquiz.domain.InsertQuizUseCase
import com.tpov.schoolquiz.domain.SetQuestionDetailFBUseCase
import com.tpov.schoolquiz.domain.SetQuestionFBUseCase
import com.tpov.schoolquiz.domain.SetQuizDataFBUseCase
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.coroutines.InternalCoroutinesApi
import java.util.Locale
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
    private val getQuizListUseCase: GetQuizListUseCase,
    val getEventLiveDataUseCase: GetEventLiveDataUseCase,
    val getQuizByIdUseCase: GetQuizByIdUseCase
) : ViewModel() {

    var tpovId = 0
    var oldId = 0
    val tpovIdLiveData = MutableLiveData<Int>()

    val getProfileFBLiveData: LiveData<ProfileEntity?> = tpovIdLiveData.switchMap { tpovId ->
        log("getProfileFBLiveData tpovId: $tpovId")
        getProfileFlowUseCase(tpovId).asLiveData()
    }

    fun updateTpovId(tpovId: Int) {
        if (tpovId != oldId) tpovIdLiveData.value = tpovId
        oldId = tpovId
    }

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
        log("init() launch()")
        if (tpovId == -1) insertProfile()
        getProfileFlowUseCase(tpovId)
        getQuiz8FBUseCase()
        getQuestion8FBUseCase()
    }

    fun getQuestionListByIdQuiz(idQuiz: Int): List<QuestionEntity> {
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
            TimeInGames(0, 0, 0, 0),
            AddPoints(0, 0, 0, 0, ""),
            Dates(
                TimeManager.getCurrentTime(),
                ""
            ),
            "",
            userLanguageCode,
            Qualification(1, 0, 0, 0, 0, 0, 0),
            Life(1, 300, 0, 0),
            Box(0, TimeManager.getCurrentTime(), 0)
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

    val getProfile = getProfileUseCase(tpovId)

    fun getNewIdQuiz(): Int {
        var i = 0
        getQuizListUseCase(tpovId).forEach {
            if (i <= 100 && i < it.id!!) {
                i = it.id!!
            }
        }
        return i + 1
    }

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
        return lvlTranslate
    }
}