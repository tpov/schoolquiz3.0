package com.tpov.schoolquiz.presentation.main

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.*
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.data.fierbase.*
import com.tpov.schoolquiz.domain.*
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.setting.SharedPrefSettings
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.coroutines.InternalCoroutinesApi
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
    private val getQuizListUseCase: GetQuizListUseCase,
    val getEventLiveDataUseCase: GetEventLiveDataUseCase,
    private val getQuizByIdUseCase: GetQuizByIdUseCase,
    private val deleteQuizByIdUseCase: DeleteQuizUseCase,
    private val deleteQuestionByIdQuizUseCase: DeleteQuestionByIdQuizUseCase,
    val updateQuizUseCase: UpdateQuizUseCase,
    val updateProfileUseCase: UpdateProfileUseCase,
    private val getQuizEventUseCase: GetQuizEventUseCase
) : ViewModel() {

    var oldId = 0
    val tpovIdLiveData = MutableLiveData<Int>()

    val getProfileFBLiveData: LiveData<ProfileEntity?> = tpovIdLiveData.switchMap { tpovId ->
        log("getProfileFBLiveData tpovId: $tpovId")
        getProfileFlowUseCase(tpovId).asLiveData()
    }

    fun getQuizById(position: Int): QuizEntity {
        log(
            "getQuizById, position: $position, getQuizByIdUseCase(position): ${
                getQuizByIdUseCase(
                    position
                )
            }"
        )
        return getQuizByIdUseCase(position)
    }

    fun updateQuiz(quizEntity: QuizEntity) {
        log("udpateQuiz: $quizEntity")
        updateQuizUseCase(quizEntity)
    }
    fun getQuestionList(): List<QuestionEntity> {
        return getQuestionListUseCase()
    }

    fun updateTpovId(tpovId: Int) {
        if (tpovId != oldId) tpovIdLiveData.value = tpovId
        oldId = tpovId
    }

    fun getAllProfiles() = getAllProfilesDBUseCase()
    fun getPlayers() = getPlayersDBUseCase()

    init {
        SharedPreferencesManager.initialize(context)
        SharedPrefSettings.initialize(context)
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
            TimeInGames(0, 0, 0, 0, 0, 0),
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

    fun getQuizListLiveData(): LiveData<List<QuizEntity>> {
        log("fun getQuizList tpovId: ${getTpovId()}")
        return getQuizLiveDataUseCase.getQuizUseCase(getTpovId())
    }

    fun getQuizLiveData(): LiveData<List<QuizEntity>> {
        return getQuizListLiveData()
    }

    fun getQuizList(): List<QuizEntity> {
        return getQuizEventUseCase()
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

    fun getIdQuizByNameQuiz(nameQuiz: String) = getIdQuizByNameQuizUseCase(
        nameQuiz,
        getTpovId()
    )

    fun log(massage: String) {
        Logcat.log(massage, "MainActivityViewModel", Logcat.LOG_VIEW_MODEL)
    }

    fun deleteQuiz(id: Int) {
        deleteQuizByIdUseCase(id)
        deleteQuestionByIdQuizUseCase(id)
    }

    fun findValueForDeviceLocale(id: Int): Int {
        val data = getQuizById(id).languages
        val deviceLocale = Locale.getDefault().language

        val pairs = data.split("|")
        for (pair in pairs) {
            val keyValue = pair.split("-")
            if (keyValue.size == 2) {
                val key = keyValue[0]
                val value = keyValue[1].toInt()
                if (key == deviceLocale) {
                    return value
                }
            }
        }

        return 0
    }

    fun getProfileCount(): Int? {
        val profile = getProfileUseCase(getTpovId())
        log("getProfileCount(): $profile, ${getTpovId()}")
        return profile.count
    }

    fun getProfileCountGold(): Int? {
        val profile = getProfileUseCase(getTpovId())
        log("getProfileCount(): $profile, ${getTpovId()}")
        return profile.countGold
    }
    fun getProfileCountGoldLife(): Int? {
        val profile = getProfileUseCase(getTpovId())
        log("getProfileCount(): $profile, ${getTpovId()}")
        return profile.countGoldLife
    }

    fun getProfileNolic(): Int? {
        val profile = getProfileUseCase(getTpovId())

        return profile.pointsNolics
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun synthPrizeBoxDay(profile: ProfileEntity?): Int? {

        val days = if (TimeManager.getDaysBetweenDates(
                profile?.timeLastOpenBox ?: "",
                TimeManager.getCurrentTime()
            ) == 1L
        ) {
            if (profile?.coundDayBox == 10) 10
            else profile?.coundDayBox?.plus(1) ?: errorGetCountBox()

        } else if (TimeManager.getDaysBetweenDates(
                profile?.timeLastOpenBox ?: "",
                TimeManager.getCurrentTime()
            ) < 1
        ) {
            log("numberBox day2: 0}")
            profile?.coundDayBox
        } else 0

        profile?.copy(
            coundDayBox = days, countBox = if (days == 10) {
                log(
                    "numberBox day4: ${
                        profile.countBox?.plus(
                            1
                        )
                    }}"
                )
                profile.countBox?.plus(
                    1
                )
            } else {
                log("numberBox day5: ${profile.countBox}}")
                profile.countBox
            }, timeLastOpenBox = TimeManager.getCurrentTime()
        )?.let { updateProfileUseCase(it) }
        return days
    }

    private fun errorGetCountBox(): Int {
        Toast.makeText(context, "Error get box day", Toast.LENGTH_SHORT).show()
        return 0
    }

    fun getProfileCountLife(): Int? {
        val profile = getProfileUseCase(getTpovId())
        return profile.countLife
    }

    fun getProfileDateCloseAp(): String? {
        val profile = getProfileUseCase(getTpovId())
        return profile.dateCloseApp
    }
    fun getProfileTimeInGame(): Int? {
        val profile = getProfileUseCase(getTpovId())
        return profile.timeInGamesInQuiz
    }

    fun getProfileSkill(): Int? {
        val profile = getProfileUseCase(getTpovId())
        return profile.pointsSkill
    }

    fun getProfileTimeInChat(): Int {
        val profile = getProfileUseCase(getTpovId())
        return profile.timeInGamesInChat ?: 0
    }
}