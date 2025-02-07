package com.tpov.schoolquiz.presentation.create_quiz

import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpov.common.BITMAP_LOAD_MAX_HEIGHT
import com.tpov.common.BITMAP_LOAD_MAX_WIDTH
import com.tpov.common.EventQuiz
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.domain.usecase.QuestionUseCase
import com.tpov.common.domain.usecase.SettingConfigObject
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.model.PathStructureName
import com.tpov.common.presentation.utils.BitmapUtil
import com.tpov.common.presentation.utils.LanguageUtils
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.presentation.model.QuestionShortEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

class CreateQuizViewModel @Inject constructor(
    val structureUseCase: StructureUseCase,
    internal val questionUseCase: QuestionUseCase,
) : ViewModel() {

    val eventId: EventQuiz? = null
    var lvlTranslate = 0
    var counter = 0
    var isCreateNewCategory = false
    var questionsEntity: ArrayList<QuestionEntity> = arrayListOf()
    var questionsShortEntity: ArrayList<QuestionShortEntity> = arrayListOf()

    lateinit var pathStructure: PathStructure

    var category: String = ""
    var subCategory: String = ""
    var subsubCategory: String = ""

    var categoryStructure: StructureDataLocal = StructureDataLocal()
    var subCategoryStructure: StructureDataLocal = StructureDataLocal()
    var subsubCategoryStructure: StructureDataLocal = StructureDataLocal()

    var quizEntity: StructureDataLocal? = null
    var oldPathStructure: PathStructure? = null
    var newPathStructure: PathStructure? = null

    var isCreateCategory = false
    var isCreateSubCategory = false
    var isCreateSubsubCategory = false
    var idGroup = 0

    val structureDataFlow: StateFlow<StructureDataLocal?> get() = _structureDataFlow
    private var _structureDataFlow = MutableStateFlow<StructureDataLocal?>(null)
    val categoryDataFlow: StateFlow<List<StructureDataLocal?>?> get() = _categoryDataFlow
    private var _categoryDataFlow = MutableStateFlow<List<StructureDataLocal?>?>(null)
    val subCategoryDataFlow: StateFlow<List<StructureDataLocal?>?> get() = _subCategoryDataFlow
    private var _subCategoryDataFlow = MutableStateFlow<List<StructureDataLocal?>?>(null)
    val subsubCategoryDataFlow: StateFlow<List<StructureDataLocal?>?> get() = _subsubCategoryDataFlow
    private var _subsubCategoryDataFlow = MutableStateFlow<List<StructureDataLocal?>?>(null)

    fun getQuestionListShortEntity(
        questionList: List<QuestionEntity>,
        languages: String
    ): ArrayList<QuestionShortEntity> {
        Log.d("QuestionDebug", "Input questionList size: ${questionList.size}")
        Log.d("QuestionDebug", "Input language: $languages")

        val indexedQuestions = questionList.withIndex()
        Log.d("QuestionDebug", "Indexed questions size: ${indexedQuestions.count()}")

        val (hardQuestions, normalQuestions) = indexedQuestions.partition { it.value.hardQuestion }
        Log.d("QuestionDebug", "Hard questions size: ${hardQuestions.size}")
        Log.d("QuestionDebug", "Normal questions size: ${normalQuestions.size}")

        val sortedNormalQuestions = sortAndFilterQuestionsForSpinner(normalQuestions, languages)
            .sortedBy { it.value.numQuestion }
        Log.d("QuestionDebug", "Sorted normal questions size: ${sortedNormalQuestions.size}")
        Log.d("QuestionDebug", "Sorted normal questions: $sortedNormalQuestions")

        val sortedHardQuestions = sortAndFilterQuestionsForSpinner(hardQuestions, languages)
            .sortedBy { it.value.numQuestion }
        Log.d("QuestionDebug", "Sorted hard questions size: ${sortedHardQuestions.size}")
        Log.d("QuestionDebug", "Sorted hard questions: $sortedHardQuestions")

        val combinedQuestions = sortedNormalQuestions + sortedHardQuestions
        Log.d("QuestionDebug", "Combined questions size: ${combinedQuestions.size}")

        return combinedQuestions.mapIndexed { index, indexedValue ->
            QuestionShortEntity(
                id = index,
                numQuestion = indexedValue.value.numQuestion,
                nameQuestion = indexedValue.value.nameQuestion,
                hardQuestion = indexedValue.value.hardQuestion
            )
        }.toCollection(ArrayList()).also {
            Log.d("QuestionDebug", "Final list size: ${it.size}")
            Log.d("QuestionDebug", "Final list: $it")
        }
    }


    fun findMissingNumber(
        isHardQuestion: Boolean,
        questionsShortEntity: List<QuestionShortEntity>
    ): Int {
        val relevantQuestions = questionsShortEntity.filter { it.hardQuestion == isHardQuestion }
        val maxNumQuestion = relevantQuestions.maxOfOrNull { it.numQuestion } ?: 0
        return maxNumQuestion + 1
    }

    fun updateNewCounterAndShortList(isInit: Boolean = false) {
        if (isInit) {
            counter = 0
            questionsShortEntity = arrayListOf(
                QuestionShortEntity(
                    id = null,
                    numQuestion = 1,
                    nameQuestion = "New Question",
                    hardQuestion = false
                )
            )
            return
        }

        val questionItemThis = questionsShortEntity[counter]
        val isHardQuestion = questionItemThis.hardQuestion
        val missingNumber = findMissingNumber(isHardQuestion, questionsShortEntity)

        val newQuestionItem = QuestionShortEntity(
            id = -1,
            numQuestion = missingNumber,
            nameQuestion = "New Question",
            hardQuestion = isHardQuestion
        )

        val insertPosition = questionsShortEntity.indexOfFirst { it.numQuestion > missingNumber }

        counter = if (insertPosition >= 0) {
            questionsShortEntity.add(insertPosition, newQuestionItem)
            insertPosition
        } else {
            questionsShortEntity.add(newQuestionItem)
            questionsShortEntity.size - 1
        }

        Log.d("rkfgujrdjkgjk", "viewModel.updateNewCounterAndShortList: ${questionsShortEntity}")
    }

    fun getLanguageQuizByQuestions(): String {
        if (questionsEntity.isEmpty()) return ""

        val firstLanguage = questionsEntity.first().language
        val commonLanguage = questionsEntity.all { it.language == firstLanguage }

        return if (commonLanguage) firstLanguage else ""
    }

    fun getUserLanguage(): String {
        return Locale.getDefault().language
    }

    private fun sortAndFilterQuestionsForSpinner(
        questions: List<IndexedValue<QuestionEntity>>,
        languages: String
    ): List<IndexedValue<QuestionEntity>> {
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

    fun initStructureData() = viewModelScope.launch(Dispatchers.IO) {
        val listHome = structureUseCase.getStructureData(EventQuiz.QUIZ_HOME.id)
        val listMyQuiz = structureUseCase.getStructureData(EventQuiz.QUIZ_BY_USER.id)
        Log.d("initStructureData", "listHome: ${listHome}")
        Log.d("initStructureData", "listMyQuiz: ${listMyQuiz}")
        _structureDataFlow.value = StructureDataLocal(children = mutableListOf(listMyQuiz, listHome))
        Log.d("initStructureData", "_structureDataFlow.value: ${_structureDataFlow.value}")
        initCategories(PathStructureName("", "", "", "", ""))
    }

    private val LOG_TAG = "CategoryInitialization"

    fun initCategories(pathStructureName: PathStructureName) {
        val structureData = _structureDataFlow.value ?: run {
            Log.e(LOG_TAG, "Structure data is null")
            return
        }

        // Отладочная печать структуры
        StructureDataLocal(children = structureData.children)
            .printFullStructure("$LOG_TAG - Initial structure")

        // Ищем категорию для пользовательских квизов
        val quizByUserCategory = structureData.children
            ?.find { it?.id == EventQuiz.QUIZ_BY_USER.id }
            ?.children

        _categoryDataFlow.value = quizByUserCategory

        // Определяем имя категории
        val foundNameCategory = when {
            pathStructureName.nameCategory.isBlank() ||
                    pathStructureName.nameCategory == "Create" -> {
                quizByUserCategory?.lastOrNull()?.nameItem ?: run {
                    Log.e(LOG_TAG, "No categories available")
                    return
                }
            }
            else -> pathStructureName.nameCategory
        }

        Log.d(LOG_TAG, "Selected category: $foundNameCategory")

        val subCategories = structureData.children
            ?.mapNotNull { eventStructure ->
                eventStructure?.printFullStructure("$LOG_TAG - Event structure")
                eventStructure?.children
                    ?.filter { it?.nameItem == foundNameCategory }
                    ?.flatMap { it?.children.orEmpty() }
            }
            ?.flatten()
            ?.filterNotNull()

        _subCategoryDataFlow.value = subCategories.orEmpty()

        val foundNameSubCategory = when {
            pathStructureName.nameSubCategory.isBlank() ||
                    pathStructureName.nameSubCategory == "Create" -> {
                subCategories?.lastOrNull()?.nameItem ?: run {
                    Log.e(LOG_TAG, "No subcategories available")
                    return
                }
            }
            else -> pathStructureName.nameSubCategory
        }

        Log.d(LOG_TAG, "Selected subcategory: $foundNameSubCategory")

        val subSubCategories = subCategories
            ?.filter { it.nameItem == foundNameSubCategory }
            ?.flatMap { it.children.orEmpty() }
            ?.filterNotNull()

        _subsubCategoryDataFlow.value = subSubCategories.orEmpty()
    }

    private fun areNamesEqual(
        list1: List<StructureDataLocal?>?,
        list2: List<StructureDataLocal?>?
    ): Boolean {
        if (list1 == null && list2 == null) return true
        if (list1 == null || list2 == null) return false
        if (list1.size != list2.size) return false

        return list1.map { it?.nameItem } == list2.map { it?.nameItem }
    }

    fun getAllQuestionsAndLanguagesWithUI(llQuestions: LinearLayout): List<Pair<String, String>> {
        val questionsAndLanguages = mutableListOf<Pair<String, String>>()
        val childCount = llQuestions.childCount

        Log.d("rkfgujrdjkgjk", " getAllQuestionsAndLanguagesWithUI : ${questionsShortEntity}")
        for (i in 0 until childCount) {
            val questionLayout = llQuestions.getChildAt(i) as LinearLayout

            val questionTextView: EditText =
                questionLayout.findViewById(R.id.tv_question_text1)
            val languageSpinner: Spinner =
                questionLayout.findViewById(R.id.sp_language_question1)

            val questionText = questionTextView.text.toString()
            val selectedLanguageIndex = languageSpinner.selectedItemPosition
            val selectedLanguageCode = LanguageUtils.languagesShortCodes[selectedLanguageIndex]

            questionsAndLanguages.add(Pair(questionText, selectedLanguageCode))
        }

        Log.d("rkfgujrdjkgjk", " getAllQuestionsAndLanguagesWithUI end : ${questionsShortEntity}")
        return questionsAndLanguages
    }

    fun errorCountLanguage() {

    }

    fun determineLanguage(textBeforeSpace: String): String {
        return getUserLanguage()
    }

    fun getAnswersWithUI(
        llGroupAnswer: LinearLayout,
        idCounters: MutableList<MutableList<Int>>
    ): List<Triple<String, String, Int>> {
        val answersList = mutableListOf<Triple<String, String, Int>>()
        val childCount = llGroupAnswer.childCount

        for (i in 0 until childCount) {
            val answerLayout = llGroupAnswer.getChildAt(i) as LinearLayout

            val answerLanguageTextView: TextView =
                answerLayout.findViewById(R.id.tv_answer_language)

            val answers = mutableListOf<String>()
            idCounters[i].forEach {
                answers.add(answerLayout.findViewById<EditText>(it).text.toString())
            }
            val language =
                LanguageUtils.getLanguageShortCode(answerLanguageTextView.text.toString())

            val answersString = answers.joinToString("|")

            answersList.add(Triple(language, answersString, 1))
        }

        return answersList
    }

    fun scaledANDSaveImage(imageView: ImageView, fileName: String) {
        val drawable = imageView.drawable

        if (drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap

            val scaledBitmap =
                if (bitmap.width > BITMAP_LOAD_MAX_WIDTH || bitmap.height > BITMAP_LOAD_MAX_HEIGHT) {
                    BitmapUtil().scaleBitmap(bitmap, BITMAP_LOAD_MAX_WIDTH, BITMAP_LOAD_MAX_HEIGHT)
                } else bitmap

            structureUseCase.savePicture(fileName, scaledBitmap)
        }
    }

    fun getUserName() = SettingConfigObject.settingsConfig.name

    suspend fun updateStructureData(structureDataLocal: StructureDataLocal, eventId: Int) {
        structureUseCase.updateStructureData(structureDataLocal, eventId)
    }

    fun initQuestions() = viewModelScope.launch(Dispatchers.IO) {
        Log.d("rkfgujrdjkgjk", " initQuestions : ${questionsShortEntity}")
        questionsEntity = questionUseCase.getQuestionByPath(pathStructure)
        questionsShortEntity = getQuestionListShortEntity(
            questionsEntity,
            getUserLanguage()
        )
        Log.d("rkfgujrdjkgjk", " initQuestions end : ${questionsShortEntity}")
    }
}