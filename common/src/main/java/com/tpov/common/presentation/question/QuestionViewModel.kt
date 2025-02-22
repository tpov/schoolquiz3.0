package com.tpov.common.presentation.question

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.tpov.common.CODE_EMPTY_ANSWER
import com.tpov.common.CODE_MAX_SCORE_ANSWER
import com.tpov.common.CODE_MIN_SCORE_ANSWER
import com.tpov.common.COUNT_VARIATION_CODE_ANSWER
import com.tpov.common.Interactor
import com.tpov.common.LVL_GOOGLE_TRANSLATOR
import com.tpov.common.MAX_DROP_ANSWER
import com.tpov.common.SPLIT_BETWEEN_ANSWERS
import com.tpov.common.SPLIT_BETWEEN_LANGUAGES
import com.tpov.common.UNKNOWN_VALUE
import com.tpov.common.data.model.local.QuestionDetailEntity
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.domain.usecase.QuestionDetailUseCase
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.domain.utils.StructureDataUtils.findChildren
import com.tpov.common.presentation.PresentationExceptions
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.utils.LanguageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@InternalCoroutinesApi
class QuestionViewModel @Inject constructor(
    var app: Application,
    var questionUseCase: QuestionUseCase,
    var questionDetailUseCase: QuestionDetailUseCase,
    val structureUseCase: StructureUseCase,
    val interactor: Interactor
) : AndroidViewModel(app) {

    var newAnswerOrder: Int = 0
    var originalAnswerOrder: String = ""
    var numQuestions: Int? = null
    var hardQuiz: Boolean? = null
    var pathStructure: PathStructure? =
        PathStructure(UNKNOWN_VALUE, UNKNOWN_VALUE, UNKNOWN_VALUE, UNKNOWN_VALUE, UNKNOWN_VALUE)
    var life: Int? = null

    var oldCurrentQuestion = 0
    var codeAnswer = ""

    val unknownCurrentQuestion = -1
    private val languageIdentifier = LanguageIdentification.getClient()
    private var translator: Translator? = null

    val errorHandler = PresentationExceptions(
        beforeException = {
            _toastMessage.value = it
        },
        afterException = {
            _closeActivity.value = true
        },
        interactor
    )

    private val _translateState = MutableStateFlow<TranslateState>(TranslateState.Initial)
    val translateState = _translateState.asStateFlow()

    val result: StateFlow<Int?> get() = _result
    private val _result = MutableStateFlow<Int?>(unknownCurrentQuestion)
    val currentQuestion: StateFlow<QuestionEntity?> get() = _currentQuestion
    private val _currentQuestion = MutableStateFlow<QuestionEntity?>(null)

    val quiz: StateFlow<StructureDataLocal?> get() = _quiz
    private val _quiz = MutableStateFlow<StructureDataLocal?>(null)
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

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage

    private val _showTranslateDialog = MutableStateFlow<Boolean?>(null)
    val showTranslateDialog: StateFlow<Boolean?> = _showTranslateDialog

    //--------------------------------------------USE CASES---------------------------------------------

    fun saveQuizResult() = viewModelScope.launch(Dispatchers.IO) {

    }

    fun getQuestionList(lng: String) = viewModelScope.launch(Dispatchers.IO) {
        Log.d("jfgksdjefkse", "lng: $lng")
        Log.d("jfgksdjefkse", "pathStructure: $pathStructure")
        val languagesUser = if (lng == "") Locale.getDefault().language
        else lng
        val filterQuestionByIdQuiz =
            questionUseCase.getQuestionByPath(pathStructure ?: errorHandler.notFoundInputData())
        Log.d("jfgksdjefkse", "filterQuestionByIdQuiz: $filterQuestionByIdQuiz")
        val filterQuestionByHardQuiz = filterQuestionByHardQuiz(
            filterQuestionByIdQuiz, hardQuiz ?: errorHandler.notFoundInitTypeHardQuestion()
        )
        Log.d("jfgksdjefkse", "filterQuestionByHardQuiz: $filterQuestionByIdQuiz")
        var filterQuestionByLanguage =
            filterQuestionByMainLanguageUser(filterQuestionByHardQuiz, languagesUser)
        Log.d("jfgksdjefkse", "filterQuestionByLanguage: $filterQuestionByLanguage")
        if (filterQuestionByLanguage.size < (numQuestions
                ?: errorHandler.notFoundNumberQuestionByTypeHardQuiz())
        ) filterQuestionByLanguage = filterQuestionByOtherLanguageUser(
            filterQuestionByHardQuiz,
            languagesUser,
            numQuestions ?: errorHandler.notFoundNumberQuestionByTypeHardQuiz()
        )
        if (filterQuestionByLanguage.isEmpty()) _showTranslateDialog.value = true
        else _questionList.value = filterQuestionByLanguage.sortedBy { it.numQuestion }

        Log.d("jfgksdjefkse", "_questionList.value: ${_questionList.value}")
    }

    fun getQuestionDetailByPath() = viewModelScope.launch(Dispatchers.IO) {
        _questionDetailList.value = questionDetailUseCase.getQuestionDetailByPath(
            pathStructure ?: errorHandler.notFoundInputData()
        )?.filter { it.hardQuiz == hardQuiz } ?: listOf(
            QuestionDetailEntity(
                0,
                pathStructure?.idEvent ?: errorHandler.notFoundInputData(),
                pathStructure?.idCategory ?: errorHandler.notFoundInputData(),
                pathStructure?.idSubCategory ?: errorHandler.notFoundInputData(),
                pathStructure?.idSubsubCategory ?: errorHandler.notFoundInputData(),
                pathStructure?.idEvent ?: errorHandler.notFoundInputData(),
                getDataToday(),
                CODE_EMPTY_ANSWER.toString()
                    .repeat(numQuestions ?: errorHandler.notFoundQuizValue()),
                hardQuiz ?: errorHandler.notFoundInitTypeHardQuestion(),
                false
            )
        )
    }

    fun saveQuestionDetail() = viewModelScope.launch(Dispatchers.IO) {
        questionDetailUseCase.saveQuestionDetail(
            QuestionDetailEntity(
                0,
                pathStructure?.idEvent ?: errorHandler.notFoundInputData(),
                pathStructure?.idCategory ?: errorHandler.notFoundInputData(),
                pathStructure?.idSubCategory ?: errorHandler.notFoundInputData(),
                pathStructure?.idSubsubCategory ?: errorHandler.notFoundInputData(),
                pathStructure?.idEvent ?: errorHandler.notFoundInputData(),
                getDataToday(),
                codeAnswer,
                hardQuiz ?: errorHandler.notFoundQuizValue(),
                false
            )
        )
    }


    private fun getDataToday() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun updateQuestionDetail(questionDetailEntity: QuestionDetailEntity) =
        viewModelScope.launch(Dispatchers.IO) {
            questionDetailUseCase.updateQuestionDetail(questionDetailEntity)
        }


    fun deleteQuestionDetailById(id: Int?): String {
        viewModelScope.launch(Dispatchers.IO) {
            //questionDetailUseCase.deleteQuestionDetailById(id)
        }
        return ""
    }

    //--------------------------------------------OTHER FUN---------------------------------

    fun initQuestionValues() {
        _currentQuestion.value = questionList.value?.get(0)
    }

    fun initQuizValue() = viewModelScope.launch(Dispatchers.IO) {
        pathStructure?.apply {
            _quiz.value = structureUseCase.getStructureCategoryList(idEvent)
                .find { it.id == idCategory }
                ?.findChildren(idSubCategory)
                ?.findChildren(idSubsubCategory)
                ?.findChildren(idQuiz) ?: errorHandler.notFoundPath()
        } ?: errorHandler.notFoundPath()
    }

    private fun filterQuestionByHardQuiz(
        questionEntityList: List<QuestionEntity>, hardQuiz: Boolean
    ) = questionEntityList.filter { it.hardQuestion == hardQuiz }

    private fun filterQuestionByMainLanguageUser(
        questionList: List<QuestionEntity>, languagesUser: String
    ) = questionList.filter { it.language == languagesUser }

    private fun filterQuestionByOtherLanguageUser(
        questionList: List<QuestionEntity>, languages: String, numQuestion: Int
    ): List<QuestionEntity> {
        for (language in languages.split(SPLIT_BETWEEN_LANGUAGES)) {
            val questionsForLanguage = questionList.filter { it.language == language }
            if (questionsForLanguage.size >= numQuestion) return questionsForLanguage
        }
        return emptyList()
    }

    fun initQuizValues() {
        numQuestions = if (hardQuiz == false) _quiz.value?.numQ else _quiz.value?.numHQ

    }

    fun initQuestionDetail() {
        _questionDetail.value = questionDetailList.value?.find { questionDetail ->
            questionDetail.codeAnswer?.any { it == '0' } ?: false
        } ?: QuestionDetailEntity(
            0,
            pathStructure?.idEvent ?: errorHandler.notFoundInputData(),
            pathStructure?.idCategory ?: errorHandler.notFoundInputData(),
            pathStructure?.idSubCategory ?: errorHandler.notFoundInputData(),
            pathStructure?.idSubsubCategory ?: errorHandler.notFoundInputData(),
            pathStructure?.idEvent ?: errorHandler.notFoundInputData(),
            getDataToday(),
            "0".repeat(numQuestions ?: errorHandler.notFoundQuizValue()),
            hardQuiz ?: errorHandler.notFoundInitTypeHardQuestion(),
            false
        )
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
        _currentQuestion.value = questionList.value?.get(current)
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

    fun translateANDAddQuestion(question: QuestionEntity, toLang: String) {
        Log.d("wadasdaw", "translateANDAddQuestion: $toLang")
        viewModelScope.launch(Dispatchers.IO) {
            val newQuestion = question
            var answers = ""
            question.nameAnswers.split(SPLIT_BETWEEN_ANSWERS).forEach { answer ->
                answers += "${
                    LanguageUtils.getLanguageShortCode(
                        translateText(answer, question.language, toLang)
                    )
                }$SPLIT_BETWEEN_ANSWERS"
            }
            newQuestion.nameQuestion =
                translateText(question.nameQuestion, question.language, toLang)
            newQuestion.nameAnswers = answers
            newQuestion.language = toLang
            newQuestion.lvlTranslate = LVL_GOOGLE_TRANSLATOR
            questionUseCase.insertQuestion(newQuestion)
        }
    }

    private suspend fun translateText(text: String, fromLang: String, toLang: String): String {
        Log.d("TranslateDebug", "Starting translation from $fromLang to $toLang: '$text'")

        return try {
            withContext(Dispatchers.IO) {
                val languagePair = "${fromLang}_$toLang"
                Log.d("TranslateDebug", "Language pair: $languagePair")

                val options = TranslatorOptions.Builder().setSourceLanguage(fromLang)
                    .setTargetLanguage(toLang).build()

                translator = Translation.getClient(options)

                try {
                    translator?.downloadModelIfNeeded()?.await()
                    val result = translator?.translate(text)?.await() ?: text
                    result
                } catch (e: Exception) {
                    errorHandler.errorTranslate<String>()
                    Log.e("TranslateError", "Translation error: ${e.message}")
                    text
                }
            }
        } catch (e: Exception) {
            Log.e("TranslateError", "General error", e)
            errorHandler.errorTranslate<String>()
            text
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