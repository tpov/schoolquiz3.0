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
import com.tpov.schoolquiz.data.database.entities.QuestionDetailEntity
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.domain.DeleteQuestionByIdQuizUseCase
import com.tpov.schoolquiz.domain.DeleteQuestionDetailByIdQuiz
import com.tpov.schoolquiz.domain.DeleteQuizUseCase
import com.tpov.schoolquiz.domain.GetProfileUseCase
import com.tpov.schoolquiz.domain.GetQuestionDetailListUseCase
import com.tpov.schoolquiz.domain.GetQuestionListByIdQuiz
import com.tpov.schoolquiz.domain.GetQuizByIdUseCase
import com.tpov.schoolquiz.domain.GetQuizListUseCase
import com.tpov.schoolquiz.domain.GetQuizLiveDataUseCase
import com.tpov.schoolquiz.domain.InsertInfoQuestionUseCase
import com.tpov.schoolquiz.domain.InsertQuestionUseCase
import com.tpov.schoolquiz.domain.InsertQuizUseCase
import com.tpov.schoolquiz.domain.UpdateProfileUseCase
import com.tpov.schoolquiz.domain.UpdateQuestionDetailUseCase
import com.tpov.schoolquiz.domain.UpdateQuizUseCase
import com.tpov.schoolquiz.presentation.custom.CalcValues
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getNolic
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getSkill
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.dialog.ResultDialog
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.coroutines.InternalCoroutinesApi
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
    val getQuizByIdUseCase: GetQuizByIdUseCase
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
    var questionListThis = ArrayList<QuestionEntity>()
    lateinit var questionDetailListThis: List<QuestionDetailEntity>
    lateinit var quizThis: QuizEntity
    lateinit var tpovId: String
    var resultTranslate = true
    var persentPlayerAll = 0
    var firstQuestionDetail = true

    private val _shouldCloseLiveData = MutableLiveData<Int>()
    val shouldCloseLiveData: LiveData<Int> = _shouldCloseLiveData

    private fun someAction(result: Int) {
        _shouldCloseLiveData.postValue(result)
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

        questionDetailListThis.forEach {

            if (it.hardQuiz == this.hardQuestion) {
                if (getUpdateAnswer(it.codeAnswer)) initOldQuestionDetail(it)
            }
        }
        if (createQuestionDetail) initNewQuestionDetail()
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
        log(
            "getQuestionsList, getQuestionByIdQuizUseCase(idQuiz) ${
                getQuestionByIdQuizUseCase(
                    idQuiz
                )
            }"
        )
        val userLocalization: String = getUserLocalization(context)
        val availableLanguages = getProfileUseCase(getTpovId()).languages

        val questionThisListAll = getQuestionByIdQuizUseCase(idQuiz)
        val sizeQuestionList = questionThisListAll.size

        var listMap = mutableMapOf<Int, Boolean>()
        log("ыуаыуаыуаы Перебор квестов, найдено $sizeQuestionList шт.")
        for (i in 0 until sizeQuestionList) {
            log("ыуаыуаыуаы берем $i. ${questionThisListAll[i].nameQuestion} ===============================================")
            if (questionThisListAll[i].hardQuestion != hardQuestion && listMap[i] != false) continue

            log("ыуаыуаыуаы этот вопрос совпал с нужным типом $i. ${questionThisListAll[i].hardQuestion}")
            for (j in i + 1 until sizeQuestionList) {
                if (questionThisListAll[j].hardQuestion != hardQuestion && listMap[j] != false) continue
                log("ыуаыуаыуаы берем второй вопрос $j. ${questionThisListAll[j].nameQuestion} ----------------------")
                if (questionThisListAll[i].numQuestion == questionThisListAll[j].numQuestion) {

                    log("ыуаыуаыуаы этот вопрос является конкурентом, нужно выбрать один по языку системы")
                    if (userLocalization.equals(
                            questionThisListAll[i].language,
                            ignoreCase = true
                        ) && questionThisListAll[i].lvlTranslate >= 100 || userLocalization.equals(
                            questionThisListAll[i].language,
                            ignoreCase = true
                        ) && getQuizByIdUseCase(
                            questionThisListAll[i].idQuiz
                        ).tpovId == getTpovId()
                    ) {
                        log("ыуаыуаыуаы первый вопрос прошел ${questionThisListAll[i].nameQuestion}, ${questionThisListAll[i].language}")
                        questionListThis.add(questionThisListAll[i])
                        listMap[i] = false
                        listMap[j] = false
                        break
                    } else if (userLocalization.equals(
                            questionThisListAll[j].language,
                            ignoreCase = true
                        ) && questionThisListAll[j].lvlTranslate >= 100 || userLocalization.equals(
                            questionThisListAll[j].language,
                            ignoreCase = true
                        ) && getQuizByIdUseCase(
                            questionThisListAll[j].idQuiz
                        ).tpovId == getTpovId()
                    ) {
                        log("ыуаыуаыуаы второй вопрос прошел ${questionThisListAll[j].nameQuestion}, ${questionThisListAll[j].language}")
                        questionListThis.add(questionThisListAll[j])
                        listMap[i] = false
                        listMap[j] = false
                        break
                    }
                }
            }

            log("ыуаыуаыуаы $listMap")
            if (listMap[i] != false) {
                log("ыуаыуаыуаы ищем по языку который знает пользователь +++++++++++++++++++++++++++++")
                for (j in i + 1 until sizeQuestionList) {
                    if (questionThisListAll[j].hardQuestion != hardQuestion && listMap[j] != false) continue
                    if (questionThisListAll[i].numQuestion == questionThisListAll[j].numQuestion) {
                        if (listMap[j] != false) {
                            log("ыуаыуаыуаы берем второй вопрос $j. ${questionThisListAll[j].nameQuestion} +++++++++++++")
                            val words = availableLanguages?.split("|")

                            if (words != null) {
                                for (word in words) {

                                    if (questionThisListAll[i].language.equals(
                                            word,
                                            ignoreCase = true
                                        )
                                    ) {
                                        log("ыуаыуаыуаы первый вопрос прошел ${questionThisListAll[i].nameQuestion}, ${questionThisListAll[i].language}")
                                        questionListThis.add(questionThisListAll[i])
                                        listMap[i] = false
                                        listMap[j] = false
                                        break
                                    } else if (questionThisListAll[j].language.equals(
                                            word,
                                            ignoreCase = true
                                        )
                                    ) {
                                        log("ыуаыуаыуаы второй вопрос прошел ${questionThisListAll[j].nameQuestion}, ${questionThisListAll[j].language}")
                                        questionListThis.add(questionThisListAll[j])
                                        listMap[i] = false
                                        listMap[j] = false
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (listMap[i] != false) {
                if (userLocalization.equals(
                        questionThisListAll[i].language,
                        ignoreCase = true
                    ) && questionThisListAll[i].lvlTranslate >= 100 || userLocalization.equals(
                        questionThisListAll[i].language,
                        ignoreCase = true
                    ) && getQuizByIdUseCase(
                        questionThisListAll[i].idQuiz
                    ).tpovId == getTpovId()
                ) {
                    log("ыуаыуаыуаы первый вопрос прошел ${questionThisListAll[i].nameQuestion}, ${questionThisListAll[i].language}")
                    questionListThis.add(questionThisListAll[i])
                    listMap[i] = false
                    continue
                } else {
                    val words = availableLanguages?.split("|")

                    if (words != null) {
                        for (word in words) {

                            if (questionThisListAll[i].language.equals(
                                    word,
                                    ignoreCase = true
                                )
                            ) {
                                log("ыуаыуаыуаы первый вопрос прошел ${questionThisListAll[i].nameQuestion}, ${questionThisListAll[i].language}")
                                questionListThis.add(questionThisListAll[i])
                                listMap[i] = false
                                break
                            }
                        }
                    }
                }
            }
        }

        log("getQuestionsList, questionListThis: $questionListThis")
        var listQuestionDetail = mutableListOf<QuestionDetailEntity>()
        getQuestionDetailListUseCase().forEach {
            if (it.idQuiz == this.idQuiz && it.hardQuiz == this.hardQuestion) listQuestionDetail.add(
                it
            )
        }
        questionDetailListThis = listQuestionDetail

        quizThis = getQuizUseCase(idQuiz)
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
        getQuestionsList()
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
            perc.add(((100 * i) / j))
        }
        j = 0
        i = 0
        perc.forEach {
            i += it
        }

        persentAll = i / perc.size

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

        log("saveResultawd getProfileUseCase(getTpovId()).copy(pointsNolics = getNolic() + CalcValues.getValueNolicForGame(hardQuestion, rating))")
        log("saveResultawd: getNolic():${getNolic()}")
        log(
            "saveResultawd: ${
                getProfileUseCase(getTpovId()).copy(
                    pointsNolics = (getNolic() + CalcValues.getValueNolicForGame(
                        hardQuestion,
                        persent,
                        quizThis.event,
                        firstQuestionDetail,
                        getProfileUseCase(getTpovId())
                    ))
                )
            }"
        )
        log(
            "saveResultawd: ${
                CalcValues.getValueNolicForGame(
                    hardQuestion, this.persent,
                    quizThis.event,
                    firstQuestionDetail,
                    getProfileUseCase(getTpovId())
                )
            }"
        )
        updateProfileUseCase(
            getProfileUseCase(getTpovId()).copy(
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
                    if (((100 * i) / j) > maxPersent) maxPersent = ((100 * i) / j)
                    perc.add(((100 * i) / j))
                } else {
                    if ((((100 * i) / j) / 5) + 100 > maxPersent) maxPersent =
                        (((100 * i) / j) / 5) + 100
                    perc.add((((100 * i) / j) / 5) + 100)
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
        var i = 0

        getQuizListUseCase(getTpovId()).forEach {
            log("getNewIdQuiz: it: ${it.id}")
            if (it.id!! in (i + 1)..100) {
                i = it.id!!
            }
        }
        return i + 1
    }

    private fun updateEvent(rating: Int) {

        newIdQuizVar = getNewIdQuiz()
        if (getRating(rating) != 0) {
            log("DSEFSE, it: quiz")
            insertQuizUseCase(
                quizThis.copy(
                    id = newIdQuizVar,
                    event = getRating(rating),
                    rating = rating,
                    starsAll = 0,
                    stars = 0
                )
            )
            getQuestionByIdQuizUseCase(idQuiz).forEach {
                log("DSEFSE, it: $it")
                insertQuestionUseCase(it.copy(idQuiz = newIdQuizVar))
            }

        }
        deleteQuizUseCase(idQuiz)
        deleteQuestionByIdQuizUseCase(idQuiz)
        deleteQuestionDetailByIdQuiz(idQuiz)
        insertQuizPlayers()

    }

    private fun getRating(rating: Int): Int {
        log("fun getRating: $rating")
        return if (rating == 1) 0
        else quizThis.event + 1
    }

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