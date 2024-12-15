package com.tpov.common.presentation.question

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tpov.common.CODE_EMPTY_ANSWER
import com.tpov.common.CODE_MAX_SCORE_ANSWER
import com.tpov.common.CODE_MIN_SCORE_ANSWER
import com.tpov.common.COUNT_VARIATION_CODE_ANSWER
import com.tpov.common.MAX_DROP_ANSWER
import com.tpov.common.SPLIT_BETWEEN_LANGUAGES
import com.tpov.common.data.model.local.QuestionDetailEntity
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.remote.StructureLocalDataRemote
import com.tpov.common.domain.usecase.QuestionDetailUseCase
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.QuizUseCase
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.presentation.Errors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@InternalCoroutinesApi
class QuestionViewModel @Inject constructor(
    var app: Application,
    var quizUseCase: QuizUseCase,
    var questionUseCase: QuestionUseCase,
    var questionDetailUseCase: QuestionDetailUseCase,
    val structureUseCase: StructureUseCase
) : AndroidViewModel(app), Errors() {

    var newAnswerOrder: Int = 0
    var originalAnswerOrder: Int = 0
    var numQuestions: Int? = null
    var hardQuiz: Boolean? = null
    var idQuiz: Int? = null
    var life: Int? = null

    var oldCurrentQuestion = 0
    var codeAnswer = ""

    val unknownCurrentQuestion = -1

    val result: StateFlow<Int?> get() = _result
    private val _result = MutableStateFlow<Int?>(unknownCurrentQuestion)
    val currentQuestion: StateFlow<Int?> get() = _currentQuestion
    private val _currentQuestion = MutableStateFlow<Int?>(-1)

    val quiz: StateFlow<QuizEntity?> get() = _quiz
    private val _quiz = MutableStateFlow<QuizEntity?>(null)
    val questionList: StateFlow<List<QuestionEntity>?> get() = _questionList
    private val _questionList = MutableStateFlow<List<QuestionEntity>?>(null)
    val questionDetailList: StateFlow<List<QuestionDetailEntity>?> get() = _questionDetailList
    private val _questionDetailList = MutableStateFlow<List<QuestionDetailEntity>?>(null)

    val questionDetail: StateFlow<QuestionDetailEntity?> get() = _questionDetail
    private val _questionDetail = MutableStateFlow<QuestionDetailEntity?>(null)

    val springAnim: StateFlow<Boolean?> get() = _springAnim
    private val _springAnim = MutableStateFlow<Boolean?>(null)

    val closeActivity: StateFlow<Boolean> get() = _closeActivity
        private val _closeActivity = MutableStateFlow(false)

//--------------------------------------------USE CASES---------------------------------------------

    fun saveQuizResult() = viewModelScope.launch(Dispatchers.IO) {
        quizUseCase.saveQuiz(_quiz.value?.copy(
            starsMaxLocal = quiz.value?.starsMaxLocal ?: _quiz.value?.starsMaxLocal ?: notFoundQuizValue(),
            starsAverageLocal = quiz.value?.starsAverageLocal ?: _quiz.value?.starsAverageLocal ?: notFoundQuizValue(),
            ratingLocal = quiz.value?.ratingLocal ?: _quiz.value?.ratingLocal ?: notFoundQuizValue()) ?: notFoundQuiz())
    }

    fun getQuizById() = viewModelScope.launch(Dispatchers.IO) {
        _quiz.value = quizUseCase.getQuizById(idQuiz ?: notFoundInputData())
    }

//    fun saveQuestion(questionEntity: QuestionEntity) = viewModelScope.launch(Dispatchers.IO) {
//        questionUseCase.saveQuestion(questionEntity)
//    }

    fun getQuestionList(languagesUser: String) = viewModelScope.launch(Dispatchers.IO) {
        val filterQuestionByIdQuiz = questionUseCase.getQuestionByIdQuiz(idQuiz ?: notFoundInputData())
        val filterQuestionByHardQuiz = filterQuestionByHardQuiz(filterQuestionByIdQuiz, hardQuiz ?: notFoundInitTypeHardQuestion())
        var filterQuestionByLanguage = filterQuestionByMainLanguageUser(filterQuestionByHardQuiz)
        if (filterQuestionByLanguage.size < (numQuestions ?: notFoundNumberQuestionByTypeHardQuiz()))
            filterQuestionByLanguage = filterQuestionByOtherLanguageUser(filterQuestionByHardQuiz, languagesUser, numQuestions ?: notFoundNumberQuestionByTypeHardQuiz())
        if (filterQuestionByLanguage.isEmpty()) notFountQuestionByLanguageUser()
        else _questionList.value = filterQuestionByLanguage.sortedBy { it.numQuestion }
    }

    fun getQuestionDetailByIdQuiz() = viewModelScope.launch(Dispatchers.IO) {
        _questionDetailList.value = questionDetailUseCase.getQuestionDetailByIdQuiz(idQuiz ?: notFoundInputData())
            ?.filter { it.hardQuiz == hardQuiz } ?: listOf(QuestionDetailEntity(0, idQuiz ?: notFoundInputData(), getDataToday(),
            "0".repeat(numQuestions ?: notFoundQuizValue()),
            hardQuiz ?: notFoundInitTypeHardQuestion(), false))
    }

    fun saveQuestionDetail() = viewModelScope.launch(Dispatchers.IO) {
            questionDetailUseCase.saveQuestionDetail(QuestionDetailEntity(
                0, idQuiz ?: notFoundQuizValue(), getDataToday(), codeAnswer,
                hardQuiz ?: notFoundQuizValue().toString().toBoolean(), false))
        }


    private fun getDataToday() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun updateQuestionDetail(questionDetailEntity: QuestionDetailEntity) =
        viewModelScope.launch(Dispatchers.IO) {
            questionDetailUseCase.updateQuestionDetail(questionDetailEntity)
        }

    fun pushStructureLocalData(ratingData: StructureLocalDataRemote) =
        viewModelScope.launch(Dispatchers.IO) {
            structureUseCase.pushStructureRating(ratingData)
        }
    fun deleteQuestionDetailById(id: Int?): String {
        viewModelScope.launch(Dispatchers.IO) {
            //questionDetailUseCase.deleteQuestionDetailById(id)
        }
        return ""
    }


    //--------------------------------------------OTHER FUN---------------------------------
    private fun filterQuestionByHardQuiz(questionEntityList: List<QuestionEntity>, hardQuiz: Boolean) =
        questionEntityList.filter { it.hardQuestion == hardQuiz }

    private fun filterQuestionByMainLanguageUser(questionList: List<QuestionEntity>) =
        questionList.filter { it.language == Locale.getDefault().language }

    private fun filterQuestionByOtherLanguageUser(questionList: List<QuestionEntity>, languages: String, numQuestion: Int): List<QuestionEntity> {
        for (language in languages.split(SPLIT_BETWEEN_LANGUAGES)) {
            val questionsForLanguage = questionList.filter { it.language == language }
            if (questionsForLanguage.size >= numQuestion) return questionsForLanguage
        }
        return emptyList()
    }

    fun initQuizValues() {
        numQuestions = if (hardQuiz ?: notFoundInitTypeHardQuestion()) quiz.value?.numHQ ?: notFoundNumberQuestionByTypeHardQuiz()
        else quiz.value?.numQ ?: notFoundNumberQuestionByTypeHardQuiz()

    }

    fun initQuestionValues() {
        _currentQuestion.value = 0
    }

    fun initQuestionDetail() {
        _questionDetail.value = questionDetailList.value?.find { questionDetail ->
            questionDetail.codeAnswer?.any { it == '0' } ?: false
        } ?: QuestionDetailEntity(0, idQuiz ?: notFoundInputData(), getDataToday(),
            "0".repeat(numQuestions ?: notFoundQuizValue()),
            hardQuiz ?: notFoundInitTypeHardQuestion(), false)
    }

    private fun calculateResultByCodeAnswer(codeAnswerThis: String) = codeAnswerThis.map {
        (((it.toInt() - CODE_MIN_SCORE_ANSWER.toInt()) / COUNT_VARIATION_CODE_ANSWER) * 100)
    }.average().toInt()

    private fun calculateStarsMaxLocal(): Int {
        val list = questionDetailList.value
        if (list.isNullOrEmpty()) {
            return 0
        }
        return list.maxOf {
            calculateResultByCodeAnswer(it.codeAnswer ?: deleteQuestionDetailById(it.id))
        }
    }


    private fun calculateStarsAverageLocal() = questionDetailList.value?.map {
        calculateResultByCodeAnswer(it.codeAnswer ?: deleteQuestionDetailById(it.id))
    }?.average()?.toInt() ?: 0

    fun setNewCurrentQuestion(current: Int) {
        _currentQuestion.value = current
    }

    fun setNextCurrentQuestion(current: Int) {
        _currentQuestion.value = current
    }

    fun setPrefCurrentQuestion(current: Int) {
        _currentQuestion.value = current
    }

    fun result() {
        _quiz.value?.starsMaxLocal = calculateStarsMaxLocal()
        _quiz.value?.starsAverageLocal = calculateStarsAverageLocal()
        _result.value = calculateResultByCodeAnswer(codeAnswer)

        saveQuestionDetail()
    }

    fun checkAnswer(selectedTags: List<Int>, is4Button: Boolean) {
        if (selectedTags.isEmpty()) return
        val score: Int

        if (is4Button) {
            val correctAnswerIndex = originalAnswerOrder.toString().toIntOrNull() ?: return
            score = if (selectedTags.contains(correctAnswerIndex)) CODE_MIN_SCORE_ANSWER.toInt()
            else CODE_MAX_SCORE_ANSWER.toInt()
        } else {
            var correctCount = 0

            val originalOrder = originalAnswerOrder.toString()
                .take(MAX_DROP_ANSWER).map { it.toString().toInt() }
            val totalCorrectAnswers = originalOrder.size

            for (i in selectedTags.indices) {
                val originalAnswer = originalOrder.getOrNull(i) ?: continue
                val selectedAnswer = selectedTags.getOrNull(i) ?: continue
                if (originalAnswer == selectedAnswer) correctCount += 1
            }

            val percentage = (correctCount.toFloat() / totalCorrectAnswers) * 100
            score = ((COUNT_VARIATION_CODE_ANSWER * percentage) / 100).toInt() + 1
        }

        setCodeInCodeAnswer(score)
        setNextQuestion()
    }

    fun setNextQuestion() {
        if (currentQuestion.value?.plus(1)!! >= numQuestions!!) _springAnim.value = true
        else setNewCurrentQuestion(currentQuestion.value?.plus(1)!!)
    }

    private fun setPrefQuestion() {
        if (currentQuestion.value?.plus(1)!! <= 1) _springAnim.value = false
        else setNewCurrentQuestion(currentQuestion.value?.minus(1)!!)
    }

    fun setCodeInCodeAnswer(score: Int) {
        val index: Int = currentQuestion.value ?: errorGetNumQuestion()

        codeAnswer = if (index < codeAnswer.length) {
            codeAnswer.substring(0, index) + score.toString() +
                    codeAnswer.substring(index + 1)
        } else codeAnswer.padEnd(index, CODE_EMPTY_ANSWER) + score.toString()
        if (!codeAnswer.contains(CODE_EMPTY_ANSWER)) result()
    }

    //--------------------------------Exceptions------------------------


    override fun closeActivity() {
        _closeActivity.value = true
    }
}