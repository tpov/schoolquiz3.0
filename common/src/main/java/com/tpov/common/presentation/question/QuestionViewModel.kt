package com.tpov.common.presentation.question

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tpov.common.data.model.local.QuestionDetailEntity
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.remote.StructureLocalData
import com.tpov.common.domain.QuestionDetailUseCase
import com.tpov.common.domain.QuestionUseCase
import com.tpov.common.domain.QuizUseCase
import com.tpov.common.domain.StructureUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@InternalCoroutinesApi
class QuestionViewModel @Inject constructor(
    var application: Application,
    var quizUseCase: QuizUseCase,
    var questionUseCase: QuestionUseCase,
    var questionDetailUseCase: QuestionDetailUseCase,
    val structureUseCase: StructureUseCase
) : AndroidViewModel(application) {

    var numQuestions: Int? = null
    var hardQuiz: Boolean? = null

    var event: Int? = null
    var starsAverageLocal: Int? = null
    var starsMaxLocal: Int? = null
    var starsMaxRemote: Int? = null
    var ratingLocal: Int? = null

    var oldCurrentQuestion = 0
    var codeAnswer = ""

    val result: StateFlow<Int?> get() = _result
    private val _result = MutableStateFlow<Int?>(0)
    val currentQuestion: StateFlow<Int?> get() = _currentQuestion
    private val _currentQuestion = MutableStateFlow<Int?>(0)

    val quiz: StateFlow<QuizEntity?> get() = _quiz
    private val _quiz = MutableStateFlow<QuizEntity?>(null)
    val questionList: StateFlow<List<QuestionEntity>?> get() = _questionList
    private val _questionList = MutableStateFlow<List<QuestionEntity>?>(null)
    val questionDetailList: StateFlow<List<QuestionDetailEntity>?> get() = _questionDetailList
    private val _questionDetailList = MutableStateFlow<List<QuestionDetailEntity>?>(null)

    fun initValues(hardQuizValue: Boolean) {
        hardQuiz = hardQuizValue
    }

//--------------------------------------------USE CASES------------------------------

    fun saveQuizResult() = viewModelScope.launch(Dispatchers.IO) {
        quizUseCase.saveQuiz(_quiz.value.copy(starsMaxLocal, ))
    }
    fun getQuizById(idQuiz: Int) = viewModelScope.launch(Dispatchers.IO) {
        _quiz.value = quizUseCase.getQuizById(idQuiz)
    }

//    fun saveQuestion(questionEntity: QuestionEntity) = viewModelScope.launch(Dispatchers.IO) {
//        questionUseCase.saveQuestion(questionEntity)
//    }

    fun getQuestionList(idQuiz: Int, languagesUser: String) = viewModelScope.launch(Dispatchers.IO) {
        val filterQuestionByIdQuiz = questionUseCase.getQuestionByIdQuiz(idQuiz)
        val filterQuestionByHardQuiz = filterQuestionByHardQuiz(filterQuestionByIdQuiz, hardQuiz ?: notFoundInitTypeHardQuestion())
        var filterQuestionByLanguage = filterQuestionByMainLanguageUser(filterQuestionByHardQuiz)
        if (filterQuestionByIdQuiz.size < (numQuestions ?: notFoundNumberQuestionByTypeHardQuiz())) filterQuestionByLanguage =
            filterQuestionByOtherLanguageUser(filterQuestionByHardQuiz, languagesUser, numQuestions ?: notFoundNumberQuestionByTypeHardQuiz())
        if (filterQuestionByLanguage.isEmpty()) notFountQuestionByLanguageUser()
        else _questionList.value = filterQuestionByLanguage
    }

    fun getQuestionDetailByIdQuiz(idQuiz: Int) = viewModelScope.launch(Dispatchers.IO) {
        _questionDetailList.value = questionDetailUseCase.getQuestionDetailByIdQuiz(idQuiz)
    }

    fun saveQuestionDetail(questionDetailEntity: QuestionDetailEntity) =
        viewModelScope.launch(Dispatchers.IO) {
            questionDetailUseCase.saveQuestionDetail(questionDetailEntity)
        }

    fun updateQuestionDetail(questionDetailEntity: QuestionDetailEntity) =
        viewModelScope.launch(Dispatchers.IO) {
            questionDetailUseCase.updateQuestionDetail(questionDetailEntity)
        }

    fun pushStructureLocalData(ratingData: StructureLocalData) =
        viewModelScope.launch(Dispatchers.IO) {
            structureUseCase.pushStructureLocalData(ratingData)
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
        for (language in languages.split("|")) {
            val questionsForLanguage = questionList.filter { it.language == language }
            if (questionsForLanguage.size >= numQuestion) return questionsForLanguage
        }
        return emptyList()
    }

    fun initQuizValues() {
        numQuestions = if (hardQuiz ?: notFoundInitTypeHardQuestion()) quiz.value?.numHQ ?: notFoundNumberQuestionByTypeHardQuiz()
        else quiz.value?.numQ ?: notFoundNumberQuestionByTypeHardQuiz()

        event = quiz.value?.event
        starsAverageLocal = quiz.value?.starsAverageLocal
        starsMaxLocal = quiz.value?.starsMaxLocal
        starsMaxRemote = quiz.value?.starsMaxRemote
        ratingLocal = quiz.value?.ratingLocal
    }

    fun initQuestionValues() {
        _currentQuestion.value = 0
    }

    private fun calculateResultByCodeAnswer(codeAnswerThis: String) = codeAnswerThis.map {
            (((it.toInt() - '1'.toInt()) / 8.0) * 100).toInt() }.average().toInt()

    private fun calculateStarsMaxLocal() = questionDetailList.value?.maxOf {
            calculateResultByCodeAnswer(it.codeAnswer
                ?: deleteQuestionDetailById(it.id))} ?: 0

    private fun calculateStarsAverageLocal() = questionDetailList.value?.map {
        calculateResultByCodeAnswer(it.codeAnswer ?: deleteQuestionDetailById(it.id))
    }?.average()?.toInt() ?: 0

    fun result() {
        starsMaxLocal = calculateStarsMaxLocal()
        starsAverageLocal = calculateStarsAverageLocal()
        _result.value = calculateResultByCodeAnswer(codeAnswer)
    }

    //--------------------------------Exceptions------------------------
    fun notFountQuestionByLanguageUser() {

    }

    fun notFoundNumberQuestionByTypeHardQuiz(): Int {

        return 0
    }

    fun notFoundInitTypeHardQuestion(): Boolean {

        return false
    }
}