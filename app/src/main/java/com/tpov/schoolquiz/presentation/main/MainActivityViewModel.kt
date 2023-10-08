package com.tpov.schoolquiz.presentation.main

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.*
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.*
import com.tpov.schoolquiz.data.fierbase.*
import com.tpov.schoolquiz.domain.*
import com.tpov.schoolquiz.presentation.*
import com.tpov.schoolquiz.presentation.core.Logcat
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.setting.SharedPrefSettings
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.coroutines.*
import java.util.*
import javax.inject.Inject

@InternalCoroutinesApi
class MainActivityViewModel @Inject constructor(
    private val context: Context,
    val localUseCase: LocalUseCase,
    val removeUseCase: RemoveUseCase
) : ViewModel() {

    var oldId = 0
    val tpovIdLiveData = MutableLiveData<Int>()

    private val placeLiveData = MutableLiveData<Int>()

    fun countPlaceLiveData() = placeLiveData

    val getProfileFBLiveData: LiveData<ProfileEntity?> = tpovIdLiveData.switchMap { tpovId ->
        log("getProfileFBLiveData tpovId: $tpovId")
        localUseCase.getProfileFlow(tpovId).asLiveData()
    }

    fun getQuizById(position: Int): QuizEntity {
        return localUseCase.getQuizById(position)
    }

    fun updateQuiz(quizEntity: QuizEntity) {
        localUseCase.updateQuiz(quizEntity)
    }

    suspend fun getQuestionList(): List<QuestionEntity> {
        return localUseCase.getQuestionList()
    }

    fun updateTpovId(tpovId: Int) {
        if (tpovId != oldId) tpovIdLiveData.value = tpovId
        oldId = tpovId
    }

    fun getAllProfiles(): List<ProfileEntity> {
        log("dwawfdrgh, ${localUseCase.getAllProfilesDB()}")
        return localUseCase.getAllProfilesDB()
    }

    fun getPlayers(): List<PlayersEntity> {
        return localUseCase.getPlayersDB()
    }

    init {
        SharedPreferencesManager.initialize(context)
        SharedPrefSettings.initialize(context)
    }

    fun init() {
        log("fun init(), tpovId: ${getTpovId()}")

        if (getTpovId() == -1) {
            CoroutineScope(Dispatchers.IO).launch {
                insertProfile()
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            localUseCase.getProfileFlow(getTpovId())
        }

        CoroutineScope(Dispatchers.IO).launch {
            removeUseCase.getQuiz8()
        }
    }


    suspend fun getQuestionListByIdQuiz(idQuiz: Int): List<QuestionEntity> {
        return getQuestionList().filter { it.idQuiz == idQuiz }
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
            Points(0, 0, 0),
            "0",
            Buy(1, "", "", ""),
            "",
            "",
            "",
            0,
            TimeInGames(0, 0, 0, 0, 0, 0),
            AddPoints(0, 0, 0, "", ""),
            Dates(
                TimeManager.getCurrentTime(), ""
            ),
            "",
            userLanguageCode,
            Qualification(0, 0, 0, 0, 0, 0),
            Life(1, 0),
            Box(0, TimeManager.getCurrentTime(), 0),
            0
        )

        localUseCase.insertProfile(profile.toProfileEntity(0, 400))
    }

    fun insertQuiz(quizEntity: QuizEntity) {

        log("fun insertQuiz")
        localUseCase.insertQuiz(quizEntity)
    }

    fun insertQuestion(questionEntity: QuestionEntity) {
        log("fun insertQuestionList")

        CoroutineScope(Dispatchers.IO).launch {
            localUseCase.insertQuestion(questionEntity)
        }
    }

    fun insertQuizEvent(quizEntity: QuizEntity) {
        log("fun updateQuizEvent")
        if (quizEntity.event == EVENT_QUIZ_FOR_USER) {
            quizEntity.event++

            log("updateQuizEvent quizEntity.event = ${quizEntity.event}")
            insertQuiz(quizEntity.copy(id = null))
        }
    }

    fun setQuestionsFB() {
        log("fun setQuestionsFB")

        CoroutineScope(Dispatchers.IO).launch {
            removeUseCase.setQuiz()
        }
    }

    suspend fun getQuizListLiveData(): LiveData<List<QuizEntity>> {
        log("fun getQuizList tpovId: ${getTpovId()}")
        return localUseCase.getQuizLiveData(getTpovId())
    }

    suspend fun getQuizLiveData(): LiveData<List<QuizEntity>> {
        return getQuizListLiveData()
    }

    suspend fun getQuizList(): List<QuizEntity> {
        return localUseCase.getQuizEvent()
    }

    fun getProfile(): ProfileEntity {
        return getProfileFun(getTpovId())
    }

    suspend fun getCountPlaceForUserQuiz(): Int {
        return try {
            val placeQuiz = localUseCase.getProfile(getTpovId()).buyQuizPlace
            val countUserQuiz =
                getQuizList().filter { it.event == EVENT_QUIZ_FOR_USER && it.tpovId == getTpovId() }
            placeQuiz?.minus(countUserQuiz.size)!!
        } catch (e: Exception) {
            1
        }
    }

    suspend fun removePlaceInUserQuiz() {
        log("fgjesdriofjeskl getCountPlaceForUserQuiz():${getCountPlaceForUserQuiz()}")
        placeLiveData.postValue(getCountPlaceForUserQuiz() - 1)
    }

    fun getProfileCountBox(): Int {
        return getProfileFun(getTpovId()).countBox ?: 0
    }

    private fun getProfileFun(tpovId: Int): ProfileEntity {
        log("getProfileFun getProfile(tpovId):${localUseCase.getProfile(tpovId)}")
        return localUseCase.getProfile(tpovId)
    }

    fun getNewIdQuiz(): Int {
        var i = 0
        runBlocking {
            localUseCase.getQuizList(getTpovId()).forEach {
                log("getNewIdQuiz: it: ${it.id}")
                if (it.id!! in (i + 1)..BARRIER_QUIZ_ID_LOCAL_AND_REMOVE) {
                    i = it.id!!
                }
            }
        }
        return i + 1
    }

    fun getIdQuizByNameQuiz(nameQuiz: String) = localUseCase.getIdQuizByNameQuiz(
        nameQuiz, getTpovId()
    )

    fun log(massage: String) {
        Logcat.log(massage, "MainActivityViewModel", Logcat.LOG_VIEW_MODEL)
    }

    fun deleteQuiz(id: Int) {
        localUseCase.deleteQuiz(id)
        localUseCase.deleteQuestionByIdQuiz(id)
    }

    fun deleteQuestion(idQuiz: Int) {
        localUseCase.deleteQuestionByIdQuiz(idQuiz)
    }

    fun findValueForDeviceLocale(id: Int): Int {
        try {
            val data = getQuizById(id).languages
            val deviceLocale = Locale.getDefault().language

            val pairs = data.split(SPLIT_BETWEEN_LANGUAGES)
            for (pair in pairs) {
                val keyValue = pair.split(SPLIT_BETWEEN_LVL_TRANSLATE_AND_LANG)
                if (keyValue.size == 2) {
                    val key = keyValue[0]
                    val value = keyValue[1].toInt()
                    if (key == deviceLocale) {
                        return value
                    }
                }
            }

            return 0
        } catch (e: Exception) {
            return -1
        }

    }

    fun getProfileCount(): Int? {
        val profile = localUseCase.getProfile(getTpovId())
        log("getProfileCount(): $profile, ${getTpovId()}")
        return profile.count
    }

    fun getProfileCountGold(): Int? {
        val profile = localUseCase.getProfile(getTpovId())
        log("getProfileCount(): $profile, ${getTpovId()}")
        return profile.countGold
    }

    fun getProfileCountGoldLife(): Int? {
        val profile = localUseCase.getProfile(getTpovId())
        log("getProfileCount(): $profile, ${getTpovId()}")
        return profile.countGoldLife
    }

    fun getProfileNolic(): Int? {
        val profile = localUseCase.getProfile(getTpovId())

        return profile.pointsNolics
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun synthPrizeBoxDay(profile: ProfileEntity?): Int? {

        log("esfsef ${profile?.timeLastOpenBox} --- ${TimeManager.getCurrentTime()}")
        var addBox = 0
        val days = if (TimeManager.getDaysBetweenDates(
                profile?.timeLastOpenBox ?: "", TimeManager.getCurrentTime()
            ) == 1L
        ) {
            if (profile?.coundDayBox == MAX_COUNT_DAY_BOX) {
                addBox = 1
                MAX_COUNT_DAY_BOX
            } else profile?.coundDayBox?.plus(1) ?: errorGetCountBox()

        } else if (TimeManager.getDaysBetweenDates(
                profile?.timeLastOpenBox ?: "", TimeManager.getCurrentTime()
            ) < 1
        ) {
            log("numberBox day2: 0}")
            profile?.coundDayBox
        } else 0

        profile?.copy(
            coundDayBox = days, countBox = profile.countBox?.plus(
                addBox
            ), timeLastOpenBox = TimeManager.getCurrentTime()
        )?.let { localUseCase.updateProfile(it) }
        return days
    }

    private fun errorGetCountBox(): Int {
        Toast.makeText(context, context.getString(R.string.error_get_box_day), Toast.LENGTH_SHORT)
            .show()
        return 0
    }

    fun getProfileCountLife(): Int? {
        val profile = localUseCase.getProfile(getTpovId())
        return profile.countLife
    }

    fun getProfileDateCloseAp(): String? {
        val profile = localUseCase.getProfile(getTpovId())
        return profile.dateCloseApp
    }

    fun getCountChat(): Int {
        val profile = localUseCase.getProfile(getTpovId())
        return profile.timeInGamesSmsPoints ?: 0
    }

    fun getMassage(): List<ChatEntity> {
        return getMassage() //todo create
    }

    fun getProfileTimeInGame(): Int? {
        val profile = localUseCase.getProfile(getTpovId())
        return profile.timeInGamesInQuiz
    }

    fun getProfileSkill(): Int? {
        val profile = localUseCase.getProfile(getTpovId())
        return profile.pointsSkill
    }

    fun getProfileTimeInChat(): Int {
        val profile = localUseCase.getProfile(getTpovId())
        return profile.timeInGamesInChat ?: 0
    }

    //This is WTF
    suspend fun setPercentResultAllQuiz() {
        localUseCase.getQuizEvent().forEach { quiz ->
            var perc = mutableListOf<Int>()
            var persentAll = 0
            var maxPersent = 0

            log("sdsdsds 1.${quiz.nameQuiz}")
            log("sdsdsds 1. getQuestionDetailList(): ${localUseCase.getQuestionDetailList()}")
            localUseCase.getQuestionDetailList().forEach {
                log("sdsdsds 1,1 it.idQuiz: ${it.idQuiz} == quiz.id: ${quiz.id} -> ${it.idQuiz == quiz.id}")
                log("sdsdsds 1,2. getTpovId(): ${getTpovId()}, quiz.tpovId: ${quiz.tpovId} -> ${getTpovId() == quiz.tpovId}")
                if (it.idQuiz == quiz.id) {
                    log("sdsdsds 2. $it")
                    var i = 0
                    var j = 0

                    it.codeAnswer?.forEach { item ->
                        if (item == CORRECTLY_ANSWERED_IN_CODE_ANSWER) i++
                        j++
                    }

                    log("sdsdsds 3. i:${i}")
                    log("sdsdsds 4. j:${j}")

                    if (!it.hardQuiz) {
                        log("sdsdsds 4.5.hardQuiz: ${it.codeAnswer}")
                        try {
                            if (((100 * i) / j) > maxPersent) maxPersent = ((100 * i) / j)
                            perc.add(((100 * i) / j))
                        } catch (e: Exception) {
                            perc.add(0)
                        }
                    } else {
                        try {
                            if ((((100 * i) / j) / 5) + 100 > maxPersent)
                                maxPersent = (((100 * i) / j) / 5) + 100
                            perc.add((((100 * i) / j) / 5) + 100)
                        } catch (e: Exception) {
                            perc.add(0)
                        }
                    }

                    log("sdsdsds 5.perc:${perc}")
                    j = 0
                    i = 0
                    perc.forEach { itemPerc ->
                        i += itemPerc
                    }
                    persentAll = i / perc.size

                    log("sdsdsds 6.i:${i}")
                    log("sdsdsds 7.persentAll: ${persentAll}")
                    log("sdsdsds 8.maxPersent: ${maxPersent}")
                }
            }
            when (quiz.event) {
                EVENT_QUIZ_FOR_USER,
                EVENT_QUIZ_ARENA,
                EVENT_QUIZ_TOURNIRE,
                EVENT_QUIZ_TOURNIRE_LEADER,
                EVENT_QUIZ_HOME
                -> updateQuiz(quiz.copy(
                        stars = maxPersent,
                        starsAll = persentAll
                    )
                )
            }
        }
    }
}