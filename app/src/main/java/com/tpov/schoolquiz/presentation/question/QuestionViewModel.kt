package com.tpov.schoolquiz.presentation.question

import android.app.Application
import android.content.Context
import android.os.CountDownTimer
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.*
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.data.database.entities.QuestionDetailEntity
import com.tpov.schoolquiz.domain.*
import com.tpov.schoolquiz.presentation.dialog.ResultDialog
import com.tpov.schoolquiz.presentation.network.event.log
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
    val getQuizLiveDataUseCase: GetQuizLiveDataUseCase
) : AndroidViewModel(application) {

    private lateinit var context: Context
    var timer: CountDownTimer? = null

    var codeAnswer = ""         //Отображает состояние квеста для всех вопросов
    var currentIndex = 0      //Номер вопроса, который виден пользователю
    var numQuestion = 0
    var hardQuestion = false
    var userName = ""
    var idQuiz = 0
    var createQuestionDetail = true
    var leftAnswer = 0
    var life = 0
    var idThisQuestionDetail = 0
    var persent = 0
    var maxPersent = 0
    var persentAll = 0
    lateinit var questionListThis: List<QuestionEntity>
    lateinit var questionDetailListThis: List<QuestionDetailEntity>
    lateinit var quizThis: QuizEntity
    lateinit var tpovId: String

    private val _shouldCloseLiveData = MutableLiveData<Boolean>()
    val shouldCloseLiveData: LiveData<Boolean> = _shouldCloseLiveData

    private fun someAction() {
        _shouldCloseLiveData.postValue(true)
    }

    private var _viewResultLiveData = MutableLiveData<String>()
    var viewResultLiveData: LiveData<String> = _viewResultLiveData

    fun synthWithDB(context: Context) {
        initConst(context)
        getQuestionsList()
        initVariable()
    }

    private fun initConst(context: Context) {
        this.context = context
        val sharedPref = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
        tpovId = (sharedPref?.getInt("tpovId", 0) ?: 0).toString()
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
        } catch (e: Exception) {
            errorLoadQuestion(questionDetailEntity)
        }
    }

    private fun getCurrentIndex(codeAnswer: String?): Int? {
        if (codeAnswer == null || codeAnswer == "") return 0
        for (i in codeAnswer.indices) {
            if (codeAnswer[i] == '0') return i
        }
        return null
    }

    private fun errorLoadQuestion(questionDetailEntity: QuestionDetailEntity): String {
        Toast.makeText(
            getApplication(),
            "Ошибка в базе данных, ждите пока разработчики пофиксят",
            Toast.LENGTH_LONG
        ).show()

        return ""
    }

    private fun getQuestionsList() {
        questionListThis = getQuestionByIdQuizUseCase(idQuiz)
        var list = mutableListOf<QuestionEntity>()
        questionListThis.forEach {
            if (it.hardQuestion == hardQuestion) list.add(it)
        }

        questionListThis = list

        var listQuestionDetail = mutableListOf<QuestionDetailEntity>()
        getQuestionDetailListUseCase().forEach {
            if (it.idQuiz == this.idQuiz) listQuestionDetail.add(
                it
            )
        }
        questionDetailListThis = listQuestionDetail
        quizThis = getQuizUseCase(idQuiz, tpovId.toInt())
    }


    private fun getUpdateAnswer(codeAnswer: String?): Boolean {
        if (codeAnswer == null || codeAnswer == "") return false
        codeAnswer.forEach {
            if (it == '0') return true
        }
        return false
    }

    private fun useCheat() {
        life - 1
    }


    fun trueButton() {
        if (questionListThis[currentIndex].answerQuestion) setTrueAnswer()
        else setFalseAnswer()
    }

    private fun setFalseAnswer() {
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
        var codeAnswer = ""
        var i = 0
        repeat(this.codeAnswer.length) {
            codeAnswer += if (i == currentIndex) '2'
            else this.codeAnswer[i]
            i++
        }
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

        questionDetailListThis.forEach {
            i = 0
            j = 0
            it.codeAnswer?.forEach { item ->
                if (item == '2') i++
                j++
            }
            if (((100 * i) / j) > maxPersent) maxPersent = ((100 * i) / j)
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
            quizThis.event,
            quizThis.rating,
            this.persent,
            this.persentAll,
            onDismissListener = { rating ->
                saveResult(rating)
            },
            onRatingSelected = { _ ->
                // Do something when the rating is selected
            },
            context = context // Pass the context of the activity or fragment
        )
        resultDialog.show()
    }

    private fun saveResult(rating: Int) {
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

        someAction()
        Log.d("ku65k", "rating $rating")
    }

    private fun updateEvent(rating: Int) {
        deleteQuizUseCase(idQuiz)
        deleteQuestionByIdQuizUseCase(idQuiz)
        deleteQuestionDetailByIdQuiz(idQuiz)
        insertQuizPlayers()
        if (getRating(rating) != 0) insertQuizUseCase(
            quizThis.copy(
                id = null,
                event = getRating(rating),
                rating = rating,
                starsAll = 0,
                stars = 0
            )
        )
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