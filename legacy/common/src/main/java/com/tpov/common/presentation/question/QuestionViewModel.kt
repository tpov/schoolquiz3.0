package com.tpov.common.presentation.question

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.Translator
import com.tpov.common.CODE_EMPTY_ANSWER
import com.tpov.common.CODE_MAX_SCORE_ANSWER
import com.tpov.common.CODE_MIN_SCORE_ANSWER
import com.tpov.common.COUNT_VARIATION_CODE_ANSWER
import com.tpov.common.ExceptionInteractor
import com.tpov.common.LVL_GOOGLE_TRANSLATOR
import com.tpov.common.MAX_DROP_ANSWER
import com.tpov.common.SPLIT_BETWEEN_ANSWERS
import com.tpov.common.data.model.local.QuestionDetailLocal
import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.common.domain.model.EventQuiz.Companion.fromInput
import com.tpov.common.domain.usecase.QuestionDetailUseCase
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.domain.utils.QuestionUtils.filterByHardQuiz
import com.tpov.common.domain.utils.QuestionUtils.filterByLanguage
import com.tpov.common.domain.utils.QuestionUtils.getResultFilter
import com.tpov.common.domain.utils.StructureDataUtils.findChildren
import com.tpov.common.errorOnNull
import com.tpov.common.presentation.QuestionScreenExceptions
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.utils.LanguageUtils
import com.tpov.common.presentation.utils.translateText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@InternalCoroutinesApi
class QuestionViewModel @Inject constructor(
    var app: Application,
    var questionUseCase: QuestionUseCase,
    var questionDetailUseCase: QuestionDetailUseCase,
    var structureUseCase: StructureUseCase,
    val interactor: ExceptionInteractor
) : AndroidViewModel(app) {

    val errorHandler = QuestionScreenExceptions(
        beforeException = {
            _toastMessage.value = it
        },
        afterException = {
            _closeActivity.value = true
        },
        interactor
    )

    var newAnswerOrder: Int = 0
    var originalAnswerOrder: String = ""

    var numQuestions: Int by errorOnNull { errorHandler.notFoundNumberQuestionByTypeHardQuiz() }
    var hardQuiz: Boolean by errorOnNull { errorHandler.notFoundInitTypeHardQuestion() }
    var pathStructure: PathStructure by errorOnNull { errorHandler.notFoundPath() }

    var life: Int? = null

    var oldCurrentQuestion = 0
    var codeAnswer = ""

    val unknownCurrentQuestion = -1
    private val languageIdentifier = LanguageIdentification.getClient()
    private var translator: Translator? = null


    private val _translateState = MutableStateFlow<TranslateState>(TranslateState.Initial)
    val translateState = _translateState.asStateFlow()

    val result: StateFlow<Int?> get() = _result
    private val _result = MutableStateFlow<Int?>(unknownCurrentQuestion)
    val currentQuestion: StateFlow<QuestionLocal?> get() = _currentQuestion
    private val _currentQuestion = MutableStateFlow<QuestionLocal?>(null)

    val quiz: StateFlow<StructureDataLocal?> get() = _quiz
    private val _quiz = MutableStateFlow<StructureDataLocal?>(null)
    val questionList: StateFlow<List<QuestionLocal>?> get() = _questionList
    private val _questionList = MutableStateFlow<List<QuestionLocal>?>(null)
    val questionDetailList: StateFlow<List<QuestionDetailLocal>?> get() = _questionDetailList
    private val _questionDetailList = MutableStateFlow<List<QuestionDetailLocal>?>(null)

    val questionDetail: StateFlow<QuestionDetailLocal?> get() = _questionDetail
    private val _questionDetail = MutableStateFlow<QuestionDetailLocal?>(null)

    val springAnim: StateFlow<Boolean?> get() = _springAnim
    private val _springAnim = MutableStateFlow<Boolean?>(null)
    val closeActivity: StateFlow<Boolean> get() = _closeActivity
    private val _closeActivity = MutableStateFlow(false)

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage

    private val _showTranslateDialog = MutableStateFlow<Boolean?>(null)
    val showTranslateDialog: StateFlow<Boolean?> = _showTranslateDialog


    fun saveQuizResult() = viewModelScope.launch(Dispatchers.IO) {

    }

    fun getQuestionList(lng: List<LanguageUtils>) = viewModelScope.launch(Dispatchers.IO) {
        val result = questionUseCase.getQuestionByPath(pathStructure)
            .filterByHardQuiz(hardQuiz)
            .filterByLanguage(lng, numQuestions)
            .getResultFilter()

        when (result) {
            is QuestionListResult.Success -> {
                _questionList.value = result.questions
                _showTranslateDialog.value = false
            }

            is QuestionListResult.EmptyTranslation -> {
                _questionList.value = emptyList()
                _showTranslateDialog.value = true
            }

            is QuestionListResult.Error -> {
                _questionList.value = emptyList()
                _showTranslateDialog.value = false
            }
        }
    }

    fun getQuestionDetailByPath() = viewModelScope.launch(Dispatchers.IO) {
        _questionDetailList.value = questionDetailUseCase.getQuestionDetailByPath(
            pathStructure)?.filter { it.hardQuiz == hardQuiz }
    }

    fun saveQuestionDetail() = viewModelScope.launch(Dispatchers.IO) {
        questionDetailUseCase.saveQuestionDetail(
            QuestionDetailLocal().create(pathStructure!!, numQuestions)
        )
    }


    fun updateQuestionDetail(questionDetailLocal: QuestionDetailLocal) =
        viewModelScope.launch(Dispatchers.IO) {
            questionDetailUseCase.updateQuestionDetail(questionDetailLocal)
        }


    fun deleteQuestionDetailById(id: Int?): String {
        viewModelScope.launch(Dispatchers.IO) {
            questionDetailUseCase.deleteQuestionDetailById(id!!)
        }
        return ""
    }

    fun initQuestionValues() {
        _currentQuestion.value = questionList.value?.get(0)
    }

    fun initQuizValue() = viewModelScope.launch(Dispatchers.IO) {
        pathStructure.apply {
            _quiz.value = structureUseCase.getStructureEventData(
                fromInput(nameEvent)
                    ?: errorHandler.notFoundEventQuiz(nameEvent)
            )?.find { it.nameItem == nameCategory }
                ?.findChildren(nameSubCategory)
                ?.findChildren(nameSubsubCategory)
                ?.findChildren(nameQuiz) ?: errorHandler.notFoundPath()
        }
    }

    fun initQuizValues() {
        numQuestions = if (!hardQuiz) _quiz.value?.numQ ?: errorHandler.notFoundNumberQuestionByTypeHardQuiz()
        else _quiz.value?.numHQ ?: errorHandler.notFoundNumberQuestionByTypeHardQuiz()

    }

    fun initQuestionDetail() {
        _questionDetail.value = questionDetailList.value?.find { questionDetail ->
            questionDetail.codeAnswer?.any { it == CODE_EMPTY_ANSWER } ?: false
        } ?: QuestionDetailLocal().create(pathStructure, numQuestions)
    }

    fun calculateResultByCodeAnswer(codeAnswerThis: String) = codeAnswerThis.map {
        (((it.toInt() - CODE_MIN_SCORE_ANSWER.toInt()) / COUNT_VARIATION_CODE_ANSWER) * 100)
    }.average().toInt()

    fun calculatePercentByCodeAnswer(): Int {
        return codeAnswer.filter { it != CODE_EMPTY_ANSWER }.map {
            (((it.toInt() - CODE_MIN_SCORE_ANSWER.toInt()).toDouble() / COUNT_VARIATION_CODE_ANSWER) * 100)
        }.average().toInt()
    }


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
        _currentQuestion.value = questionList.value?.get(current - 1)
    }

    fun saveResult() {

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

            val originalOrder =
                originalAnswerOrder.toString().take(MAX_DROP_ANSWER).map { it.toString().toInt() }
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
        if (currentQuestion.value?.numQuestion!! >= numQuestions!!) _springAnim.value = true
        else setNewCurrentQuestion(currentQuestion.value?.numQuestion!! + 1)
    }

    private fun setPrefQuestion() {
        if (currentQuestion.value?.numQuestion!!.plus(1)!! <= 1) _springAnim.value = false
        else setNewCurrentQuestion(currentQuestion.value?.numQuestion!!.minus(1)!!)
    }

    fun setCodeInCodeAnswer(score: Int) {
        val index = currentQuestion.value?.numQuestion ?: errorHandler.errorGetNumQuestion()

        codeAnswer = if (index < codeAnswer.length) {
            codeAnswer.substring(0, index) + score.toString() + codeAnswer.substring(index + 1)
        } else codeAnswer.padEnd(index, CODE_EMPTY_ANSWER) + score.toString()
        if (!codeAnswer.contains(CODE_EMPTY_ANSWER)) saveResult()
    }

    fun translateANDAddQuestion(question: QuestionLocal, toLang: LanguageUtils) {
        Log.d("wadasdaw", "translateANDAddQuestion: $toLang")
        viewModelScope.launch(Dispatchers.IO) {
            val newQuestion = question
            var answers = ""
            question.nameAnswers.split(SPLIT_BETWEEN_ANSWERS).forEach { answer ->
                answers += "${
                    translateText(answer, question.language, toLang)
                }$SPLIT_BETWEEN_ANSWERS"
            }
            newQuestion.nameQuestion = translateText(question.nameQuestion, question.language, toLang)!!
            newQuestion.nameAnswers = answers
            newQuestion.language = toLang
            newQuestion.lvlTranslate = LVL_GOOGLE_TRANSLATOR
            questionUseCase.insertQuestion(newQuestion)
        }
    }

    fun countNonEmptyAnswers(): Int {
        return codeAnswer.count { it != '0' }
    }

    override fun onCleared() {
        super.onCleared()
        translator?.close()
    }
}
