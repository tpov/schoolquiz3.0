package com.tpov.schoolquiz.presentation.network.event

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.domain.*
import com.tpov.schoolquiz.presentation.SPLIT_BETWEEN_LANGUAGES
import com.tpov.schoolquiz.presentation.core.Logcat
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager.getTpovId
import kotlinx.coroutines.*
import javax.inject.Inject


class EventViewModel @Inject constructor(
    val questionUseCase: QuestionUseCase,
    val profileUseCase: ProfileUseCase,
    val quizUseCase: QuizUseCase
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
        questionUseCase.getQuestionList().filter { it.id == idQuestion }

    suspend fun getQuestionList(numQuestion: Int, idQuiz: Int) =
        questionUseCase.getQuestionList()
            .filter { it.idQuiz == idQuiz && it.numQuestion == numQuestion }

    fun getProfile(): ProfileEntity {
        return getProfileUseCaseFun(getTpovId())
    }

    private fun getProfileUseCaseFun(tpovId: Int): ProfileEntity {
        log("getProfileUseCaseFun getProfileUseCase(tpovId):${profileUseCase.getProfile(tpovId)}")
        return profileUseCase.getProfile(tpovId)
    }

    suspend fun loadQuests() {
        log("loadQuests")
        questionLiveData.value = questionUseCase.getQuestionList()
    }

    suspend fun loadQuestion(idQuestion: Int) {
        log("localUseCase.getQuestionList() :${questionUseCase.getQuestionList()}")
        questionLiveData.value = questionUseCase.getQuestionList()
    }


    fun getQuizList() {
        log("fun getQuizList")

        runBlocking {
            quiz2List.clear()
            quiz3List.clear()
            quiz4List.clear()
            quizUseCase.getQuizList().forEach {
                log("getQuizList ${it.event}: $it")
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
        log("eventList() 2: $quiz2List")
    }

    fun getTranslateList(tpovId: Int) {
        viewModelScope.launch {
        log("getTranslateList localUseCase.getEventTranslate().size: ${questionUseCase.getQuestionList().size}")
            questionUseCase.getQuestionList()
                .groupBy { it.idQuiz }
                .flatMap { (_, questions) ->
                    questions.filter { question ->
                        question.language !in profileUseCase.getProfile(tpovId).languages!!.split("|") ||
                                question.lvlTranslate < (profileUseCase.getProfile(tpovId).translater)!! - 50
                    }
                }
                .forEach { question ->
                    if (question.language !in profileUseCase.getProfile(tpovId).languages!!.split("|")
                        && !translateEditQuestion.any {
                            it.numQuestion == question.numQuestion &&
                                    it.idQuiz == question.idQuiz &&
                                    it.hardQuestion == question.hardQuestion
                        }
                    ) {
                        translateEditQuestion.add(question)
                        log(
                            "getTranslateList translateEditQuestion.add(question): ${
                                translateEditQuestion.add(
                                    question
                                )
                            }"
                        )

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

                        log(
                            "getTranslateList translate1Question.add(question): ${
                                translate1Question.add(
                                    question
                                )
                            }"
                        )
                        translate1Question.add(question)
                    }

                    updateEventList.postValue(valUpdateEventList++)
                }
        }
    }

    fun getProfileCount(): Int? {
        val profile = profileUseCase.getProfile(getTpovId())
        log("getProfileCount(): $profile, ${getTpovId()}")
        return profile.count
    }

    fun getTpovIdQuiz(id: Int): Int {
        return quizUseCase.getQuiz(id).tpovId
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
        val quiz = quizUseCase.getQuiz(idQuiz)
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
                if (it.nameQuestion == "") questionUseCase.deleteQuestion(it.id!!)
                else questionUseCase.insertQuestion(
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
            val result =
                wordsMap.entries.joinToString(separator = SPLIT_BETWEEN_LANGUAGES) { entry ->
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

        val keyValuePairs = infoTranslater.split(SPLIT_BETWEEN_LANGUAGES)
        val infoMap = mutableMapOf<String, String>()

        for (pair in keyValuePairs) {
            try {
                val (key, value) = pair.split(SPLIT_BETWEEN_LANGUAGES).map { it.trim() }
                if (key.isNotBlank()) {
                    infoMap[key] = value
                }
            } catch (e: Exception) {
            }
        }

        return infoMap
    }

    private fun getProfileLvlTranslate(): Int {
        return profileUseCase.getProfile(getTpovId()).translater ?: 0
    }
}

@OptIn(InternalCoroutinesApi::class)
fun log(m: String) {
    Logcat.log(m, "Event", Logcat.LOG_VIEW_MODEL)
}