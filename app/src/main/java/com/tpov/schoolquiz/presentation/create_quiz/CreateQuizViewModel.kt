package com.tpov.schoolquiz.presentation.create_quiz

import android.graphics.drawable.BitmapDrawable
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpov.common.BITMAP_LOAD_MAX_HEIGHT
import com.tpov.common.BITMAP_LOAD_MAX_WIDTH
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity
import com.tpov.common.domain.QuestionUseCase
import com.tpov.common.domain.QuizUseCase
import com.tpov.common.domain.SettingConfigObject
import com.tpov.common.domain.StructureUseCase
import com.tpov.common.presentation.utils.BitmapUtil
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.presentation.core.LanguageUtils
import com.tpov.schoolquiz.presentation.model.QuestionShortEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

class CreateQuizViewModel @Inject constructor(
    val structureUseCase: StructureUseCase,
    private val quizUseCase: QuizUseCase,
    private val questionUseCase: QuestionUseCase,
) : ViewModel() {

    var idQuiz = -1
    var lvlTranslate = 0
    var counter = 0
    var isCreateNewCategory = false
    var questionsEntity: ArrayList<QuestionEntity> = arrayListOf()
    var questionsShortEntity: ArrayList<QuestionShortEntity> = arrayListOf()

    private var structureCategoryDataEntity = StructureCategoryDataEntity()
    var quizEntity: QuizEntity? = null

    var category: String = ""
    var subCategory: String = ""
    var subsubCategory: String = ""

    var isInitialSetupCategorySpinner = true
    var idGroup = 0

    var regime: Int = 0

    fun getQuestionListShortEntity(
        questionList: List<QuestionEntity>,
        languages: String
    ): ArrayList<QuestionShortEntity> {
        val indexedQuestions = questionList.withIndex()

        val (hardQuestions, normalQuestions) = indexedQuestions.partition { it.value.hardQuestion }

        val sortedNormalQuestions =
            sortAndFilterQuestionsForSpinner(normalQuestions,languages).sortedBy { it.value.numQuestion }

        val sortedHardQuestions =
            sortAndFilterQuestionsForSpinner(hardQuestions, languages).sortedBy { it.value.numQuestion }

        val combinedQuestions = sortedNormalQuestions + sortedHardQuestions

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
    fun findMissingNumber(isHardQuestion: Boolean, questionsShortEntity: List<QuestionShortEntity>): Int {
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
    }

    fun getLanguageQuizByQuestions(): String {
        if (questionsEntity.isEmpty()) return ""

        val firstLanguage = questionsEntity.first().language
        val commonLanguage = questionsEntity.all { it.language == firstLanguage }

        return if (commonLanguage) firstLanguage else ""
    }
    fun getNewCategory() = if (!isCreateNewCategory) Triple(
        quizEntity?.idCategory ?: 0,
        quizEntity?.idSubcategory ?: 0,
        quizEntity?.idSubsubcategory ?: 0
    ) else Triple(0, 0, 0)

    fun getUserLanguage(): String {
        return Locale.getDefault().language
    }

    private fun sortAndFilterQuestionsForSpinner(questions: List<IndexedValue<QuestionEntity>>, languages: String): List<IndexedValue<QuestionEntity>> {
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

    suspend fun getStructureData() = structureUseCase.getStructureData()

    fun getAllQuestionsAndLanguagesWithUI(llQuestions: LinearLayout): List<Pair<String, String>> {
        val questionsAndLanguages = mutableListOf<Pair<String, String>>()
        val childCount = llQuestions.childCount

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

        return questionsAndLanguages
    }

    fun insertQuizThis(
        structureCategoryDataEntity: StructureCategoryDataEntity,
        quizIt: QuizEntity,
        questionsIt: ArrayList<QuestionEntity>
    ) = viewModelScope.launch(Dispatchers.Default) {
        when (regime) {
            CreateQuizActivity.REGIME_CREATE_QUIZ -> {
                val newIdQuiz = getNewIdLocalQuiz()
                val updatedStructureCategoryData = structureCategoryDataEntity.copy(oldIdQuizId = newIdQuiz)
                val updatedQuizIt = quizIt.copy(id = newIdQuiz)
                questionsIt.replaceAll { it.copy(idQuiz = newIdQuiz) }

                structureUseCase.insertStructureCategoryData(updatedStructureCategoryData)
                quizUseCase.insertQuiz(updatedQuizIt)
                questionsIt.forEach { questionUseCase.insertQuestion(it) }
            }

            CreateQuizActivity.REGIME_EDIT_QUIZ -> {
                structureUseCase.insertStructureCategoryData(structureCategoryDataEntity)
                quizUseCase.updateQuiz(quizIt)
                questionUseCase.deleteQuestionByIdQuiz(quizIt.id!!)
                questionsIt.forEach { questionUseCase.insertQuestion(it) }
            }

            CreateQuizActivity.REGIME_EDIT_ARCHIVE_MY_QUIZ -> {
                val newIdQuiz = getNewIdLocalQuiz()
val structureCategoryDataEntity = structureCategoryDataEntity.copy(newEventId = 3, oldIdQuizId = newIdQuiz)
                structureUseCase.insertStructureCategoryData(structureCategoryDataEntity)
                quizUseCase.updateQuiz(quizIt)
                questionUseCase.deleteQuestionByIdQuiz(quizIt.id!!)
                questionsIt.forEach { questionUseCase.insertQuestion(it) }
            }

            CreateQuizActivity.REGIME_EDIT_ARCHIVE_QUIZ -> {
            }

            CreateQuizActivity.REGIME_TRANSLATE_QUIZ -> {

            }
        }
    }

fun errorCountLanguage() {

}
    fun determineLanguage(textBeforeSpace: String): String {
        return getUserLanguage()
    }


    private suspend fun getNewIdLocalQuiz(): Int {
        val quizzes = quizUseCase.getQuizzes()
        val usedIds = quizzes?.map { it.id }?.toSet()

        for (i in 1..100) {
            if (i !in usedIds!!) {
                return i
            }
        }

        throw IllegalStateException("Нет свободных ID до 100")
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

            val scaledBitmap = if (bitmap.width > BITMAP_LOAD_MAX_WIDTH || bitmap.height > BITMAP_LOAD_MAX_HEIGHT) {
                BitmapUtil().scaleBitmap(bitmap,BITMAP_LOAD_MAX_WIDTH,BITMAP_LOAD_MAX_HEIGHT)
            } else bitmap

            structureUseCase.savePicture(fileName, scaledBitmap)
        }
    }

    fun getUserName() = SettingConfigObject.settingsConfig.name
}