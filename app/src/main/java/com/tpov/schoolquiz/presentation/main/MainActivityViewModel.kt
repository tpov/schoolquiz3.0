package com.tpov.schoolquiz.presentation.main

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
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
import com.tpov.schoolquiz.domain.DeleteQuestionByIdQuizUseCase
import com.tpov.schoolquiz.domain.DeleteQuizUseCase
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
import com.tpov.schoolquiz.domain.UpdateProfileUseCase
import com.tpov.schoolquiz.domain.UpdateQuizUseCase
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getTpovId
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.withContext
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
    private val getQuizByIdUseCase: GetQuizByIdUseCase,
    private val deleteQuizByIdUseCase: DeleteQuizUseCase,
    private val deleteQuestionByIdQuizUseCase: DeleteQuestionByIdQuizUseCase,
    val updateQuizUseCase: UpdateQuizUseCase,
    val updateProfileUseCase: UpdateProfileUseCase
) : ViewModel() {

    var oldId = 0
    val tpovIdLiveData = MutableLiveData<Int>()

    val getProfileFBLiveData: LiveData<ProfileEntity?> = tpovIdLiveData.switchMap { tpovId ->
        log("getProfileFBLiveData tpovId: $tpovId")
        getProfileFlowUseCase(tpovId).asLiveData()
    }

    fun getQuizById(position: Int): QuizEntity {
        log("getQuizById, position: $position, getQuizByIdUseCase(position): ${getQuizByIdUseCase(position)}")
        return getQuizByIdUseCase(position)
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
        log("fun init(), tpovId: ${getTpovId()}")

        if (getTpovId() == -1) insertProfile()
        getProfileFlowUseCase(getTpovId())
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

        SharedPreferencesManager.setTpovId(0)

        log("set tpovId = 0")

        val profile = Profile(
            0.toString(),
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
            Life(1, 0),
            Box(0, TimeManager.getCurrentTime(), 0)
        )

        insertProfileUseCase(profile.toProfileEntity(0, 100))
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
        log("fun getQuizList tpovId: ${getTpovId()}")
        return getQuizLiveDataUseCase.getQuizUseCase(getTpovId())
    }

    fun getQuizLiveData(): LiveData<List<QuizEntity>> {
        return getQuizList()
    }

    fun getProfile(): ProfileEntity {
        return getProfileUseCaseFun(getTpovId())
    }

    private fun getProfileUseCaseFun(tpovId: Int): ProfileEntity {
        log("getProfileUseCaseFun getProfileUseCase(tpovId):${getProfileUseCase(tpovId)}")
        return getProfileUseCase(tpovId)
    }

    fun getNewIdQuiz(): Int {
        var i = 0
        getQuizListUseCase(getTpovId()).forEach {
            log("getNewIdQuiz: it: ${it.id}")
            if (it.id!! in (i + 1)..100) {
                i = it.id!!
            }
        }
        return i + 1
    }

    fun getIdQuizByNameQuiz(nameQuiz: String) = getIdQuizByNameQuizUseCase(nameQuiz,
        getTpovId()
    )

    fun log(massage: String) {
        Logcat.log(massage, "MainActivityViewModel", Logcat.LOG_VIEW_MODEL)
    }

    fun deleteQuiz(id: Int) {
        deleteQuizByIdUseCase(id)
        deleteQuestionByIdQuizUseCase(id)
    }

    fun getLvlTranslateByQuizId(id: Int): Int {
        var lvlTranslate = 1000
        getQuestionListByIdQuiz(id).forEach {
            if (it.lvlTranslate < lvlTranslate) lvlTranslate = it.lvlTranslate
        }
        return lvlTranslate
    }
    fun getProfileCount(): Int? {
        val profile = getProfileUseCase(getTpovId())
        log("getProfileCount(): $profile, ${getTpovId()}")
        return profile.count
    }

    fun getProfileCountLife(): Int? {
        val profile =  getProfileUseCase(getTpovId())
        return profile.countLife
    }

    fun getProfileDateCloseAp(): String? {
        val profile = getProfileUseCase(getTpovId())
        return profile.dateCloseApp
    }
}