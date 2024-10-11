package com.tpov.schoolquiz.presentation.main

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tpov.common.data.core.Core
import com.tpov.common.data.model.local.CategoryData
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity
import com.tpov.common.data.model.local.StructureData
import com.tpov.common.domain.QuestionUseCase
import com.tpov.common.domain.QuizUseCase
import com.tpov.common.domain.StructureUseCase
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.domain.ProfileUseCase
import com.tpov.schoolquiz.presentation.model.QuestionShortEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider

@OptIn(FlowPreview::class)
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

    private val _tpovIdFlow = MutableStateFlow(Core.tpovId)
    val tpovId: StateFlow<Int> = _tpovIdFlow

    init {
        viewModelScope.launch(Dispatchers.Default) {
            var previousProfile: ProfileEntity? = null

            combine(profileUseCase.getProfileFlow() ?: flowOf(null), tpovId) { profile, currentTpovId ->
                profile to currentTpovId }
                .debounce(500)
                .collect { (profile, currentTpovId) ->

                    if ((profile != previousProfile) && currentTpovId != 0 || (profile != previousProfile)) {
                        _profileState.value = profile
                        if (profile == null) {
                            createProfile()
                        } else {
                            //profileUseCase.syncProfile()
                        }
                        previousProfile = profile
                    }
                }
        }
    }


    fun sendStructureCategory(structureCategoryDataEntity: StructureCategoryDataEntity) =
        viewModelScope.launch(Dispatchers.Default) {
            structureUseCase.pushStructureCategoryData(structureCategoryDataEntity)
        }

    fun getQuestionListShortEntity(
        questionList: List<QuestionEntity>,
        languages: String
    ): ArrayList<QuestionShortEntity> {
        val indexedQuestions = questionList.withIndex()

        // Разделение вопросов на "обычные" и "сложные"
        val (hardQuestions, normalQuestions) = indexedQuestions.partition { it.value.hardQuestion }

        // Функция для сортировки и фильтрации вопросов
        fun sortAndFilterQuestions(questions: List<IndexedValue<QuestionEntity>>): List<IndexedValue<QuestionEntity>> {
            return questions
                .groupBy { it.value.numQuestion }
                .flatMap { (_, questionsGroup) ->
                    questionsGroup.sortedWith(compareBy(
                        { question ->
                            languages.indexOf(question.value.language).takeIf { it >= 0 }
                                ?: Int.MAX_VALUE
                        },
                        { question -> -question.value.lvlTranslate }
                    )).take(1)
                }
        }

        // Сортировка обычных вопросов по numQuestion
        val sortedNormalQuestions =
            sortAndFilterQuestions(normalQuestions).sortedBy { it.value.numQuestion }

        // Сортировка сложных вопросов в обратном порядке по numQuestion
        val sortedHardQuestions =
            sortAndFilterQuestions(hardQuestions).sortedBy { it.value.numQuestion }

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
            questionShortEntity
        }.toCollection(ArrayList())

        return questionShortList
    }

    fun updateProfile(profileEntity: ProfileEntity) = viewModelScope.launch(Dispatchers.Default){
        profileUseCase.updateProfile(profileEntity)
    }

    suspend fun getStructureData() = structureUseCase.getStructureData()

    //Загрузить если нету локальной структуры, иначе она загрузится в фоне
    private suspend fun getNewQuizListANDcatDataWithServer(): List<Pair<String, String>> {
        var listNewQuiz: List<Pair<String, String>> = listOf()

        Log.e("test", "getNewQuizListWithServer()")
        var structureDataList = structureUseCase.getStructureData()
        Log.e("test", "2()")
        if (structureDataList == null) {
            Log.e("test", "3()")
            firstStartApp = true
            listNewQuiz = structureUseCase.syncStructureDataANDquizzes()
            structureDataList = structureUseCase.getStructureData()
        }

        Log.e("test", "4() $structureDataList")
        withContext(Dispatchers.Default) {
            _categoryData.postValue(prepareData(structureDataList ?: retryFromDelay()))
        }

        Log.e("test", "getNewQuizListWithServer() end")
        return listNewQuiz
    }

    //Этот метод загружает структуру, сравнивает в локальной, получает список новых квестов и загружает их
    suspend fun getNewStructureDataANDQuizzes(): List<Pair<String, String>> {
        val listNewQuiz = getNewQuizListANDcatDataWithServer()
        loadQuizzesByList(listNewQuiz)

        Log.e("testPushAndFetchQuiz", "2 ${questionUseCase.getQuestionByIdQuiz(101).size}")
        return listNewQuiz
    }

    //Этот метод загружает квизы из строки
    private suspend fun loadQuizzesByList(listNewQuiz: List<Pair<String, String>>) {
        Log.e("test", "loadQuizzesByList()")
        listNewQuiz.forEach {
            val quizStructure = it.second
            val categories = quizStructure.split(">")

            Log.e("testPushAndFetchQuiz", "1")
            if (categories.size >= 5) {
                val typeId = categories[0].toIntOrNull() ?: errorGetDataFromStructure()
                val categoryId = categories[1].toIntOrNull() ?: errorGetDataFromStructure()
                val subcategoryId = categories[2].toIntOrNull() ?: errorGetDataFromStructure()
                val subsubcategoryId = categories[3].toIntOrNull() ?: errorGetDataFromStructure()
                val idQuiz = categories[4].toIntOrNull() ?: errorGetDataFromStructure()
                val tpovId = categories[5].toIntOrNull() ?: errorGetDataFromStructure()

                val starsMaxLocal = 0
                val starsAverageLocal = 0
                val ratingLocal = 0

                Log.e("testPushAndFetchQuiz", "2")
                quizUseCase.insertQuiz(
                    quizUseCase.fetchQuiz(
                        typeId,
                        categoryId,
                        subcategoryId,
                        subsubcategoryId,
                        starsMaxLocal,
                        starsAverageLocal,
                        ratingLocal,
                        idQuiz,
                        tpovId
                    )
                )

                questionUseCase.deleteQuestionByIdQuiz(idQuiz)
                Log.e("testPushAndFetchQuiz", "3")
                questionUseCase.fetchQuestion(
                    typeId,
                    categoryId,
                    subcategoryId,
                    Locale.getDefault().language, idQuiz
                ).forEach {
                    Log.e("testPushAndFetchQuiz", "questionUseCase.fetchQuestion")
                    questionUseCase.insertQuestion(it.toQuizEntity(null, idQuiz))
                }
                updateIsShowDownload(structureUseCase.getStructureData()!!, true)
            }
        }
        Log.e("testPushAndFetchQuiz", "4")
    }

    fun updateIsShowDownload(
        structureData: StructureData,
        newIsShowDownload: Boolean
    ): StructureData {
        return structureData.copy(
            event = structureData.event.map { event ->
                event.copy(
                    isShowDownload = newIsShowDownload,
                    category = event.category.map { category ->
                        category.copy(
                            isShowDownload = newIsShowDownload,
                            subcategory = category.subcategory.map { subCategory ->
                                subCategory.copy(
                                    isShowDownload = newIsShowDownload,
                                    subSubcategory = subCategory.subSubcategory.map { subsubCategory ->
                                        subsubCategory.copy(
                                            isShowDownload = newIsShowDownload,
                                            quizData = subsubCategory.quizData.map { quizData ->
                                                quizData.copy(
                                                    isShowDownload = newIsShowDownload
                                                )
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
    }

    fun createProfile() = viewModelScope.launch(Dispatchers.Default) {
        val currentTimestamp = Instant.now().epochSecond
        val daysSinceEpoch = Instant.now().epochSecond / 86400

        profileUseCase.insertAndPushProfile(
            ProfileEntity(
                dataCreateAcc = currentTimestamp.toString(),
                languages = Locale.getDefault().language,
                timeLastOpenBox = daysSinceEpoch.toString(),
                tpovId = Core.tpovId
            )
        )
    }

    private suspend fun retryFromDelay(maxAttempts: Int = 100): StructureData {
        var attempt = 0
        while (attempt < maxAttempts) {
            delay(1000L * (attempt + 1))
            val data = structureUseCase.getStructureData()
            Log.e("testPushAndFetchQuiz", "data $data")
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

    private suspend fun pushStructureCategory(structureCategoryDataEntity: StructureCategoryDataEntity) =
        quizUseCase.pushStructureCategory(structureCategoryDataEntity)

    suspend fun pushTheQuiz(
        structureCategoryDataEntity: StructureCategoryDataEntity,
        quizEntity: QuizEntity,
        questionsEntity: ArrayList<QuestionEntity>
    ) {
        val newStructureCategory = pushStructureCategory(structureCategoryDataEntity)
        val idQuiz = newStructureCategory.oldIdQuizId
        val eventQuiz = newStructureCategory.newEventId
        val oldCategoryId = newStructureCategory.oldCategoryId
        val oldSubCategoryId = newStructureCategory.oldSubCategoryId
        val oldSubsubCategoryId = newStructureCategory.oldSubsubCategoryId
        Log.d("pushTheQuiz", newStructureCategory.toString())
        quizEntity.id = idQuiz
        quizEntity.event = eventQuiz
        quizEntity.idCategory = oldCategoryId
        quizEntity.idSubcategory = oldSubCategoryId
        quizEntity.idSubsubcategory = oldSubsubCategoryId

        pushQuiz(quizEntity)
        Log.d("pushTheQuiz", "pushQuiz questionsEntity ${questionsEntity.size}")
        questionsEntity.forEach {
            Log.d("pushTheQuiz", "$it")
            val question = it.copy(idQuiz = idQuiz)
            pushQuestion(question, eventQuiz, idQuiz)
            Log.d("pushTheQuiz", "pushQuestion")
        }

        Log.d("pushTheQuiz", "End")
    }

    private suspend fun pushQuiz(quizEntity: QuizEntity) {
        quizUseCase.pushQuiz(quizEntity)
    }

    private suspend fun pushQuestion(questionEntity: QuestionEntity, event: Int, idQuiz: Int) {
        Log.d("pushTheQuiz", "pushingQuestion")
        questionUseCase.pushQuestion(questionEntity, event, idQuiz)
    }

    suspend fun insertQuiz(quizEntity: QuizEntity) {
        quizUseCase.insertQuiz(quizEntity)
    }

    suspend fun insertQuestion(questionsList: List<QuestionEntity>) {
        Log.d("savequestionsList ", "questionsList: $questionsList")
        questionsList.forEach {
            Log.d("savequestionsList ", "question: $it")
            questionUseCase.insertQuestion(it)
        }
    }

    fun getQuizByIdQuiz(idQuiz: Int) = viewModelScope.launch(Dispatchers.Default) {
        _quizData.value = quizUseCase.getQuizById(idQuiz)
    }

    fun getQuestionByIdQuiz(idQuiz: Int) = viewModelScope.launch(Dispatchers.Default) {
        _questionData.value = questionUseCase.getQuestionByIdQuiz(idQuiz)
    }

    private fun errorGetDataFromStructure(): Int {
        return 0
    }

    fun insertQuizThis(
        structureCategoryDataEntity: StructureCategoryDataEntity,
        quizIt: QuizEntity,
        questionsIt: ArrayList<QuestionEntity>
    ) = viewModelScope.launch(Dispatchers.Default) {
        val newIdQuiz = getNewIdQuiz()
        structureUseCase.insertStructureCategoryData(structureCategoryDataEntity)
        quizUseCase.insertQuiz(quizIt.copy(id = newIdQuiz))
        questionsIt.forEach {
            questionUseCase.insertQuestion(it.copy(idQuiz = newIdQuiz))
        }
    }

    private suspend fun getNewIdQuiz(): Int {
        val quizzes = quizUseCase.getQuizzes()
        val usedIds = quizzes?.map { it.id }?.toSet()

        for (i in 1..100) {
            if (i !in usedIds!!) {
                return i
            }
        }

        throw IllegalStateException("Нет свободных ID до 100")
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
