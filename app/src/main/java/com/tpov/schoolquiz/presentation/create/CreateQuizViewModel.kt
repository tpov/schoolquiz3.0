package com.tpov.schoolquiz.presentation.create

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tpov.common.SPLIT_BETWEEN_ANSWERS
import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.SettingConfigObject.settingsConfig
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.domain.utils.QuestionUtils
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.utils.LanguageUtils
import com.tpov.schoolquiz.presentation.create.model.CheckBoxUiState
import com.tpov.schoolquiz.presentation.create.model.ImageUiState
import com.tpov.schoolquiz.presentation.create.model.QuizUiModelState
import com.tpov.schoolquiz.presentation.create.model.SpinnerUiState
import com.tpov.schoolquiz.presentation.create.model.TextUiState
import com.tpov.schoolquiz.presentation.create.model.TranslateAnswer
import com.tpov.schoolquiz.presentation.create.model.TranslateQuestion
import com.tpov.schoolquiz.presentation.create.model.getType
import com.tpov.schoolquiz.presentation.create.model.isUiState
import com.tpov.schoolquiz.presentation.create.strategy.CreateQuizRegimeStrategy
import com.tpov.schoolquiz.presentation.create.strategy.EditArchiveQuizRegimeStrategy
import com.tpov.schoolquiz.presentation.create.strategy.EditQuizRegimeStrategy
import com.tpov.schoolquiz.presentation.create.strategy.QuizRegimeStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

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

    private val _llCreateNewCategoryUiState = MutableStateFlow<isUiState>(isUiState.Hidden) // Added
    val llCreateNewCategoryUiState: StateFlow<isUiState> = _llCreateNewCategoryUiState // Added

    private val _stroceTopUiState = MutableStateFlow<isUiState>(isUiState.Hidden) // Added
    val stroceTopUiState: StateFlow<isUiState> = _stroceTopUiState // Added

    private val _stroceBottomUiState = MutableStateFlow<isUiState>(isUiState.Hidden) // Added
    val stroceBottomUiState: StateFlow<isUiState> = _stroceBottomUiState // Added


    // StateFlow for the list of answers (data list, not UI state wrapper)
    private val _answerListState = MutableStateFlow<List<TranslateAnswer>>(emptyList())
    val answerListState: StateFlow<List<TranslateAnswer>> = _answerListState

    // StateFlow for the list of translations for the *current* question
    private val _currentQuestionTranslationsState = MutableStateFlow<List<TranslateQuestion>>(emptyList())
    val currentQuestionTranslationsState: StateFlow<List<TranslateQuestion>> = _currentQuestionTranslationsState

    private val _questionList = MutableStateFlow<List<QuestionLocal>>(emptyList())
    val questionList: StateFlow<List<QuestionLocal>> = _questionList

    // StateFlow for the index of the currently selected main question
    private val _currentQuestionNumber = MutableStateFlow<Int>(1)
    val currentQuestionNumber: StateFlow<Int> = _currentQuestionNumber

    private lateinit var currentRegimeStrategy: QuizRegimeStrategy

    private var beforeQuestion: List<QuestionLocal> = listOf()
    private var afterQuestion: List<QuestionLocal> = listOf()

    // Модель для создания новой категории
    data class NewCategoryData(
        val categoryName: String = "",
        val categoryImage: String? = null,
        val subCategoryName: String = "",
        val subCategoryImage: String? = null,
        val subSubCategoryName: String = "",
        val subSubCategoryImage: String? = null
    )

    val pictureQuestionMap = mutableMapOf<Pair<Int, Boolean>, BitmapDrawable>()

    fun setPhotoQuestion(bitmapDrawable: BitmapDrawable) {
        pictureQuestionMap[Pair(currentQuestionNumber.value, typeQuestionCheckBoxState.value.getType())] = bitmapDrawable
    }

    // StateFlow для хранения данных о новых категориях
    private val _newCategoryData = MutableStateFlow(NewCategoryData())
    val newCategoryData: StateFlow<NewCategoryData> = _newCategoryData

    // StateFlow для управления видимостью раздела создания новых категорий
    private val _showNewCategoryFields = MutableStateFlow(false)
    val showNewCategoryFields: StateFlow<Boolean> = _showNewCategoryFields

    // Метод для переключения видимости раздела создания новых категорий
    fun toggleNewCategoryFields() {
        _showNewCategoryFields.value = !_showNewCategoryFields.value
    }

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
            val loadedUiStateModel = currentRegimeStrategy.loadData(pathStructure ?: PathStructure())
            beforeQuestion = loadedUiStateModel.questionList ?: listOf()
            updateUiState(loadedUiStateModel)
        }
    }

    private fun updateUiState(model: QuizUiModelState) {
        model.quizNameUiState?.let { _quizNameUiState.value = it }
        model.categorySpinnerUiState?.let { _categorySpinnerUiState.value = it }
        model.subCategorySpinnerUiState?.let { _subCategorySpinnerUiState.value = it }
        model.subsubCategorySpinnerUiState?.let { _subsubCategorySpinnerUiState.value = it }
        model.quizImageUiState?.let { _quizImageUiState.value = it }
        model.questionImageUiState?.let { _questionImageUiState.value = it }
        model.fullscreenButtonUiState?.let { _fullscreenButtonUiState.value = it }
        model.questionNumberSpinnerUiState?.let { _questionNumberSpinnerUiState.value = it }
        model.saveQuizButtonUiState?.let { _saveQuizButtonUiState.value = it }

        model.addAnswerButtonUiState?.let { _addAnswerButtonUiState.value = it }
        model.addTranslateButtonUiState?.let { _addTranslateButtonUiState.value = it }
        model.bBeforeEditTranslate?.let { _beforeEditTranslateButtonUiState.value = it }
        model.bAfterEditTranslate?.let { _afterEditTranslateButtonUiState.value = it }
        model.cancelButtonUiState?.let { _cancelButtonUiState.value = it }
        model.llCreateNewCategory?.let { _llCreateNewCategoryUiState.value = it }
        model.stroceTop?.let { _stroceTopUiState.value = it }
        model.stroceBottom?.let { _stroceBottomUiState.value = it }

        model.questionList?.let {
            _questionList.value = it
            updateQuestionsState(it)
        }
    }

    fun updateQuestionsState(
        questionList: List<QuestionLocal> = this.questionList.value,
        numQuestion: Int = currentQuestionNumber.value,
        hardQuestion: Boolean = false
    ) {
        questionList.forEach {
            Log.d("sjkfljeskf", "1 $it")
        }

        val currentQuestion = questionList.filter { it.hardQuestion == hardQuestion && it.numQuestion == numQuestion }
        currentQuestion.forEach {
            Log.d("sjkfljeskf", "2 $it")
        }

        Log.d("sjkfljeskf", "2 $numQuestion")

        // Создаем новые списки напрямую через map
        val currentQuestionTranslate = currentQuestion.map {
            TranslateQuestion(it.nameQuestion, it.language)
        }

        val currentAnswerTranslate = currentQuestion.map {
            TranslateAnswer(
                it.nameAnswers.split(SPLIT_BETWEEN_ANSWERS).toMutableList(),
                it.language
            )
        }

        _currentQuestionNumber.value = numQuestion
        _questionNumberSpinnerUiState.value = SpinnerUiState.Visible(
            items = questionList.filter {it.language == settingsConfig.languages[0] }.map { "${it.numQuestion}${if (it.hardQuestion) "*" else ""}. ${it.nameQuestion}" },
            selectedIndex = numQuestion - 1
        )
        _questionList.value = questionList
        _typeQuestionCheckBoxState.value = CheckBoxUiState.Visible(hardQuestion)

        currentQuestionTranslate.forEach {
            Log.d("sjkfljeskf", "currentQuestionTranslate $it")
        }
        currentAnswerTranslate.forEach {
            Log.d("sjkfljeskf", "currentAnswerTranslate $it")
        }

        _currentQuestionTranslationsState.value = emptyList()
        _currentQuestionTranslationsState.value = currentQuestionTranslate
        _answerListState.value = emptyList()
        _answerListState.value = currentAnswerTranslate
    }

    fun selectCategory(name: String) {
        // _categorySpinnerUiState.value = SpinnerUiState.Visible(mutableListOf( name ).add(categorySpinnerUiState.value) )
    }

    fun selectSubCategory(name: String) {
        // _subCategorySpinnerUiState.value = SpinnerUiState.Visible(mutableListOf( name ).add(subCategorySpinnerUiState.value) )
    }

    fun selectSubsubCategory(name: String) {
        // _subsubCategorySpinnerUiState.value = SpinnerUiState.Visible(mutableListOf( name ).add(subsubCategorySpinnerUiState.value) )
    }

    // Function to add a new answer option to all languages of the *current* question
    fun addAnswerOption() {
        val currentList = _questionList.value.toMutableList() ?: return
        val currentQuestionNumber = _currentQuestionNumber.value

        // Находим все вопросы с текущим номером и типом
        val questionsToUpdate = currentList.filter {
            it.numQuestion == currentQuestionNumber &&
                it.hardQuestion == typeQuestionCheckBoxState.value.getType()
        }

        questionsToUpdate.forEach { question ->
            val index = currentList.indexOf(question)
            if (index != -1) {
                currentList[index] = question.copy(
                    nameAnswers = question.nameAnswers + SPLIT_BETWEEN_ANSWERS
                )
            }

            _questionList.value = currentList
            updateQuestionsState()
        }
    }

    fun onAnswerOptionsChanged(updatedTranslateAnswer: TranslateAnswer) {
        val currentList = _questionList.value.toMutableList() ?: return
        val currentQuestionNumber = _currentQuestionNumber.value

        val questionIndex = currentList.indexOfFirst {
            it.numQuestion == currentQuestionNumber &&
                it.language == updatedTranslateAnswer.language
        }

        if (questionIndex != -1) {
            currentList[questionIndex] = currentList[questionIndex].copy(
                nameAnswers = updatedTranslateAnswer.listAnswer.joinToString(SPLIT_BETWEEN_ANSWERS),
                language = updatedTranslateAnswer.language
            )

            if (updatedTranslateAnswer.listAnswer.size >= MAX_ANSWER_OPTIONS_LIMIT) _addAnswerButtonUiState.value =
                TextUiState.Visible(isEnabled = false)
            else _addAnswerButtonUiState.value = TextUiState.Visible(isEnabled = true)

            _questionList.value = currentList
        }
    }

    fun onQuestionTextChanged(updatedTranslateQuestion: TranslateQuestion) {
        Log.d("awdawd", "_questionList: before")
        val currentList = _questionList.value.toMutableList() ?: return
        val currentQuestionNumber = _currentQuestionNumber.value

        val questionIndex = currentList.indexOfFirst {
            it.numQuestion == currentQuestionNumber &&
                it.language == updatedTranslateQuestion.language
        }

        if (questionIndex != -1) {
            currentList[questionIndex] = currentList[questionIndex].copy(
                nameQuestion = updatedTranslateQuestion.question,
                language = updatedTranslateQuestion.language
            )

            _questionList.value = currentList
        }

        Log.d("awdawd", "_questionList: ${_questionList.value}")
    }

    // Function to add a new translation (language) for the current question and answers
    fun addTranslate() {
        val availableLanguages = settingsConfig.languages
        val currentLanguages = _currentQuestionTranslationsState.value.map { it.language }
        val currentList = _questionList.value.toMutableList() ?: return
        val currentQuestionNumber = _currentQuestionNumber.value

        val question = currentList.find {
            it.numQuestion == currentQuestionNumber && it.hardQuestion == typeQuestionCheckBoxState.value.getType()
        }

        val newLanguage = LanguageUtils.entries.firstOrNull { it !in currentLanguages } ?: return

        _questionList.value = questionList.value + QuestionLocal().copy(
            numQuestion = currentQuestionNumber,
            hardQuestion = typeQuestionCheckBoxState.value.getType(),
            language = newLanguage,
            nameAnswers = List(
                (question?.nameAnswers?.split(SPLIT_BETWEEN_ANSWERS)?.size?.minus(1)) ?: 1
            ) { SPLIT_BETWEEN_ANSWERS }.joinToString("")
        )

        updateQuestionsState()
    }

    fun createNewQuestion() {
        val newNumQuestionAllType = QuestionUtils.getNumsQuestion(questionList.value, settingsConfig.languages[0])
        val newNumQuestionThisType: Int =
            if (typeQuestionCheckBoxState.value.getType()) newNumQuestionAllType.second + 1
            else newNumQuestionAllType.first + 1

        val newQuestionList = questionList.value + QuestionLocal(
            id = null,
            numQuestion = newNumQuestionThisType,
            nameAnswers = SPLIT_BETWEEN_ANSWERS,
            hardQuestion = false,
            language = settingsConfig.languages[0]
        )
        Log.d("sjkfljeskf", "createNewQuestion: $newNumQuestionThisType")

        updateQuestionsState(
            numQuestion = newNumQuestionThisType,
            hardQuestion = false,
            questionList = newQuestionList
        )
    }

    fun onQuestionTranslationsChanged(updatedTranslateQuestion: TranslateQuestion) {
        val currentTranslations = _currentQuestionTranslationsState.value.toMutableList()
        val index = currentTranslations.indexOfFirst { it.language == updatedTranslateQuestion.language }
        if (index != -1) {
            currentTranslations[index] = updatedTranslateQuestion
            _currentQuestionTranslationsState.value =
                currentTranslations.toList() // Update the StateFlow with the modified list
        }
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
            currentRegimeStrategy.saveData(questionList.value, pictureQuestionMap, combined, defaultImage)
        }
    }


    fun updateCheckBox() {
        val currentState = _typeQuestionCheckBoxState.value.getType()
        _typeQuestionCheckBoxState.value = CheckBoxUiState.Visible(!currentState, isInit = false)

        val questionThis = questionList.value.filter {
            it.numQuestion == currentQuestionNumber.value && it.hardQuestion == currentState
        }

        questionThis.forEach {
            it.hardQuestion = !currentState
        }
    }

    fun onQuestionLanguageChanged(oldLanguage: LanguageUtils, newLanguage: LanguageUtils) {
        val currentList = _questionList.value.toMutableList() ?: return
        val currentQuestionNumber = _currentQuestionNumber.value

        val questionIndex = currentList.indexOfFirst {
            it.numQuestion == currentQuestionNumber &&
                it.language == oldLanguage
        }

        if (questionIndex != -1) {
            // Создаем копию вопроса с новым языком
            currentList[questionIndex] = currentList[questionIndex].copy(
                language = newLanguage
            )

            Log.d("awdawd", "Language changed from $oldLanguage to $newLanguage for question $currentQuestionNumber")

            // Обновляем список вопросов
            _questionList.value = currentList
            // Обновляем интерфейс
            updateQuestionsState()
        }
    }


}
