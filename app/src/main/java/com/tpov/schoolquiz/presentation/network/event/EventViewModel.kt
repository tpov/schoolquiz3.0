package com.tpov.schoolquiz.presentation.network.event

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
import kotlinx.coroutines.*
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

    suspend fun getQuestionItem(idQuestion: Int) =
        getQuestionListUseCase().filter { it.id == idQuestion }

    suspend fun getQuestionList(numQuestion: Int, idQuiz: Int) =
        getQuestionListUseCase().filter { it.idQuiz == idQuiz && it.numQuestion == numQuestion }

    fun getProfile(): ProfileEntity {
        return getProfileUseCaseFun(getTpovId())
    }

    private fun getProfileUseCaseFun(tpovId: Int): ProfileEntity {
        log("getProfileUseCaseFun getProfileUseCase(tpovId):${getProfileUseCase(tpovId)}")
        return getProfileUseCase(tpovId)
    }

    suspend fun loadQuests() {
        log("loadQuests")
        // Здесь загружайте список квестов и устанавливайте значение для questsLiveData
        questionLiveData.value = getQuestionListUseCase()
    }

    suspend fun loadQuestion(idQuestion: Int) {
        log("getQuestionListUseCase() :${getQuestionListUseCase()}")
        // Здесь загружайте вопрос и устанавливайте значение для questionLiveData
        questionLiveData.value = getQuestionListUseCase()
    }


    fun getQuizList() {
        log("fun getQuizList")

        runBlocking {
            getQuizEventUseCase().forEach {
                when (it.event) {
                    2 -> quiz2List.add(it)
                    3 -> quiz3List.add(it)
                    4 -> quiz4List.add(it)
                }
                updateEventList.postValue(valUpdateEventList++)
            }
        }

        log("getQuizList quiz2List: $quiz2List")
        log("getQuizList quiz3List: $quiz3List")
        log("getQuizList quiz4List: $quiz4List")
    }

    fun getTranslateList(tpovId: Int) {
        log("getTranslateList getEventTranslateUseCase().size: ${getEventTranslateUseCase().size}")

        getEventTranslateUseCase()
            .groupBy { it.idQuiz }
            .flatMap { (_, questions) ->
                questions.filter { question ->
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
                    }
                ) {
                    translateEditQuestion.add(question)
                    log("getTranslateList translateEditQuestion.add(question): ${translateEditQuestion.add(question)}")

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

                    log("getTranslateList translate1Question.add(question): ${translate1Question.add(question)}")
                    translate1Question.add(question)
                }

                updateEventList.postValue(valUpdateEventList++)
            }

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
            log("updateddsd1: ${it.language}")
            try {
                parseInfoTranslater(it.infoTranslater).forEach {
                    log("updateddsd2: ${it}")
                    createMassage(it.key, it.value)
                }
            } catch (e: Exception) {

            }

            log("dwadwad21: ${it.nameQuestion}")

            CoroutineScope(Dispatchers.IO).launch {
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
            }
            val word = it.language
            val lvlTranslate = it.lvlTranslate

            val currentMinLvlTranslate = wordsMap[word]
            if (currentMinLvlTranslate == null || lvlTranslate < currentMinLvlTranslate) {
                wordsMap[word] = lvlTranslate
            }
            val result = wordsMap.entries.joinToString(separator = "|") { entry ->
                "${entry.key}-${entry.value}"
            }


        }

    }


    private fun hasTpovIdZeroAtEnd(infoTranslater: String): String {
        return if (infoTranslater.endsWith("${getTpovId()}|")) infoTranslater
        else "${infoTranslater}|${getTpovId()}|"

    }

    private fun createMassage(tpovId: String, rating: String) {

    }

    private fun parseInfoTranslater(infoTranslater: String): Map<String, String> {

        val keyValuePairs = infoTranslater.split("|")
        val infoMap = mutableMapOf<String, String>()

        for (pair in keyValuePairs) {
            try {
                val (key, value) = pair.split("|").map { it.trim() }
                if (key.isNotBlank()) {
                    infoMap[key] = value
                }
            } catch (e: Exception) {
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