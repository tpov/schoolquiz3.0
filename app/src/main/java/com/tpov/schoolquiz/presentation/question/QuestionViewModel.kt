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
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.database.entities.QuestionDetailEntity
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.domain.*
import com.tpov.schoolquiz.presentation.Core.RATING_QUIZ_EVENT_BED
import com.tpov.schoolquiz.presentation.custom.CalcValues
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getNolic
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getSkill
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.dialog.ResultDialog
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@InternalCoroutinesApi
class QuestionViewModel @Inject constructor(
    application: Application,
    val getQuestionByIdQuizUseCase: GetQuestionListByIdQuiz,
    val getQuestionDetailListUseCase: GetQuestionDetailListUseCase,
    val getQuizUseCase: GetQuizByIdUseCase,
    val insertQuestionDetailEntity: InsertInfoQuestionUseCase,
    val updateQuestionDetailUseCase: UpdateQuestionDetailUseCase,
    val updateQuizUseCase: UpdateQuizUseCase,
    val deleteQuizUseCase: DeleteQuizUseCase,
    val insertQuizUseCase: InsertQuizUseCase,
    val deleteQuestionByIdQuizUseCase: DeleteQuestionByIdQuizUseCase,
    val deleteQuestionDetailByIdQuiz: DeleteQuestionDetailByIdQuiz,
    val insertQuestionUseCase: InsertQuestionUseCase,
    val getQuizLiveDataUseCase: GetQuizLiveDataUseCase,
    val getProfileUseCase: GetProfileUseCase,
    val updateProfileUseCase: UpdateProfileUseCase,
    val getQuizListUseCase: GetQuizListUseCase,
    val getQuizByIdUseCase: GetQuizByIdUseCase,
    val getQuizEventUseCase: GetQuizEventUseCase
) : AndroidViewModel(application) {

    private lateinit var context: Context
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
        return getProfileUseCase(getTpovId())
    }

    fun synthWithDB(context: Context) {
        initConst(context)
        getQuestionsList()
        initVariable()
    }

    private fun initConst(context: Context) {
        this.context = context
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
        this.persentPlayerAll = getQuizUseCase(idQuiz).starsAllPlayer

        insertQuestionDetailEntity(
            QuestionDetailEntity(
                null,
                idQuiz,
                TimeManager.getCurrentTime(),
                codeAnswer,
                hardQuestion,
                false
            )
        )

        getQuestionDetailListUseCase().forEach {
            if (it.hardQuiz == this.hardQuestion) {
                firstQuestionDetail = false
                if (getUpdateAnswer(it.codeAnswer)) this.idThisQuestionDetail = it.id!!
            }
        }
    }

    private fun getCodeAnswer(size: Int): String {
        var newCodeAnswer = ""
        for (i in 0 until size) {
            newCodeAnswer += '0'
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
            this.persentPlayerAll = getQuizUseCase(idQuiz).starsAllPlayer
        } catch (e: Exception) {
            errorLoadQuestion()
        }
    }

    private fun getCurrentIndex(codeAnswer: String?): Int? {
        if (codeAnswer == null || codeAnswer == "") return 0
        for (i in codeAnswer.indices) {
            if (codeAnswer[i] == '0') return i
        }
        return null
    }

    private fun errorLoadQuestion(): String {
        Toast.makeText(
            getApplication(),
            "Ошибка в базе данных, ждите пока разработчики пофиксят",
            Toast.LENGTH_LONG
        ).show()

        return ""
    }

    private fun getUserLocalization(context: Context): String {
        val config: Configuration = context.resources.configuration
        return config.locale.language
    }

    private fun getQuestionsList() {

        val questionThisListAll = getQuestionByIdQuizUseCase(idQuiz)
            .filter { it.hardQuestion == hardQuestion }
            .sortedBy { it.id }

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
        Questionlist.questionListThis = ArrayList(this.questionListThis.sortedBy { it.id })

        if (!didFoundAllQuestion(this.questionListThis, listMap)) Toast.makeText(
            context,
            "Error found questions",
            Toast.LENGTH_LONG
        ).show()
        getQuestionDetail()

        quizThis = getQuizUseCase(idQuiz)
    }

    private fun getListQuestionByProfileLang(
        questionThisListAll: List<QuestionEntity>,
        listMap: MutableMap<Int, Boolean>
    ): ArrayList<QuestionEntity> {
        val userLocalization: String = getUserLocalization(context)
        val availableLanguages = getProfileUseCase(getTpovId()).languages

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
        getQuestionDetailListUseCase().forEach {
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
            if (it == '0') return true
        }
        return false
    }

    private fun useCheat() {
        updateProfileUseCase(
            getProfileUseCase(getTpovId()).copy(
                count = getProfileUseCase(getTpovId()).count?.minus(
                    1000
                )
            )
        )
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
            codeAnswer += if (i == currentIndex) '1'
            else this.codeAnswer[i]
            i++
        }
        this.codeAnswer = codeAnswer

        leftAnswer--

        updateQuestionDetail()
    }

    private fun updateQuestionDetail() {
        log("updateQuestionDetail()")
        updateQuestionDetailUseCase(
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
            codeAnswer += if (i == currentIndex) '2'
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
            if (it == '0') i++
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
            if (it == '2') i++
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
                if (item == '2') i++
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
            if (it == '2') {
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
            profile = getProfileUseCase(getTpovId())
        )
        resultDialog.show()
    }

    private fun saveResult(rating: Int, result: Int) {
        updateProfileUseCase(
            getProfileUseCase(getTpovId()).copy(
                timeInGamesCountQuestions = getProfileUseCase(getTpovId()).timeInGamesCountQuestions?.plus(
                    numQuestion
                ),
                timeInGamesCountTrueQuestion = getProfileUseCase(getTpovId()).timeInGamesCountTrueQuestion.plus(
                    numTrueQuestion
                ) ?: 0,
                pointsNolics = (getNolic() + CalcValues.getValueNolicForGame(
                    hardQuestion,
                    persent,
                    quizThis.event,
                    firstQuestionDetail,
                    getProfileUseCase(getTpovId())
                )),
                pointsSkill = (getSkill() + CalcValues.getValueSkillForFame(
                    hardQuestion,
                    persent,
                    quizThis.event,
                    firstQuestionDetail,
                    getProfileUseCase(getTpovId())
                ))
            )
        )

        var perc = mutableListOf<Int>()
        log("saveResult getQuestionDetailListUseCase(): ${getQuestionDetailListUseCase()}")
        getQuestionDetailListUseCase().forEach {
            log("saveResult all detail: ${it}, it.idQuiz: ${it.idQuiz}, this.idQuiz: ${this@QuestionViewModel.idQuiz}")

            var i = 0
            var j = 0
            if (it.idQuiz == this.idQuiz) {

                it.codeAnswer?.forEach { item ->
                    if (item == '2') i++
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

                var j = 0
                var i = 0
                perc.forEach { itemPerc ->
                    i += itemPerc
                }
                persentAll = i / perc.size

                log("saveResult $maxPersent")

                when (quizThis.event) {
                    1, 5, 6, 7, 8 -> updateQuizUseCase(
                        quizThis.copy(
                            stars = maxPersent,
                            starsAll = persentAll,
                            rating = rating
                        )
                    )

                    2, 3, 4 -> updateEvent(rating)

                }

                someAction(result)
                Log.d("ku65k", "rating $rating")
            }
        }
    }

    private fun getNewIdQuiz(): Int {
        log("getNewIdQuiz()")
        var i = 0
        runBlocking {
            log("getNewIdQuiz 1")
            getQuizEventUseCase().forEach {
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
        insertQuizUseCase(
            quizThis.copy(
                id = newIdQuizVar,
                ratingPlayer = rating,
                event = getEvent(rating),
                starsAll = 0,
                stars = 0,
                versionQuiz = quizThis.versionQuiz + 1
            )
        )

        deleteQuestionDetailByIdQuiz(idQuiz)
        insertQuizPlayers()

    }

    private fun getEvent(rating: Int) = if (rating == 1) quizThis.event
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