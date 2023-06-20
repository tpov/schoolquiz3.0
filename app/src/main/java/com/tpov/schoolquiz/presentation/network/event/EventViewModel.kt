package com.tpov.schoolquiz.presentation.network.event

import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.domain.*
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getTpovId
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Inject


class EventViewModel @Inject constructor(
    private val getQuizEventUseCase: GetQuizEventUseCase,
    private val getEventTranslateUseCase: GetEventTranslateUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    private val getQuestionListUseCase: GetQuestionListUseCase,
    val updateProfileUseCase: UpdateProfileUseCase,
    val updateQuestionUseCase: UpdateQuestionUseCase,
    val insertQuestionUseCase: InsertQuestionUseCase,
    val getQuizByIdUseCase: GetQuizByIdUseCase,
    private val updateQuizUseCase: UpdateQuizUseCase,
    private val deleteQuestionUseCase: DeleteQuestionUseCase
) : ViewModel() {
    var quiz2List: MutableList<QuizEntity> = arrayListOf()
    var quiz3List: MutableList<QuizEntity> = arrayListOf()
    var quiz4List: MutableList<QuizEntity> = arrayListOf()
    var translate1Question: MutableList<QuestionEntity> = arrayListOf()
    var translate2Question: MutableList<QuestionEntity> = arrayListOf()
    var translateEditQuestion: MutableList<QuestionEntity> = arrayListOf()
    var moderator: MutableList<ChatEntity> = arrayListOf()
    var admin: MutableList<ChatEntity> = arrayListOf()
    var develop: MutableList<ChatEntity> = arrayListOf()

    var updateEventList: MutableLiveData<Int> = MutableLiveData()

    var valUpdateEventList = 0

    val questionLiveData: MutableLiveData<List<QuestionEntity>?> = MutableLiveData()

    fun getQuestionItem(idQuestion: Int) = getQuestionListUseCase().filter { it.id == idQuestion }

    fun getQuestionList(numQuestion: Int, idQuiz: Int) =
        getQuestionListUseCase().filter { it.idQuiz == idQuiz && it.numQuestion == numQuestion }

    fun getProfile(): ProfileEntity {
        return getProfileUseCaseFun(getTpovId())
    }

    private fun getProfileUseCaseFun(tpovId: Int): ProfileEntity {
        log("getProfileUseCaseFun getProfileUseCase(tpovId):${getProfileUseCase(tpovId)}")
        return getProfileUseCase(tpovId)
    }

    fun loadQuests() {
        log("loadQuests")
        // Здесь загружайте список квестов и устанавливайте значение для questsLiveData
        questionLiveData.value = getQuestionListUseCase()
    }

    fun loadQuestion(idQuestion: Int) {
        log("getQuestionListUseCase() :${getQuestionListUseCase()}")
        // Здесь загружайте вопрос и устанавливайте значение для questionLiveData
        questionLiveData.value = getQuestionListUseCase()
    }


    fun getQuizList() {
        log("fun getQuizList")

        getQuizEventUseCase().forEach {
            when (it.event) {
                2 -> quiz2List.add(it)
                3 -> quiz3List.add(it)
                4 -> quiz4List.add(it)
            }
            updateEventList.postValue(valUpdateEventList++)
        }

        log("getQuizList quiz2List: $quiz2List")
        log("getQuizList quiz3List: $quiz3List")
        log("getQuizList quiz4List: $quiz4List")
    }

    fun getTranslateList(tpovId: Int) {
        log("fun getTranslateList: ${getEventTranslateUseCase()}")
        getEventTranslateUseCase()
            .groupBy { it.idQuiz }
            .flatMap { (_, questions) ->
                questions.filter { question ->
                    log(
                        "getTranslateList: question.language: ${question.language}, getProfileUseCase(tpovId).languages!!.split(|): ${
                            getProfileUseCase(
                                tpovId
                            ).languages!!.split("|")
                        },\n     ${getQuizByIdUseCase(question.idQuiz)}"
                    )
                    question.language !in getProfileUseCase(tpovId).languages!!.split("|") ||
                            question.lvlTranslate < (getProfileUseCase(tpovId).translater)!! - 50
                }
            }
            .forEach { question ->
                if (question.language !in getProfileUseCase(tpovId).languages!!.split("|")
                    && !translateEditQuestion.any {
                        it.numQuestion == question.numQuestion &&
                                it.idQuiz == question.idQuiz &&
                                it.hardQuestion == question.hardQuestion
                    }) {
                    translateEditQuestion.add(question)

                } else if (question.lvlTranslate > 200 &&
                    !translate2Question.any {
                        it.numQuestion == question.numQuestion &&
                                it.idQuiz == question.idQuiz &&
                                it.hardQuestion == question.hardQuestion
                    }
                ) {
                    translate2Question.add(question)

                } else if (!translate1Question.any {
                        it.numQuestion == question.numQuestion &&
                                it.idQuiz == question.idQuiz &&
                                it.hardQuestion == question.hardQuestion
                    }) {

                    translate1Question.add(question)
                }

                updateEventList.postValue(valUpdateEventList++)
            }
        log("getTranslateList translate1Question $translate1Question")
        log("getTranslateList translate2Question $translate2Question")
        log("getTranslateList translateEditQuestion $translateEditQuestion")
    }

    fun getProfileCount(): Int? {
        val profile = getProfileUseCase(getTpovId())
        log("getProfileCount(): $profile, ${getTpovId()}")
        return profile.count
    }

    fun getTpovIdQuiz(id: Int): Int {
        return getQuizByIdUseCase(id).tpovId
    }

    fun getEventDeveloper() {
        log("fun getTranslateList")

        log("getEventDeveloper moderator: $moderator")
        log("getEventDeveloper admin: $admin")
        log("getEventDeveloper develop: $develop")
    }

    fun saveQuestions(updatedQuestions: List<QuestionEntity>, activity: FragmentActivity?) {
        val questionFirst = updatedQuestions[0]
        val idQuiz = questionFirst.idQuiz
        val quiz = getQuizByIdUseCase(idQuiz)
        val wordsMap = mutableMapOf<String, Int>()

        updatedQuestions.forEach {
            try {
                parseInfoTranslater(it.infoTranslater).forEach {
                    createMassage(it.key, it.value)
                }
            } catch (e: Exception) {
                Toast.makeText(activity, "Error parse info Translater", Toast.LENGTH_LONG).show()
            }
            log("update: $it, $questionFirst")
            if (it.nameQuestion == "") deleteQuestionUseCase(it.id!!)
            else insertQuestionUseCase(
                it.copy(
                    numQuestion = questionFirst.numQuestion,
                    answerQuestion = questionFirst.answerQuestion,
                    hardQuestion = questionFirst.hardQuestion,
                    lvlTranslate = getProfileLvlTranslate(),
                    idQuiz = questionFirst.idQuiz,
                    infoTranslater = hasTpovIdZeroAtEnd(it.infoTranslater)
                )
            )
            val word = it.language
            val lvlTranslate = it.lvlTranslate

            val currentMinLvlTranslate = wordsMap[word]
            if (currentMinLvlTranslate == null || lvlTranslate < currentMinLvlTranslate) {
                wordsMap[word] = lvlTranslate
            }
            val result = wordsMap.entries.joinToString(separator = "|") { entry ->
                "${entry.key}-${entry.value}"
            }

            try {
                updateQuizUseCase(
                    quiz.copy(
                        languages = mergeStrings(result, quiz.languages),
                        versionQuiz = quiz.versionQuiz + 1
                    )
                )
            } catch (e: Exception) {
                Toast.makeText(activity, "quiz do not update", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun mergeStrings(string1: String, string2: String): String {
        val mergedMap = mutableMapOf<String, Int>()

        // Обработка первой строки
        val parts1 = string1.split("|")
        for (part in parts1) {
            val (word, value) = part.split("-")
            val currentValue = mergedMap[word]
            if (currentValue == null || value.toInt() > currentValue) {
                mergedMap[word] = value.toInt()
            }
        }

        // Обработка второй строки
        val parts2 = string2.split("|")
        for (part in parts2) {
            val (word, value) = part.split("-")
            val currentValue = mergedMap[word]
            if (currentValue == null || value.toInt() > currentValue) {
                mergedMap[word] = value.toInt()
            }
        }

        // Составление результирующей строки
        val mergedString = StringBuilder()
        for ((word, value) in mergedMap) {
            if (mergedString.isNotEmpty()) {
                mergedString.append("|")
            }
            mergedString.append("$word-$value")
        }

        return mergedString.toString()
    }


    private fun hasTpovIdZeroAtEnd(infoTranslater: String): String {
        return if (infoTranslater.endsWith("${getTpovId()}|0")) infoTranslater
        else "${infoTranslater}|${getTpovId()}|0"

    }

    private fun createMassage(tpovId: String, rating: String) {

    }

    private fun parseInfoTranslater(infoTranslater: String): Map<String, String> {
        val keyValuePairs = infoTranslater.split("|")
        val infoMap = mutableMapOf<String, String>()

        for (pair in keyValuePairs) {
            val (key, value) = pair.split("|").map { it.trim() }
            if (key.isNotBlank()) {
                infoMap[key] = value
            }
        }

        return infoMap
    }

    private fun getProfileLvlTranslate(): Int {
        return getProfileUseCase(getTpovId()).translater ?: 0
    }

}

@OptIn(InternalCoroutinesApi::class)
fun log(m: String) {
    Logcat.log(m, "Event", Logcat.LOG_VIEW_MODEL)
}