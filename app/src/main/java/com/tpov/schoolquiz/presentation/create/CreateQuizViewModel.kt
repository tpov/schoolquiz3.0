package com.tpov.schoolquiz.presentation.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.presentation.model.PathStructure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@javax.inject.Singleton
class CreateQuizViewModel @Inject constructor(
    val questionUseCase: QuestionUseCase
) : ViewModel() {

    // Полный список всех вопросов
    var questionList: MutableList<QuestionLocal> = arrayListOf()

    // Отфильтрованный список для отображения в ViewPager
    private val _showQuestionList = MutableStateFlow(mutableListOf<QuestionLocal>())
    val showQuestionList: StateFlow<List<QuestionLocal>> = _showQuestionList.asStateFlow()

    // Данные структуры
    var categoryData = StructureDataLocal()
    var subCategoryData = StructureDataLocal()
    var subsubCategoryData = StructureDataLocal()
    var quizData = StructureDataLocal()

    // StateFlow для отображения в UI
    private val _categoriesList = MutableStateFlow<List<StructureDataLocal>>(emptyList())
    val categoriesList: StateFlow<List<StructureDataLocal>> = _categoriesList.asStateFlow()

    private val _subCategoriesList = MutableStateFlow<List<StructureDataLocal>>(emptyList())
    val subCategoriesList: StateFlow<List<StructureDataLocal>> = _subCategoriesList.asStateFlow()

    private val _subSubCategoriesList = MutableStateFlow<List<StructureDataLocal>>(emptyList())
    val subSubCategoriesList: StateFlow<List<StructureDataLocal>> = _subSubCategoriesList.asStateFlow()

    private val _quizzesList = MutableStateFlow<List<String>>(emptyList())
    val quizzesList: StateFlow<List<String>> = _quizzesList.asStateFlow()

    // Выбранные элементы
    private val _selectedCategory = MutableStateFlow<StructureDataLocal?>(null)
    val selectedCategory: StateFlow<StructureDataLocal?> = _selectedCategory.asStateFlow()

    private val _selectedSubCategory = MutableStateFlow<StructureDataLocal?>(null)
    val selectedSubCategory: StateFlow<StructureDataLocal?> = _selectedSubCategory.asStateFlow()

    private val _selectedSubSubCategory = MutableStateFlow<StructureDataLocal?>(null)
    val selectedSubSubCategory: StateFlow<StructureDataLocal?> = _selectedSubSubCategory.asStateFlow()

    private val _selectedQuiz = MutableStateFlow<String?>(null)
    val selectedQuiz: StateFlow<String?> = _selectedQuiz.asStateFlow()

    // Параметры фильтрации
    private var currentFilter: QuestionFilter = QuestionFilter.ALL
    private var showOnlyHardQuestions: Boolean = false
    private var currentCategoryFilter: String? = null

    init {
        // Инициализация тестовыми данными
        loadTestData()
    }

    private fun loadTestData() {
        // Тестовые категории
        val testCategories = listOf(
            StructureDataLocal().apply {
                nameItem = "Математика"
            },
            StructureDataLocal().apply {
                nameItem = "История"
            },
            StructureDataLocal().apply {
                nameItem = "География"
            }
        )
        _categoriesList.value = testCategories
    }

    // Методы выбора элементов структуры
    fun selectCategory(category: StructureDataLocal) {
        _selectedCategory.value = category
        categoryData = category

        // Очищаем зависимые уровни
        _selectedSubCategory.value = null
        _selectedSubSubCategory.value = null
        _selectedQuiz.value = null

        // Загружаем субкатегории для выбранной категории
        loadSubCategories(category)
    }

    fun selectSubCategory(subCategory: StructureDataLocal) {
        _selectedSubCategory.value = subCategory
        subCategoryData = subCategory

        // Очищаем зависимые уровни
        _selectedSubSubCategory.value = null
        _selectedQuiz.value = null

        // Загружаем субсубкатегории
        loadSubSubCategories(subCategory)
    }

    fun selectSubSubCategory(subSubCategory: StructureDataLocal) {
        _selectedSubSubCategory.value = subSubCategory
        subsubCategoryData = subSubCategory

        // Очищаем квиз
        _selectedQuiz.value = null

        // Загружаем квизы
        loadQuizzes(subSubCategory)
    }

    fun selectQuiz(quizName: String) {
        _selectedQuiz.value = quizName
        quizData = StructureDataLocal().apply { nameItem = quizName }
    }

    private fun loadSubCategories(category: StructureDataLocal) {
        // Тестовые данные для субкатегорий
        val testSubCategories = when (category.nameItem) {
            "Математика" -> listOf(
                StructureDataLocal().apply {
                    nameItem = "Алгебра"
                },
                StructureDataLocal().apply {
                    nameItem = "Геометрия"
                }
            )

            "История" -> listOf(
                StructureDataLocal().apply {
                    nameItem = "Древний мир"
                },
                StructureDataLocal().apply {
                    nameItem = "Средневековье"
                }
            )

            "География" -> listOf(
                StructureDataLocal().apply {
                    nameItem = "Физическая география"
                },
                StructureDataLocal().apply {
                    nameItem = "Политическая география"
                }
            )

            else -> emptyList()
        }
        _subCategoriesList.value = testSubCategories
    }

    private fun loadSubSubCategories(subCategory: StructureDataLocal) {
        // Тестовые данные для субсубкатегорий
        val testSubSubCategories = when (subCategory.nameItem) {
            "Алгебра" -> listOf(
                StructureDataLocal().apply {
                    nameItem = "Уравнения"
                },
                StructureDataLocal().apply {
                    nameItem = "Функции"
                }
            )

            "Геометрия" -> listOf(
                StructureDataLocal().apply {
                    nameItem = "Планиметрия"
                },
                StructureDataLocal().apply {
                    nameItem = "Стереометрия"
                }
            )

            else -> listOf(
                StructureDataLocal().apply {
                    nameItem = "Общие вопросы"
                }
            )
        }
        _subSubCategoriesList.value = testSubSubCategories
    }

    private fun loadQuizzes(subSubCategory: StructureDataLocal) {
        // Тестовые названия квизов
        val testQuizzes = when (subSubCategory.nameItem) {
            "Уравнения" -> listOf(
                "Линейные уравнения",
                "Квадратные уравнения",
                "Системы уравнений"
            )

            "Функции" -> listOf(
                "Линейная функция",
                "Квадратичная функция",
                "Степенная функция"
            )

            else -> listOf(
                "Базовые вопросы",
                "Продвинутый уровень"
            )
        }
        _quizzesList.value = testQuizzes
    }

    // Методы работы с вопросами (оставляем как есть)
    fun addQuestion(question: QuestionLocal) {
        questionList.add(question)
        applyFilter()
    }

    fun removeQuestion(position: Int) {
        if (position >= 0 && position < questionList.size) {
            questionList.removeAt(position)
            applyFilter()
        }
    }

    fun updateQuestion(position: Int, question: QuestionLocal) {
        if (position >= 0 && position < questionList.size) {
            questionList[position] = question
            applyFilter()
        }
    }

    fun clearQuestions() {
        questionList.clear()
        applyFilter()
    }

    fun showAllQuestions(hardQuestion: Boolean, pathStructure: PathStructure) = viewModelScope.launch {
        if (questionList.isEmpty()) {
            if (pathStructure != PathStructure()) {
                questionList = questionUseCase.getQuestionByPath(pathStructure).toMutableList()
            } else {
                createEmptyQuestion(hardQuestion)
            }
        }
        _showQuestionList.value = questionList.filter { it.hardQuestion == hardQuestion }.toMutableList()
    }

    fun showQuestionsByCategory(category: String) {
        currentFilter = QuestionFilter.BY_CATEGORY
        currentCategoryFilter = category
        applyFilter()
    }

    fun showOnlyHardQuestions(showHard: Boolean) {
        showOnlyHardQuestions = showHard
        applyFilter()
    }

    fun showQuestionsRange(startIndex: Int, endIndex: Int) {
        currentFilter = QuestionFilter.BY_RANGE
        val filteredQuestions = questionList.filterIndexed { index, _ ->
            index in startIndex..endIndex
        }
        _showQuestionList.value = filteredQuestions.toMutableList()
    }

    fun showSpecificQuestions(questionNumbers: List<Int>) {
        currentFilter = QuestionFilter.BY_NUMBERS
        val filteredQuestions = questionList.filter { question ->
            question.numQuestion in questionNumbers
        }
        _showQuestionList.value = filteredQuestions.toMutableList()
    }

    private fun applyFilter() {
        val filteredQuestions = when (currentFilter) {
            QuestionFilter.ALL -> questionList.toList()
            QuestionFilter.BY_CATEGORY -> {
                questionList.filter { question ->
                    // Здесь можно добавить логику фильтрации по категории
                    // Пока возвращаем все вопросы
                    true
                }
            }

            QuestionFilter.BY_RANGE -> questionList.toList() // Для BY_RANGE используется отдельный метод
            QuestionFilter.BY_NUMBERS -> questionList.toList() // Для BY_NUMBERS используется отдельный метод
        }

        val finalFilteredQuestions = if (showOnlyHardQuestions) {
            filteredQuestions.filter { it.hardQuestion }
        } else {
            filteredQuestions
        }

        _showQuestionList.value = finalFilteredQuestions.toMutableList()
    }

    // Получить текущий отображаемый вопрос по индексу в ViewPager
    fun getCurrentDisplayedQuestion(pagerPosition: Int): QuestionLocal? {
        return _showQuestionList.value.getOrNull(pagerPosition)
    }

    // Получить общее количество отображаемых вопросов
    fun getDisplayedQuestionsCount(): Int {
        return _showQuestionList.value.size
    }

    // Проверка готовности к сохранению
    fun isReadyToSave(): Boolean {
        return _selectedCategory.value != null &&
            _selectedSubCategory.value != null &&
            _selectedSubSubCategory.value != null &&
            _selectedQuiz.value != null
    }

    fun saveQuiz() {
        if (isReadyToSave()) {
            // TODO: Implement save logic
            // Здесь будет логика сохранения квиза с выбранной структурой
        }
    }


    fun getPathStructure(): com.tpov.common.presentation.model.PathStructure {
        return com.tpov.common.presentation.model.PathStructure().apply {
            nameCategory = selectedCategory.value?.nameItem ?: ""
            nameSubCategory = selectedSubCategory.value?.nameItem ?: ""
            nameSubsubCategory = selectedSubSubCategory.value?.nameItem ?: ""
            nameQuiz = selectedQuiz.value ?: ""
        }
    }

    // Обновить изображение вопроса
    fun updateQuestionImage(questionNumber: Int, imagePath: String) {
        val questionIndex = questionList.indexOfFirst { it.numQuestion == questionNumber }
        if (questionIndex != -1) {
            questionList[questionIndex] = questionList[questionIndex].copy(
                pathPictureQuestion = imagePath
            )

            // Обновляем отображаемый список если этот вопрос в нем есть
            val currentList = _showQuestionList.value.toMutableList()
            val displayIndex = currentList.indexOfFirst { it.numQuestion == questionNumber }
            if (displayIndex != -1) {
                currentList[displayIndex] = currentList[displayIndex].copy(
                    pathPictureQuestion = imagePath
                )
                _showQuestionList.value = currentList
            }
        }
    }

    // Получить вопрос по номеру
    fun getQuestionByNumber(questionNumber: Int): QuestionLocal? {
        return questionList.find { it.numQuestion == questionNumber }
    }

    fun createEmptyQuestion(isHardQuestion: Boolean) {
        val currentDisplayedList = _showQuestionList.value
        val nextQuestionNumber = (currentDisplayedList.maxOfOrNull { it.numQuestion } ?: 0) + 1

        val newQuestion = QuestionLocal().create(nextQuestionNumber, isHardQuestion)

        questionList.add(newQuestion)

        val updatedList = currentDisplayedList.toMutableList()
        updatedList.add(newQuestion)
        _showQuestionList.value = updatedList
    }

    // Обновить текст вопроса
    fun updateQuestionText(questionNumber: Int, newText: String) {
        // Обновляем в основном списке
        val questionIndex = questionList.indexOfFirst { it.numQuestion == questionNumber }
        if (questionIndex != -1) {
            questionList[questionIndex] = questionList[questionIndex].copy(nameQuestion = newText)
        }

        // Обновляем в отображаемом списке
        val currentList = _showQuestionList.value.toMutableList()
        val displayIndex = currentList.indexOfFirst { it.numQuestion == questionNumber }
        if (displayIndex != -1) {
            currentList[displayIndex] = currentList[displayIndex].copy(nameQuestion = newText)
            _showQuestionList.value = currentList
        }
    }

    // Обновить ответы на вопрос
    fun updateQuestionAnswers(questionNumber: Int, newAnswers: String) {
        // Обновляем в основном списке
        val questionIndex = questionList.indexOfFirst { it.numQuestion == questionNumber }
        if (questionIndex != -1) {
            questionList[questionIndex] = questionList[questionIndex].copy(nameAnswers = newAnswers)
        }

        // Обновляем в отображаемом списке
        val currentList = _showQuestionList.value.toMutableList()
        val displayIndex = currentList.indexOfFirst { it.numQuestion == questionNumber }
        if (displayIndex != -1) {
            currentList[displayIndex] = currentList[displayIndex].copy(nameAnswers = newAnswers)
            _showQuestionList.value = currentList
        }
    }

    enum class QuestionFilter {
        ALL,
        BY_CATEGORY,
        BY_RANGE,
        BY_NUMBERS
    }
}
