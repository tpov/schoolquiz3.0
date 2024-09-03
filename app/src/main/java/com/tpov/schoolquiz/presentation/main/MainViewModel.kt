package com.tpov.schoolquiz.presentation.main

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tpov.common.data.core.Core.tpovId
import com.tpov.common.data.model.local.CategoryData
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureData
import com.tpov.common.domain.QuestionUseCase
import com.tpov.common.domain.QuizUseCase
import com.tpov.common.domain.StructureUseCase
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.domain.ProfileUseCase
import com.tpov.schoolquiz.presentation.model.QuestionShortEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider

class MainViewModel @Inject constructor(
    private val structureUseCase: StructureUseCase,
    private val quizUseCase: QuizUseCase,
    private val questionUseCase: QuestionUseCase,
    private val profileUseCase: ProfileUseCase,
    private val context: Context
) : ViewModel() {

    val categoryData: LiveData<List<CategoryData>> get() = _categoryData
    private val _categoryData = MutableLiveData<List<CategoryData>>()
    val profileState: StateFlow<ProfileEntity?> get() = _profileState
    private val _profileState = MutableStateFlow<ProfileEntity?>(null)

    val quizData: StateFlow<QuizEntity?> get() = _quizData
    private val _quizData = MutableStateFlow<QuizEntity?>(null)
    val questionData: StateFlow<List<QuestionEntity>?> get() = _questionData
    private val _questionData = MutableStateFlow<List<QuestionEntity>?>(null)
    val structureData: StateFlow<StructureData?> get() = _structureData
    private val _structureData = MutableStateFlow<StructureData?>(null)

    var firstStartApp = false

    init {
        viewModelScope.launch {
            profileUseCase.getProfileFlow(tpovId)?.collect { profile ->
                _profileState.value = profile
            }
        }
    }

    fun getQuestionListShortEntity(questionList: List<QuestionEntity>, languages: String): ArrayList<QuestionShortEntity> {
        val indexedQuestions = questionList.withIndex()
        Log.d("getQuestionListShortEntity", "Indexed Questions: $indexedQuestions")

        // Разделение вопросов на "обычные" и "сложные"
        val (hardQuestions, normalQuestions) = indexedQuestions.partition { it.value.hardQuestion }

        // Функция для сортировки и фильтрации вопросов
        fun sortAndFilterQuestions(questions: List<IndexedValue<QuestionEntity>>): List<IndexedValue<QuestionEntity>> {
            return questions
                .groupBy { it.value.numQuestion }
                .flatMap { (_, questionsGroup) ->
                    Log.d("getQuestionListShortEntity", "Grouping Questions: $questionsGroup")
                    questionsGroup.sortedWith(compareBy(
                        { question -> languages.indexOf(question.value.language).takeIf { it >= 0 } ?: Int.MAX_VALUE },
                        { question -> -question.value.lvlTranslate }
                    )).take(1)
                }
        }

        // Сортировка обычных вопросов по numQuestion
        val sortedNormalQuestions = sortAndFilterQuestions(normalQuestions).sortedBy { it.value.numQuestion }

        // Сортировка сложных вопросов в обратном порядке по numQuestion
        val sortedHardQuestions = sortAndFilterQuestions(hardQuestions).sortedBy { it.value.numQuestion }

        // Объединение результатов: сначала обычные вопросы, затем сложные
        val combinedQuestions = sortedNormalQuestions + sortedHardQuestions

        // Создание итогового списка
        val questionShortList = combinedQuestions.mapIndexed { index, indexedValue ->
            val questionShortEntity = QuestionShortEntity(
                id = index,
                numQuestion = indexedValue.value.numQuestion,
                nameQuestion = indexedValue.value.nameQuestion,
                hardQuestion = indexedValue.value.hardQuestion
            )
            Log.d("getQuestionListShortEntity", "Created QuestionShortEntity: $questionShortEntity")
            questionShortEntity
        }.toCollection(ArrayList())

        Log.d("getQuestionListShortEntity", "Final QuestionShortEntity List: $questionShortList")
        return questionShortList
    }

    fun updateProfile(profileEntity: ProfileEntity) {
        profileUseCase.updateProfile(profileEntity)
    }

    suspend fun getStructureData() = structureUseCase.getStructureData()

    suspend fun loadHomeCategory(): List<String> {
        var listNewQuiz: List<String> = listOf()

        var structureDataList = structureUseCase.getStructureData()
        if (structureDataList == null) {
            firstStartApp = true
            listNewQuiz = structureUseCase.syncData()
            structureDataList = structureUseCase.getStructureData()
        }
        withContext(Dispatchers.Main) {
            _categoryData.value = prepareData(structureDataList ?: retryFromDelay())
        }
        return listNewQuiz
    }

    //This fun started only first run
    suspend fun loadQuizByStructure(listNewQuiz: List<String>) {
        listNewQuiz.forEach { quizStructure ->
            val categories = quizStructure.split("|")

            if (categories.size >= 5) {
                val typeId = categories[0].toIntOrNull() ?: errorGetDataFromStructure()
                val categoryId = categories[1].toIntOrNull() ?: errorGetDataFromStructure()
                val subcategoryId = categories[2].toIntOrNull() ?: errorGetDataFromStructure()
                val subsubcategoryId = categories[3].toIntOrNull() ?: errorGetDataFromStructure()
                val idQuiz = categories[4].toIntOrNull() ?: errorGetDataFromStructure()

                val starsMaxLocal = 0
                val starsAverageLocal = 0
                val ratingLocal = 0

                quizUseCase.insertQuiz(
                    quizUseCase.fetchQuiz(
                        typeId,
                        categoryId,
                        subcategoryId,
                        subsubcategoryId,
                        starsMaxLocal,
                        starsAverageLocal,
                        ratingLocal,
                        idQuiz
                    )
                )

                questionUseCase.fetchQuestion(
                    8,
                    categoryId,
                    subcategoryId,
                    Locale.getDefault().language, idQuiz
                )
            }
        }
    }

    suspend fun createProfile() {
        val currentTimestamp = Instant.now().epochSecond
        val userLocalDate = ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        profileUseCase.insertProfile(
            ProfileEntity(
                dataCreateAcc = currentTimestamp.toString(),
                dateSynch = currentTimestamp.toString(),
                languages = Locale.getDefault().language,
                timeLastOpenBox = userLocalDate
            )
        )
    }

    private suspend fun retryFromDelay(maxAttempts: Int = 100): StructureData {
        var attempt = 0
        while (attempt < maxAttempts) {
            delay(1000L * (attempt + 1))
            val data = structureUseCase.getStructureData()
            if (data != null) {
                return data
            }
            attempt++
        }
        return null!!                   //Best practices
    }

    private fun prepareData(structureDataList: StructureData): List<CategoryData> {
        return structureDataList.event.filter { it.id == 8 }.flatMap { eventData ->
            eventData.category.filter { it.isShowDownload }
        }
    }

    suspend fun insertQuiz(quizEntity: QuizEntity) {
        quizUseCase.insertQuiz(quizEntity)
    }

    suspend fun insertQuestion(questionsList: List<QuestionEntity>) {
        Log.d("savequestionsList ", "questionsList: $questionsList")
        questionsList.forEach { questionUseCase.insertQuestion(it) }
    }

    fun getQuizByIdQuiz(idQuiz: Int) = viewModelScope.launch {
        _quizData.value = quizUseCase.getQuizById(idQuiz)
    }

    fun getQuestionByIdQuiz(idQuiz: Int) = viewModelScope.launch {
        _questionData.value = questionUseCase.getQuestionByIdQuiz(idQuiz)
    }

    private fun errorGetDataFromStructure(): Int {
        return 0
    }

}

class ViewModelFactory @Inject constructor(
    private val creators: Map<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val creator = creators[modelClass] ?: creators.entries.first {
            modelClass.isAssignableFrom(
                it.key
            )
        }.value
        return creator.get() as T
    }
}
