package com.tpov.schoolquiz.presentation.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.domain.utils.QuestionUtils.getNumsQuestion
import com.tpov.common.domain.utils.QuestionUtils.sumPair
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.utils.LanguageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@javax.inject.Singleton
class CreateQuizViewModel @Inject constructor(
    val questionUseCase: QuestionUseCase,
    val structureUseCase: StructureUseCase
) : ViewModel() {

    var questionList: MutableList<QuestionLocal> = arrayListOf()

    // Отфильтрованный список для отображения в ViewPager
    private val _showQuestionList = MutableStateFlow<List<QuestionLocal>>(emptyList())
    val showQuestionList: StateFlow<List<QuestionLocal>> = _showQuestionList.asStateFlow()

    // Данные структуры
    var categoryData = StructureDataLocal()
    var subCategoryData = StructureDataLocal()
    var subsubCategoryData = StructureDataLocal()
    var quizData = StructureDataLocal()

    private val _categoriesList = MutableStateFlow<List<StructureDataLocal>>(emptyList())
    val categoriesList: StateFlow<List<StructureDataLocal>> = _categoriesList.asStateFlow()

    private val _subCategoriesList = MutableStateFlow<List<StructureDataLocal>>(emptyList())
    val subCategoriesList: StateFlow<List<StructureDataLocal>> = _subCategoriesList.asStateFlow()

    private val _subSubCategoriesList = MutableStateFlow<List<StructureDataLocal>>(emptyList())
    val subSubCategoriesList: StateFlow<List<StructureDataLocal>> = _subSubCategoriesList.asStateFlow()

    private val _quizzesList = MutableStateFlow<List<String>>(emptyList())
    val quizzesList: StateFlow<List<String>> = _quizzesList.asStateFlow()

    private val _selectedCategory = MutableStateFlow<StructureDataLocal?>(null)
    val selectedCategory: StateFlow<StructureDataLocal?> = _selectedCategory.asStateFlow()

    private val _selectedSubCategory = MutableStateFlow<StructureDataLocal?>(null)
    val selectedSubCategory: StateFlow<StructureDataLocal?> = _selectedSubCategory.asStateFlow()

    private val _selectedSubSubCategory = MutableStateFlow<StructureDataLocal?>(null)
    val selectedSubSubCategory: StateFlow<StructureDataLocal?> = _selectedSubSubCategory.asStateFlow()

    private val _selectedQuiz = MutableStateFlow<String?>(null)
    val selectedQuiz: StateFlow<String?> = _selectedQuiz.asStateFlow()


    fun showQuestions(hardQuestion: Boolean, pathStructure: PathStructure) = viewModelScope.launch {
        if (questionList.isEmpty()) {
            if (pathStructure != PathStructure()) {
                questionList = questionUseCase.getQuestionByPath(pathStructure).toMutableList()
            }
        }
        var filterQuestion = questionList.filter { it.hardQuestion == hardQuestion }.toMutableList()
        if (filterQuestion.isEmpty()) {
            createEmptyQuestionInternal(hardQuestion)
            filterQuestion = questionList.filter { it.hardQuestion == hardQuestion }.toMutableList()
        }
        _showQuestionList.value = filterQuestion.toList()
    }


    // Проверка готовности к сохранению
    fun isReadyToSave(): Boolean {
        return true // todo validate
    }

    fun saveQuiz(pathStructure: PathStructure, questionList: List<QuestionLocal>, categoryImage: String, subCategoryImage: String, subSubCategoryImage: String, currentQuizImage: String) = viewModelScope.launch(
        Dispatchers.IO) {
        if (isReadyToSave()) {
            // Сохранение вопросов
            questionList.forEach { question ->
                question.pathStructure = pathStructure
                questionUseCase.insertQuestion(question)
            }

            // Сохранение структуры
            val category = StructureDataLocal().create(pathStructure.nameCategory, questionList.getNumsQuestion().sumPair(), 0, "", categoryImage)
            val subCategory = StructureDataLocal().create(pathStructure.nameSubCategory, questionList.getNumsQuestion().sumPair(), 0, "", subCategoryImage)
            val subSubCategory = StructureDataLocal().create(pathStructure.nameSubsubCategory, questionList.getNumsQuestion().sumPair(), 0, "", subSubCategoryImage)
            val quiz = StructureDataLocal().create(pathStructure.nameQuiz, questionList.getNumsQuestion().sumPair(), 0, "", currentQuizImage)

            // Создаем root event структуру
            val eventStructure = StructureDataLocal().create(
                EventQuiz.QUIZ_BY_USER.name,
                questionList.getNumsQuestion().sumPair(),
                0,
                "",
                ""
            ).copy(children = mutableListOf(category.copy(children = mutableListOf(subCategory.copy(children = mutableListOf(subSubCategory.copy(children = mutableListOf(quiz))))))))

            structureUseCase.insertStructureData(eventStructure, EventQuiz.QUIZ_BY_USER)
        }
    }

    fun selectSubCategory(subCategory: StructureDataLocal?) {
        _selectedSubCategory.value = subCategory
    }

    fun selectSubSubCategory(subSubCategory: StructureDataLocal?) {
        _selectedSubSubCategory.value = subSubCategory
    }

    fun selectQuiz(quizName: String?) {
        _selectedQuiz.value = quizName
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
    fun updateQuestionImage(numQuestion: Int, hardQuestion: Boolean, imagePath: String) {
        val questionIndex = questionList.indexOfFirst {   it.numQuestion == numQuestion && it.hardQuestion == hardQuestion  }
        if (questionIndex != -1) {
            questionList[questionIndex] = questionList[questionIndex].copy(
                pathPictureQuestion = imagePath
            )

            // Обновляем отображаемый список если этот вопрос в нем есть
            val currentList = _showQuestionList.value.toMutableList()
            val displayIndex = currentList.indexOfFirst { it.numQuestion == numQuestion  && it.hardQuestion == hardQuestion}
            if (displayIndex != -1) {
                currentList[displayIndex] = currentList[displayIndex].copy(
                    pathPictureQuestion = imagePath
                )
                showQuestions(hardQuestion, PathStructure())
            }
        }
    }

    private fun createEmptyQuestionInternal(isHardQuestion: Boolean) {
        val filteredByType = questionList.filter { it.hardQuestion == isHardQuestion }
        val nextQuestionNumber = (filteredByType.maxOfOrNull { it.numQuestion } ?: 0) + 1

        val newQuestion = QuestionLocal().createEmpty(nextQuestionNumber, isHardQuestion)
        questionList.add(newQuestion)
    }

    fun createEmptyQuestion(isHardQuestion: Boolean) {
        createEmptyQuestionInternal(isHardQuestion)
        val filterQuestion = questionList.filter { it.hardQuestion == isHardQuestion }.toMutableList()
        _showQuestionList.value = filterQuestion.toList()
    }


    fun updateQuestionText(numQuestion: Int, hardQuestion: Boolean, language: LanguageUtils, newText: String) {
        val questionIndex = questionList.indexOfFirst {
            it.numQuestion == numQuestion && it.hardQuestion == hardQuestion && it.language == language
        }
        if (questionIndex != -1) {
            questionList[questionIndex] = questionList[questionIndex].copy(nameQuestion = newText)
        }
    }

    fun updateQuestionAnswers(numQuestion: Int, hardQuestion: Boolean, language: LanguageUtils, answersString: String) {
        val questionIndex = questionList.indexOfFirst {
            it.numQuestion == numQuestion && it.hardQuestion == hardQuestion && it.language == language
        }
        if (questionIndex != -1) {
            questionList[questionIndex] = questionList[questionIndex].copy(nameAnswers = answersString)
        }
    }

}
