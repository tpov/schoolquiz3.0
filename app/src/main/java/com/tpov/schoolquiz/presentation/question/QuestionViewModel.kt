package com.tpov.schoolquiz.presentation.question

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.CountDownTimer
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.database.entities.QuestionDetailEntity
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.domain.ProfileUseCase
import com.tpov.schoolquiz.domain.QuestionDetailUserCase
import com.tpov.schoolquiz.domain.QuestionUseCase
import com.tpov.schoolquiz.domain.QuizUseCase
import com.tpov.schoolquiz.presentation.CORRECTLY_ANSWERED_IN_CODE_ANSWER
import com.tpov.schoolquiz.presentation.EVENT_QUIZ_ARENA
import com.tpov.schoolquiz.presentation.EVENT_QUIZ_FOR_ADMIN
import com.tpov.schoolquiz.presentation.EVENT_QUIZ_FOR_MODERATOR
import com.tpov.schoolquiz.presentation.EVENT_QUIZ_FOR_TESTER
import com.tpov.schoolquiz.presentation.EVENT_QUIZ_FOR_USER
import com.tpov.schoolquiz.presentation.EVENT_QUIZ_HOME
import com.tpov.schoolquiz.presentation.EVENT_QUIZ_TOURNIRE
import com.tpov.schoolquiz.presentation.EVENT_QUIZ_TOURNIRE_LEADER
import com.tpov.schoolquiz.presentation.INCORRECTLY_ANSWERED_IN_CODE_ANSWER
import com.tpov.schoolquiz.presentation.RATING_QUIZ_EVENT_BED
import com.tpov.schoolquiz.presentation.UNANSWERED_IN_CODE_ANSWER
import com.tpov.schoolquiz.presentation.core.CalcValues
import com.tpov.schoolquiz.presentation.core.Logcat
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager.getNolic
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager.getSkill
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.core.Values.context
import com.tpov.schoolquiz.presentation.dialog.ResultDialog
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@InternalCoroutinesApi
class QuestionViewModel @Inject constructor(
    application: Application,
   val profileUseCase: ProfileUseCase,
   val quizUseCase: QuizUseCase,
   val questionDetailUseCase: QuestionDetailUserCase,
   val questionUseCase: QuestionUseCase
) : AndroidViewModel(application) {
    var timer: CountDownTimer? = null

    var codeAnswer = ""         //Отображает состояние квеста для всех вопросов
    var currentIndex = 0      //Номер вопроса, который виден пользователю
    var numQuestion = 0
    var hardQuestion = false
    var userName = ""
    var idQuiz = 0
    var newIdQuizVar = 0
    var createQuestionDetail = true
    var leftAnswer = 0
    var idThisQuestionDetail = 0
    var persent = 0
    var maxPersent = 0
    var persentAll = 0
    var questionListThis = listOf<QuestionEntity>()
    lateinit var questionDetailListThis: List<QuestionDetailEntity>
    lateinit var quizThis: QuizEntity
    lateinit var tpovId: String
    var resultTranslate = true
    var persentPlayerAll = 0
    var firstQuestionDetail = true

    var numTrueQuestion = 0

    private val _closeActivityEvent = MutableLiveData<Unit>()
    val closeActivityEvent: LiveData<Unit> = _closeActivityEvent

    fun onCloseActivity() {
        _closeActivityEvent.value = Unit
    }

    private val _shouldCloseLiveData = MutableLiveData<Int>()
    val shouldCloseLiveData: LiveData<Int> = _shouldCloseLiveData

    private val _setPetcentPBLiveData = MutableLiveData<Int>()
    val setPetcentPBLiveData: LiveData<Int> = _setPetcentPBLiveData

    private val _setPercentiveData = MutableLiveData<Int>()
    val setPercentiveData: LiveData<Int> = _setPercentiveData

    private val _setLeftAnswerLiveData = MutableLiveData<Int>()
    val setLeftAnswerLiveData: LiveData<Int> = _setLeftAnswerLiveData

    private fun someAction(result: Int) {
        _shouldCloseLiveData.postValue(result)
    }

    fun getProfile(): ProfileEntity {
        return profileUseCase.getProfile(getTpovId())
    }

    fun synthWithDB(context: Context) {
        initConst()
        getQuestionsList()
        initVariable()
    }

    private fun initConst() {
        tpovId = getTpovId().toString()
    }

    private fun initVariable() {

//todo fix crash after answer oldQuestion
        //questionDetailListThis.forEach {
        //if (it.hardQuiz == this.hardQuestion) {
        //    if (getUpdateAnswer(it.codeAnswer)) initOldQuestionDetail(it)
        //}
        //}
        //if (createQuestionDetail) initNewQuestionDetail()

        initNewQuestionDetail() //delete after fix
    }

    private fun initNewQuestionDetail() {

        this.codeAnswer = getCodeAnswer(questionListThis.size)
        this.currentIndex = 0
        this.leftAnswer = questionListThis.size
        this.numQuestion = questionListThis.size
        this.persentPlayerAll = quizUseCase.getQuiz(idQuiz).starsAllPlayer

        questionDetailUseCase.insertQuestionDetail(
            QuestionDetailEntity(
                null,
                idQuiz,
                TimeManager.getCurrentTime(),
                codeAnswer,
                hardQuestion,
                false
            )
        )

        questionDetailUseCase.getQuestionDetailList().forEach {
            if (it.hardQuiz == this.hardQuestion) {
                firstQuestionDetail = false
                if (getUpdateAnswer(it.codeAnswer)) this.idThisQuestionDetail = it.id!!
            }
        }
    }

    private fun getCodeAnswer(size: Int): String {
        var newCodeAnswer = ""
        for (i in 0 until size) {
            newCodeAnswer += UNANSWERED_IN_CODE_ANSWER
        }
        return newCodeAnswer
    }

    private fun initOldQuestionDetail(questionDetailEntity: QuestionDetailEntity) {
        createQuestionDetail = false

        try {
            this.codeAnswer = questionDetailEntity.codeAnswer!!
            this.currentIndex = getCurrentIndex(questionDetailEntity.codeAnswer)!!
            this.leftAnswer = getLeftAnswer(questionDetailEntity.codeAnswer)
            this.numQuestion = leftAnswer
            this.idThisQuestionDetail = questionDetailEntity.id!!
            this.idQuiz = questionDetailEntity.idQuiz
            this.persentPlayerAll = quizUseCase.getQuiz(idQuiz).starsAllPlayer
        } catch (e: Exception) {
            errorLoadQuestion()
        }
    }

    private fun getCurrentIndex(codeAnswer: String?): Int? {
        if (codeAnswer == null || codeAnswer == "") return 0
        for (i in codeAnswer.indices) {
            if (codeAnswer[i] == UNANSWERED_IN_CODE_ANSWER) return i
        }
        return null
    }

    private fun errorLoadQuestion(): String {
        Toast.makeText(
            getApplication(),
            context.getString(R.string.dialog_error_db),
            Toast.LENGTH_LONG
        ).show()

        return ""
    }

    private fun getUserLocalization(context: Context): String {
        val config: Configuration = context.resources.configuration
        return config.locale.language
    }

    private fun getQuestionsList() {

        val questionThisListAll = questionUseCase.getQuestionsByIdQuiz(idQuiz)
            .filter { it.hardQuestion == hardQuestion }
            .sortedBy { it.numQuestion }

        var listMap = mutableMapOf<Int, Boolean>()

        listMap = getMap(questionThisListAll, listMap)
        log("okokoko, listMap:$listMap")


        val questionByLocal = getListQuestionListByLocal(listMap, questionThisListAll)

        this.questionListThis =
            if (didFoundAllQuestion(questionByLocal, listMap)) questionByLocal
            else getListQuestionByProfileLang(
                questionThisListAll,
                listMap
            )
        Questionlist.questionListThis = ArrayList(this.questionListThis.sortedBy { it.numQuestion })

        if (!didFoundAllQuestion(this.questionListThis, listMap)) Toast.makeText(
            context,
            context.getString(R.string.toast_error_found_question),
            Toast.LENGTH_LONG
        ).show()
        getQuestionDetail()

        quizThis = quizUseCase.getQuiz(idQuiz)
    }

    private fun getListQuestionByProfileLang(
        questionThisListAll: List<QuestionEntity>,
        listMap: MutableMap<Int, Boolean>
    ): ArrayList<QuestionEntity> {
        val userLocalization: String = getUserLocalization(context)
        val availableLanguages = profileUseCase.getProfile(getTpovId()).languages

        val questionList = ArrayList<QuestionEntity>()

        listMap.forEach { map ->
            var filteredList = questionThisListAll
                .filter { it.numQuestion == map.key }
                .filter { it.language == userLocalization }

            if (filteredList.isNotEmpty()) {
                questionList.add(filteredList[0])
            } else {
                filteredList = questionThisListAll
                    .filter { it.numQuestion == map.key }
                    .filter { availableLanguages?.contains(it.language) ?: false }

                if (filteredList.isNotEmpty()) {
                    questionList.add(filteredList[0])
                }
            }
        }
        return questionList
    }

    private fun didFoundAllQuestion(
        questionList: List<QuestionEntity>,
        listMap: MutableMap<Int, Boolean>
    ): Boolean {
        var fountQuestion = true

        log("okokoko listMap: $listMap")
        questionList.forEach {
            log("okokoko questionList: $it")
        }
        listMap.forEach {
            try {
                if (questionList[it.key - 1].id == null) fountQuestion = false
            } catch (e: Exception) {
                fountQuestion = false
            }
        }
        log("okokoko fountQuestion: $fountQuestion")
        return fountQuestion
    }

    private fun getListQuestionListByLocal(
        listMap: MutableMap<Int, Boolean>,
        questionThisListAll: List<QuestionEntity>
    ): ArrayList<QuestionEntity> {
        val userLocalization: String = getUserLocalization(context)

        val questionList = ArrayList<QuestionEntity>()
        listMap.forEach { map ->
            val filteredList = questionThisListAll
                .filter { it.numQuestion == map.key }
                .filter { it.language == userLocalization }

            if (filteredList.isNotEmpty()) questionList.add(filteredList[0])
        }

        log("okokoko, questionList:$questionList")
        return questionList
    }

    private fun getQuestionDetail() {
        var listQuestionDetail = mutableListOf<QuestionDetailEntity>()
        questionDetailUseCase.getQuestionDetailList().forEach {
            if (it.idQuiz == this.idQuiz && it.hardQuiz == this.hardQuestion) listQuestionDetail.add(
                it
            )
        }
        questionDetailListThis = listQuestionDetail
    }

    private fun getMap(
        listQuestionEntity: List<QuestionEntity>,
        listMap: MutableMap<Int, Boolean>
    ): MutableMap<Int, Boolean> {
        listQuestionEntity.forEach {
            listMap[it.numQuestion] = false
        }
        return listMap
    }

    private fun translateForGoogleTranslate() {
        Toast.makeText(context, "Ошибка сравнивания вопросов", Toast.LENGTH_LONG).show()
    }

    private fun getUpdateAnswer(codeAnswer: String?): Boolean {
        if (codeAnswer == null || codeAnswer == "") return false
        codeAnswer.forEach {
            if (it == UNANSWERED_IN_CODE_ANSWER) return true
        }
        return false
    }

    fun trueButton() {
        if (questionListThis[currentIndex].answerQuestion) setTrueAnswer()
        else setFalseAnswer()
    }

    private fun setFalseAnswer() {
        log("setFalseAnswer")
        var codeAnswer = ""
        var i = 0
        repeat(this.codeAnswer.length) {
            codeAnswer += if (i == currentIndex) INCORRECTLY_ANSWERED_IN_CODE_ANSWER
            else this.codeAnswer[i]
            i++
        }
        this.codeAnswer = codeAnswer

        leftAnswer--

        updateQuestionDetail()
    }

    private fun updateQuestionDetail() {
        log("updateQuestionDetail()")
        questionDetailUseCase.updateQuestionDetail(
            QuestionDetailEntity(
                idThisQuestionDetail,
                idQuiz,
                TimeManager.getCurrentTime(),
                codeAnswer,
                hardQuestion,
                false
            )
        )
        setPercentResult()
        _setPetcentPBLiveData.value =
            (((numQuestion - leftAnswer).toDouble() / numQuestion) * 100).toInt()
        _setPercentiveData.value = persent
        _setLeftAnswerLiveData.value = leftAnswer
        if (leftAnswer == 0) result()
    }

    private fun setTrueAnswer() {
        log("setTrueAnswer")
        var codeAnswer = ""
        var i = 0
        repeat(this.codeAnswer.length) {
            codeAnswer += if (i == currentIndex) CORRECTLY_ANSWERED_IN_CODE_ANSWER
            else this.codeAnswer[i]
            i++
        }

        log("setTrueAnswer codeAnswer: ${codeAnswer}")
        log("setTrueAnswer this.codeAnswer: ${this.codeAnswer}")
        this.codeAnswer = codeAnswer

        leftAnswer--

        updateQuestionDetail()
    }

    fun falseButton() {
        if (!questionListThis[currentIndex].answerQuestion) setTrueAnswer()
        else setFalseAnswer()
    }

    private fun getLeftAnswer(codeAnswer: String?): Int {
        if (codeAnswer == "" || codeAnswer == null) return numQuestion
        var i = 0
        codeAnswer.forEach {
            if (it == UNANSWERED_IN_CODE_ANSWER) i++
        }
        return i
    }

    private fun result() {
        setPercentResult()
        Log.d("iofjerdklgj", "starsPercentAll ${persent}")
        showResultDialog()
    }

    private fun setPercentResult() {
        var i = 0
        codeAnswer.forEach {
            if (it == CORRECTLY_ANSWERED_IN_CODE_ANSWER) i++
        }
        numTrueQuestion = i

        i = 0
        var j = 0
        var iThis = 0
        var perc = mutableListOf<Int>()
        maxPersent = 0

        log("questionDetailListThis: $questionDetailListThis")
        questionDetailListThis.forEach {
            i = 0
            j = 0
            it.codeAnswer?.forEach { item ->
                if (item == CORRECTLY_ANSWERED_IN_CODE_ANSWER) i++
                j++
            }
            if (try {
                    ((100 * i) / j)
                } catch (e: Exception) {
                    0
                } > maxPersent
            ) maxPersent = ((100 * i) / j)
            try {
                perc.add(((100 * i) / j))
            } catch (e: Exception) {
                perc.add(0)
            }
        }
        j = 0
        i = 0
        perc.forEach {
            i += it
        }

        persentAll = try {
            i / perc.size
        } catch (e: Exception) {
            0
        }

        j = 0
        i = 0
        codeAnswer.forEach {
            if (it == CORRECTLY_ANSWERED_IN_CODE_ANSWER) {
                i++
            }
            j++
        }
        persent = ((100 * i) / j)
    }

    private fun showResultDialog() {
        val resultDialog = ResultDialog(
            hardQuestion,
            quizThis.event,
            quizThis.rating,
            this.persent,
            this.persentAll,
            this.persentPlayerAll,
            this.firstQuestionDetail,
            onDismissListener = { rating, result ->
                saveResult(rating, result)
            },
            onRatingSelected = { _ ->
                // Do something when the rating is selected
            },
            context = context, // Pass the context of the activity or fragment
            profile = profileUseCase.getProfile(getTpovId())
        )
        resultDialog.show()
        onCloseActivity()
    }


    private fun saveResult(rating: Int, result: Int) {
        profileUseCase.updateProfile(
            profileUseCase.getProfile(getTpovId()).copy(
                timeInGamesCountQuestions = profileUseCase.getProfile(getTpovId()).timeInGamesCountQuestions?.plus(
                    numQuestion
                ),
                timeInGamesCountTrueQuestion = profileUseCase.getProfile(getTpovId()).timeInGamesCountTrueQuestion.plus(
                    numTrueQuestion
                ) ?: 0,
                pointsNolics = (getNolic() + CalcValues.getValueNolicForGame(
                    hardQuestion,
                    persent,
                    quizThis.event,
                    firstQuestionDetail,
                    profileUseCase.getProfile(getTpovId())
                )),
                pointsSkill = (getSkill() + CalcValues.getValueSkillForFame(
                    hardQuestion,
                    persent,
                    quizThis.event,
                    firstQuestionDetail,
                    profileUseCase.getProfile(getTpovId())
                ))
            )
        )

        var perc = mutableListOf<Int>()
        log("saveResult getQuestionDetailList(): ${questionDetailUseCase.getQuestionDetailList()}")
        questionDetailUseCase.getQuestionDetailList().forEach {
            log("saveResult all detail: ${it}, it.idQuiz: ${it.idQuiz}, this.idQuiz: ${this@QuestionViewModel.idQuiz}")

            var i = 0
            var j = 0
            if (it.idQuiz == this.idQuiz) {

                it.codeAnswer?.forEach { item ->
                    if (item == CORRECTLY_ANSWERED_IN_CODE_ANSWER) i++
                    j++
                }

                if (!it.hardQuiz) {
                    try {
                        if (((100 * i) / j) > maxPersent) maxPersent = ((100 * i) / j)
                        perc.add(((100 * i) / j))
                    } catch (e: Exception) {
                        perc.add(0)
                    }
                } else {
                    try {
                        if ((((100 * i) / j) / 5) + 100 > maxPersent) maxPersent =
                            (((100 * i) / j) / 5) + 100
                        perc.add((((100 * i) / j) / 5) + 100)
                    } catch (e: Exception) {
                        perc.add(0)
                    }
                }

                var i = 0
                perc.forEach { itemPerc ->
                    i += itemPerc
                }
                persentAll = i / perc.size

                log("saveResult $maxPersent")

            }
        }

        when (quizThis.event) {
            EVENT_QUIZ_FOR_USER,
            EVENT_QUIZ_ARENA,
            EVENT_QUIZ_TOURNIRE,
            EVENT_QUIZ_TOURNIRE_LEADER,
            EVENT_QUIZ_HOME -> quizUseCase.updateQuiz(
                quizThis.copy(
                    stars = maxPersent,
                    starsAll = persentAll,
                    rating = rating
                )
            )

            EVENT_QUIZ_FOR_TESTER,
            EVENT_QUIZ_FOR_MODERATOR,
            EVENT_QUIZ_FOR_ADMIN -> updateEvent(rating)

        }

        someAction(result)
        Log.d("ku65k", "rating $rating")
    }

    private fun getNewIdQuiz(): Int {
        log("getNewIdQuiz()")
        var i = 0
        runBlocking {
            log("getNewIdQuiz 1")
            quizUseCase.getQuizList().forEach {
                log("getNewIdQuiz: it: ${it.id}")
                if (it.id!! in (i + 1)..100) {
                    i = it.id!!
                }
            }
        }
        return i + 1
    }

    private fun updateEvent(rating: Int) {

        newIdQuizVar = idQuiz
        log("DSEFSE, id: $newIdQuizVar")
        quizUseCase.insertQuiz(
            quizThis.copy(
                id = newIdQuizVar,
                ratingPlayer = rating,
                event = getEvent(rating),
                starsAll = 0,
                stars = 0,
                versionQuiz = quizThis.versionQuiz + 1
            )
        )

        questionDetailUseCase.deleteQuestionDetailByIdQuiz(idQuiz)
        insertQuizPlayers()

    }

    private fun getEvent(rating: Int) = if (rating == RATING_QUIZ_EVENT_BED) quizThis.event
                                        else quizThis.event + RATING_QUIZ_EVENT_BED

    private fun insertQuizPlayers() {

    }

    fun getCurrentTimer(typeQuestion: Boolean): Int {
        return if (typeQuestion) TIME_HARD_QUESTION
        else TIME_LIGHT_QUESTION
    }

    fun formatTime(millisUntilFinished: Long): String {
        val seconds = millisUntilFinished / MILLIS_IN_SECONDS
        val minutes = seconds / SECONDS_IN_MINUTES
        val leftSeconds = seconds - (minutes * SECONDS_IN_MINUTES)
        return String.format("%02d:%02d", minutes, leftSeconds)
    }

    companion object {
        const val MILLIS_IN_SECONDS = 1000L
        private const val SECONDS_IN_MINUTES = 60

        private const val TIME_HARD_QUESTION = 10
        private const val TIME_LIGHT_QUESTION = 20

        private const val MAX_PERCENT = 100
        private const val COEF_PERCENT_HARD_QUIZ =
            20 //Это нужно что-бы посчитать проценты сложных вопросов
    }
}

fun log(m: String) {
    Logcat.log(m, "Question", Logcat.LOG_VIEW_MODEL)
}