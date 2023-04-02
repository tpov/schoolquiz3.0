package com.tpov.schoolquiz.presentation.network.event

import android.content.Context
import androidx.lifecycle.ViewModel
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.domain.*
import com.tpov.schoolquiz.presentation.mainactivity.MainActivity
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Inject


class EventViewModel @Inject constructor(
    private val getQuizEventUseCase: GetQuizEventUseCase,
    private val getEventTranslateUseCase: GetEventTranslateUseCase,
    private val getProfileUseCase: GetProfileUseCase
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


    fun getQuizList() {
        log("fun getQuizList")

        getQuizEventUseCase().forEach {
            when (it.event) {
                2 -> quiz2List.add(it)
                3 -> quiz3List.add(it)
                4 -> quiz4List.add(it)
            }
        }

        log("getQuizList quiz2List: $quiz2List")
        log("getQuizList quiz3List: $quiz3List")
        log("getQuizList quiz4List: $quiz4List")
    }

    fun getTranslateList(tpovId: Int) {
        log("fun getTranslateList")
        getEventTranslateUseCase()
            .groupBy { it.idQuiz }
            .flatMap { (_, questions) ->
                questions.filter { question ->
                    question.language !in getProfileUseCase(tpovId).languages.split("|") ||
                            question.lvlTranslate < getProfileUseCase(tpovId).translater - 50
                }
            }
            .forEach { question ->
                if (question.language !in getProfileUseCase(tpovId).languages.split("|")) {
                    translateEditQuestion.add(question)
                } else if (question.lvlTranslate > 200) {
                    translate2Question.add(question)
                } else {
                    translate1Question.add(question)
                }
            }

        log("getTranslateList translateEditQuestion: $translateEditQuestion")
        log("getTranslateList translate2Question: $translate2Question")
        log("getTranslateList translate1Question: $translate1Question")
    }

    fun getEventDeveloper() {
        log("fun getTranslateList")
        moderator.add(ChatEntity(0, "fewsfs", "user", "mass", 5, 100))
        admin.add(ChatEntity(0, "fewsfs", "user", "mass", 5, 100))
        develop.add(ChatEntity(0, "fewsfs", "user", "mass", 5, 100))

        log("getEventDeveloper moderator: $moderator")
        log("getEventDeveloper admin: $admin")
        log("getEventDeveloper develop: $develop")
    }
}
@OptIn(InternalCoroutinesApi::class)
fun log(m: String) { MainActivity.log(m, "Event", MainActivity.LOG_VIEW_MODEL)}