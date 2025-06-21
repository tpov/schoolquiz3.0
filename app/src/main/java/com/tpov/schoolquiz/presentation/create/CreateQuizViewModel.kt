package com.tpov.schoolquiz.presentation.create

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.utils.LanguageUtils
import com.tpov.schoolquiz.presentation.create.manager.CategoryStateManager
import com.tpov.schoolquiz.presentation.create.manager.QuestionStateManager
import com.tpov.schoolquiz.presentation.create.manager.UiElementsStateManager
import com.tpov.schoolquiz.presentation.create.model.QuizUiModelState
import com.tpov.schoolquiz.presentation.create.model.TranslateAnswer
import com.tpov.schoolquiz.presentation.create.model.TranslateQuestion
import com.tpov.schoolquiz.presentation.create.model.getNumQuestion
import com.tpov.schoolquiz.presentation.create.model.getType
import com.tpov.schoolquiz.presentation.create.strategy.CreateQuizRegimeStrategy
import com.tpov.schoolquiz.presentation.create.strategy.EditArchiveQuizRegimeStrategy
import com.tpov.schoolquiz.presentation.create.strategy.EditQuizRegimeStrategy
import com.tpov.schoolquiz.presentation.create.strategy.QuizRegimeStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class CreateQuizViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val structureUseCase: StructureUseCase,
    private val questionUseCase: QuestionUseCase
) : ViewModel() {
    companion object {
        const val REGIME_CREATE_QUIZ = 1
        const val REGIME_EDIT_QUIZ = 2
        const val REGIME_EDIT_ARCHIVE_QUIZ = 3
        const val MAX_ANSWER_OPTIONS_LIMIT = 4
    }

    private val _uiState = MutableStateFlow(QuizUiModelState())
    val uiState: StateFlow<QuizUiModelState> = _uiState.asStateFlow()

    private val questionStateManager = QuestionStateManager(_uiState)
    private val categoryStateManager = CategoryStateManager(_uiState)
    private val uiElementsManager = UiElementsStateManager(_uiState)

    private lateinit var currentRegimeStrategy: QuizRegimeStrategy


    init {
        val currentRegime = savedStateHandle.get<Int>("extra_regime") ?: REGIME_CREATE_QUIZ
        val pathStructure = savedStateHandle.get<PathStructure?>("extra_path_structure")

        currentRegimeStrategy = when (currentRegime) {
            REGIME_CREATE_QUIZ -> {
                CreateQuizRegimeStrategy(structureUseCase, questionUseCase, questionStateManager)
            }
            REGIME_EDIT_QUIZ -> EditQuizRegimeStrategy(structureUseCase, questionUseCase)
            REGIME_EDIT_ARCHIVE_QUIZ -> EditArchiveQuizRegimeStrategy(structureUseCase, questionUseCase)
            else -> CreateQuizRegimeStrategy(structureUseCase, questionUseCase, questionStateManager)
        }

        _uiState.value = currentRegimeStrategy.setupUiState()

        viewModelScope.launch {
            when (currentRegime) {
                REGIME_CREATE_QUIZ -> {
                    _uiState.value  = currentRegimeStrategy.loadData(pathStructure ?: PathStructure())
                    createNewQuestion()
                }
                REGIME_EDIT_QUIZ -> {

                }
                REGIME_EDIT_ARCHIVE_QUIZ -> {

                }
                else -> {
                }

            }
        }
    }

    fun List<QuestionLocal>.updateQuestion(
        questionNumber: Int,
        language: LanguageUtils,
        isHard: Boolean,
        update: (QuestionLocal) -> QuestionLocal
    ): List<QuestionLocal> {
        return map { question ->
            if (question.numQuestion == questionNumber &&
                question.language == language &&
                question.hardQuestion == isHard) {
                update(question)
            } else question
        }
    }


    fun selectCategory(names: Triple<String, String,String>) = categoryStateManager.selectCategory(names)
    fun toggleNewCategoryFields() = categoryStateManager.toggleNewCategoryFields()

    fun addAnswerOption() = questionStateManager.addAnswerOption()
    fun onAnswerOptionsChanged(updatedTranslateAnswer: TranslateAnswer) =
        questionStateManager.updateAnswerOptions(updatedTranslateAnswer)
    fun onQuestionTextChanged(updatedTranslateQuestion: TranslateQuestion) =
        questionStateManager.updateQuestionText(updatedTranslateQuestion.question, updatedTranslateQuestion.language)
    fun addTranslate() = questionStateManager.addTranslation()
    fun createNewQuestion() {
        val (newNumberQuestion, hardQuestion) = questionStateManager.createNewQuestion()
        Log.d("awdawdasdf", "newNumberQuestion: $newNumberQuestion, hardQuestion: $hardQuestion")
        selectQuestion(newNumberQuestion, hardQuestion)
    }

    fun updateCheckBox() {
        questionStateManager.toggleQuestionType()
        questionStateManager.selectQuestion()
    }

    fun onQuestionLanguageChanged(oldLanguage: LanguageUtils, newLanguage: LanguageUtils) {
        questionStateManager.updateQuestionLanguage(oldLanguage, newLanguage)

    }

    fun selectQuestion(questionNumber: Int? = null, isHard: Boolean? = null) {
        questionStateManager.selectQuestion(questionNumber, isHard)
    }

    fun onQuestionTranslationsChanged(updatedTranslateQuestion: TranslateQuestion) {
        questionStateManager.updateQuestionText(updatedTranslateQuestion.question, updatedTranslateQuestion.language)
    }

    fun setPhotoQuestion(bitmapDrawable: BitmapDrawable) {
        uiElementsManager.setPhotoQuestion(
            bitmapDrawable,
            _uiState.value.questionNumberSpinnerUiState?.getNumQuestion() ?: 0,
            _uiState.value.typeQuestionCheckBoxState?.getType() ?: false
        )
    }

    fun saveDataForCurrentRegime(
        structureDataLocal: List<String>,
        structureDataImageList: List<BitmapDrawable>,
        defaultImage: Drawable
    ) {
        val combined: List<Pair<String, BitmapDrawable>> =
            structureDataLocal.zip(structureDataImageList) { name, image ->
                name to image
            }

        viewModelScope.launch {
            currentRegimeStrategy.saveData(
                _uiState.value.questionList?.toList() ?: emptyList(),
                _uiState.value.questionDrawable,
                combined,
                defaultImage
            )
        }
    }
}
