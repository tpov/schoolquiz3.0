package com.tpov.schoolquiz.presentation.create

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.presentation.model.PathStructure
import com.tpov.schoolquiz.presentation.create.model.CheckBoxUiState
import com.tpov.schoolquiz.presentation.create.model.ContainerUiState
import com.tpov.schoolquiz.presentation.create.model.CreateQuizUiModelState
import com.tpov.schoolquiz.presentation.create.model.ImageUiState
import com.tpov.schoolquiz.presentation.create.model.SpinnerUiState
import com.tpov.schoolquiz.presentation.create.model.TextUiState
import com.tpov.schoolquiz.presentation.create.model.TranslateAnswer
import com.tpov.schoolquiz.presentation.create.model.TranslateQuestion
import com.tpov.schoolquiz.presentation.create.strategy.CreateQuizRegimeStrategy
import com.tpov.schoolquiz.presentation.create.strategy.EditArchiveQuizRegimeStrategy
import com.tpov.schoolquiz.presentation.create.strategy.EditQuizRegimeStrategy
import com.tpov.schoolquiz.presentation.create.strategy.QuizRegimeStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Provider

class CreateQuizViewModel constructor(
    private val savedStateHandle: SavedStateHandle,
    private val structureUseCase: StructureUseCase,
    private val questionUseCase: QuestionUseCase
) : ViewModel() {
    companion object {
        const val REGIME_CREATE_QUIZ = 1
        const val REGIME_EDIT_QUIZ = 2
        const val REGIME_EDIT_ARCHIVE_QUIZ = 3
        const val MAX_ANSWER_OPTIONS_LIMIT = 4

        class Factory constructor(
            private val owner: AppCompatActivity,
            private val structureUseCaseProvider: Provider<StructureUseCase>,
            private val questionUseCaseProvider: Provider<QuestionUseCase>
        ) : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(CreateQuizViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return CreateQuizViewModel(
                        owner.defaultViewModelCreationExtras.createSavedStateHandle(),
                        structureUseCaseProvider.get(),
                        questionUseCaseProvider.get()
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    // StateFlows for individual UI elements based on CreateQuizUiModelState.kt
    private val _quizNameUiState = MutableStateFlow<TextUiState>(TextUiState.Hidden)
    val quizNameUiState: StateFlow<TextUiState> = _quizNameUiState

    private val _categorySpinnerUiState = MutableStateFlow<SpinnerUiState>(SpinnerUiState.Hidden)
    val categorySpinnerUiState: StateFlow<SpinnerUiState> = _categorySpinnerUiState

    private val _subCategorySpinnerUiState = MutableStateFlow<SpinnerUiState>(SpinnerUiState.Hidden)
    val subCategorySpinnerUiState: StateFlow<SpinnerUiState> = _subCategorySpinnerUiState

    private val _subsubCategorySpinnerUiState = MutableStateFlow<SpinnerUiState>(SpinnerUiState.Hidden)
    val subsubCategorySpinnerUiState: StateFlow<SpinnerUiState> = _subsubCategorySpinnerUiState

    private val _quizImageUiState = MutableStateFlow<ImageUiState>(ImageUiState.Hidden)
    val quizImageUiState: StateFlow<ImageUiState> = _quizImageUiState

    private val _questionImageUiState = MutableStateFlow<ImageUiState>(ImageUiState.Hidden)
    val questionImageUiState: StateFlow<ImageUiState> = _questionImageUiState

    private val _fullscreenButtonUiState = MutableStateFlow<CheckBoxUiState>(CheckBoxUiState.Hidden) // Corrected type
    val fullscreenButtonUiState: StateFlow<CheckBoxUiState> = _fullscreenButtonUiState // Corrected type

    private val _questionNumberSpinnerUiState = MutableStateFlow<SpinnerUiState>(SpinnerUiState.Hidden)
    val questionNumberSpinnerUiState: StateFlow<SpinnerUiState> = _questionNumberSpinnerUiState

    private val _addAnswerButtonUiState = MutableStateFlow<TextUiState>(TextUiState.Hidden)
    val addAnswerButtonUiState: StateFlow<TextUiState> = _addAnswerButtonUiState

    private val _saveQuizButtonUiState = MutableStateFlow<TextUiState>(TextUiState.Hidden)
    val saveQuizButtonUiState: StateFlow<TextUiState> = _saveQuizButtonUiState

    private val _addTranslateButtonUiState = MutableStateFlow<TextUiState>(TextUiState.Hidden)
    val addTranslateButtonUiState: StateFlow<TextUiState> = _addTranslateButtonUiState

    private val _beforeEditTranslateButtonUiState = MutableStateFlow<TextUiState>(TextUiState.Hidden) // Corrected name
    val beforeEditTranslateButtonUiState: StateFlow<TextUiState> = _beforeEditTranslateButtonUiState // Corrected name

    private val _afterEditTranslateButtonUiState = MutableStateFlow<TextUiState>(TextUiState.Hidden) // Corrected name
    val afterEditTranslateButtonUiState: StateFlow<TextUiState> = _afterEditTranslateButtonUiState // Corrected name

     private val _cancelButtonUiState = MutableStateFlow<TextUiState>(TextUiState.Hidden)
    val cancelButtonUiState: StateFlow<TextUiState> = _cancelButtonUiState

    private val _typeQuestionCheckBoxState = MutableStateFlow<CheckBoxUiState>(CheckBoxUiState.Hidden)
    val typeQuestionCheckBoxState: StateFlow<CheckBoxUiState> = _typeQuestionCheckBoxState

    private val _llCreateNewCategoryUiState = MutableStateFlow<ContainerUiState>(ContainerUiState.Hidden) // Added
    val llCreateNewCategoryUiState: StateFlow<ContainerUiState> = _llCreateNewCategoryUiState // Added

    private val _stroceTopUiState = MutableStateFlow<ContainerUiState>(ContainerUiState.Hidden) // Added
    val stroceTopUiState: StateFlow<ContainerUiState> = _stroceTopUiState // Added

     private val _stroceBottomUiState = MutableStateFlow<ContainerUiState>(ContainerUiState.Hidden) // Added
    val stroceBottomUiState: StateFlow<ContainerUiState> = _stroceBottomUiState // Added


    // StateFlow for the list of answers (data list, not UI state wrapper)
    private val _answerListState = MutableStateFlow<List<TranslateAnswer>>(emptyList())
    val answerListState: StateFlow<List<TranslateAnswer>> = _answerListState

    // StateFlow for the list of translations for the *current* question
    private val _currentQuestionTranslationsState = MutableStateFlow<List<TranslateQuestion>>(emptyList())
    val currentQuestionTranslationsState: StateFlow<List<TranslateQuestion>> = _currentQuestionTranslationsState

    // StateFlow for the list of *all* main questions in the quiz
    private val _allQuestionsState = MutableStateFlow<List<QuestionEntity>>(emptyList()) // Using QuestionEntity as the main question representation
    val allQuestionsState: StateFlow<List<QuestionEntity>> = _allQuestionsState

    // StateFlow for the index of the currently selected main question
    private val _currentQuestionIndexState = MutableStateFlow<Int>(0)
    val currentQuestionIndexState: StateFlow<Int> = _currentQuestionIndexState

    private lateinit var currentRegimeStrategy: QuizRegimeStrategy

    private var beforeQuestion: List<QuestionEntity> = listOf()
    private var afterQuestion: List<QuestionEntity> = listOf()

    init {
        val currentRegime = savedStateHandle.get<Int>("extra_regime") ?: -1
        val pathStructure = savedStateHandle.get<PathStructure?>("extra_path_structure")

        currentRegimeStrategy = when (currentRegime) {
            REGIME_CREATE_QUIZ -> CreateQuizRegimeStrategy(structureUseCase, questionUseCase)
            REGIME_EDIT_QUIZ -> EditQuizRegimeStrategy(structureUseCase, questionUseCase)
            REGIME_EDIT_ARCHIVE_QUIZ -> EditArchiveQuizRegimeStrategy(structureUseCase, questionUseCase)
            else -> CreateQuizRegimeStrategy(structureUseCase, questionUseCase)
        }

        val initialUiStateModel = currentRegimeStrategy.setupUiState()
        updateUiState(initialUiStateModel)

        viewModelScope.launch {
            try {
                val loadedUiStateModel = currentRegimeStrategy.loadData(pathStructure ?: PathStructure())
                beforeQuestion = loadedUiStateModel.questionList ?: listOf()
                updateUiState(loadedUiStateModel)

                val loadedAllQuestions: List<QuestionEntity> = emptyList()
                _allQuestionsState.value = loadedAllQuestions

                // Select the first question by default
                if (loadedAllQuestions.isNotEmpty()) {

                } else {
                    _currentQuestionIndexState.value = -1
                    _currentQuestionTranslationsState.value = emptyList()
                    _answerListState.value = emptyList()
                }

            } catch (e: Exception) {
                 _allQuestionsState.value = emptyList()
                 _currentQuestionIndexState.value = -1
                 _currentQuestionTranslationsState.value = emptyList()
                 _answerListState.value = emptyList()
            }
        }
    }

    // Private function to update all individual StateFlows based on the UI state model
    private fun updateUiState(model: CreateQuizUiModelState) {
        // Use ?.let to update only if the corresponding field in the model is not null
        model.quizNameUiState?.let { _quizNameUiState.value = it }
        model.categorySpinnerUiState?.let { _categorySpinnerUiState.value = it }
        model.subCategorySpinnerUiState?.let { _subCategorySpinnerUiState.value = it }
        model.subsubCategorySpinnerUiState?.let { _subsubCategorySpinnerUiState.value = it }
        model.quizImageUiState?.let { _quizImageUiState.value = it }
        model.questionImageUiState?.let { _questionImageUiState.value = it }
        model.fullscreenButtonUiState?.let { _fullscreenButtonUiState.value = it }
        model.saveQuizButtonUiState?.let { _saveQuizButtonUiState.value = it }

        // Update other individual StateFlows if present in CreateQuizUiModelState
        model.addAnswerButtonUiState?.let { _addAnswerButtonUiState.value = it }
        model.addTranslateButtonUiState?.let { _addTranslateButtonUiState.value = it }
        model.bBeforeEditTranslate?.let { _beforeEditTranslateButtonUiState.value = it }
        model.bAfterEditTranslate?.let { _afterEditTranslateButtonUiState.value = it }
        model.cancelButtonUiState?.let { _cancelButtonUiState.value = it }
        model.typeQuestionCheckBoxState?.let { _typeQuestionCheckBoxState.value = it }
        model.llCreateNewCategory?.let { _llCreateNewCategoryUiState.value = it }
        model.stroceTop?.let { _stroceTopUiState.value = it }
        model.stroceBottom?.let { _stroceBottomUiState.value = it }

        // Update spinner UI state based on questionListState size (will be handled by Activity observing allQuestionsState)
        // model.questionNumberSpinnerUiState?.let { _questionNumberSpinnerUiState.value = it }
    }

    // --- Functions for handling user actions (for the list of answers) ---

    // Function to add a new answer option to all languages of the *current* question
    fun addAnswerOption() {
        // Check if the answer options limit is reached
        val currentAnswers = _answerListState.value
        val currentAnswerOptionsCount = currentAnswers.firstOrNull()?.listAnswer?.size ?: 0

        if (currentAnswerOptionsCount < MAX_ANSWER_OPTIONS_LIMIT) {
            // Create a new list of TranslateAnswer with an added empty option
            val updatedAnswers = currentAnswers.map { translateAnswer ->
                // Create a new MutableList, add an empty string
                val updatedListAnswer = translateAnswer.listAnswer.toMutableList()
                updatedListAnswer.add("")
                // Create a new copy of TranslateAnswer with the updated list
                translateAnswer.copy(listAnswer = updatedListAnswer)
            }
            // Update the StateFlow of the answers list for the current question
            _answerListState.value = updatedAnswers
            // TODO: Reflect this change back in the _allQuestionsState for the current question
        }
    }

    // Function to update the list of answer options for a specific TranslateAnswer object of the *current* question
    fun onAnswerOptionsChanged(updatedTranslateAnswer: TranslateAnswer) {
        val currentAnswers = _answerListState.value.toMutableList()
        val index = currentAnswers.indexOfFirst { it.language == updatedTranslateAnswer.language }
        if (index != -1) {
            currentAnswers[index] = updatedTranslateAnswer
            _answerListState.value = currentAnswers.toList() // Update the StateFlow with the modified list
             // TODO: Reflect this change back in the _allQuestionsState for the current question
        }
    }

    fun onQuestionTextChanged(updatedTranslateQuestion: TranslateQuestion) {
        val currentTranslations = _currentQuestionTranslationsState.value.toMutableList()
        val index = currentTranslations.indexOfFirst { it.language == updatedTranslateQuestion.language }
        if (index != -1) {
            currentTranslations[index] = updatedTranslateQuestion
            _currentQuestionTranslationsState.value = currentTranslations.toList() // Update the StateFlow with the modified list
        }
    }

    // Function to add a new translation (language) for the current question and answers
    fun addTranslate() {
        // TODO: Determine the new language.
        val newLanguage = "New Language" // Placeholder

        val currentQuestionTranslations = _currentQuestionTranslationsState.value.toMutableList()
        val currentAnswers = _answerListState.value.toMutableList()

        // Check if language already exists in either list
        val languageExists = currentQuestionTranslations.any { it.language == newLanguage } ||
                             currentAnswers.any { it.language == newLanguage }

        if (!languageExists) {
            currentQuestionTranslations.add(TranslateQuestion(language = newLanguage, question = ""))
            _currentQuestionTranslationsState.value = currentQuestionTranslations.toList()

            val numberOfAnswerOptions = currentAnswers.firstOrNull()?.listAnswer?.size ?: 0
            val emptyAnswerOptions = MutableList(numberOfAnswerOptions) { "" }
            currentAnswers.add(TranslateAnswer(language = newLanguage, listAnswer = emptyAnswerOptions))
            _answerListState.value = currentAnswers.toList()

        }
    }

    fun onQuestionTranslationsChanged(updatedTranslateQuestion: TranslateQuestion) {
        val currentTranslations = _currentQuestionTranslationsState.value.toMutableList()
        val index = currentTranslations.indexOfFirst { it.language == updatedTranslateQuestion.language }
        if (index != -1) {
            currentTranslations[index] = updatedTranslateQuestion
            _currentQuestionTranslationsState.value = currentTranslations.toList() // Update the StateFlow with the modified list
        }
    }


    fun saveDataForCurrentRegime() {
        viewModelScope.launch {

        }
    }

}
