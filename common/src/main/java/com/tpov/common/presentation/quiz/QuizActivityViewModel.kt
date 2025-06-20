package com.tpov.common.presentation.quiz

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpov.common.data.model.entity.QuestionEntity
import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.usecase.StructureUseCase
import com.tpov.common.presentation.model.PathStructure
import com.tpov.log_api.logger.Logger
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Logger
@InternalCoroutinesApi
class QuizActivityViewModel @Inject constructor(
    val structureUseCase: StructureUseCase
) : ViewModel() {

    var pathStructure: PathStructure? =
        PathStructure()
    var nameCategory = ""
    var nameSubCategory = ""

    val listStructureDataLocalFlow: StateFlow<List<StructureDataLocal>> get() = _listStructureDataLocalFlow
    private val _listStructureDataLocalFlow =
        MutableStateFlow<List<StructureDataLocal>>(emptyList())

    fun getNamePathEvent(event: String): String {
        return when (event) {
            EventQuiz.QUIZ_HOME.name -> "Home quiz"
            EventQuiz.QUIZ_BY_USER.name -> "My quiz"
            else -> "Error quiz"
        }
    }

    private fun getUserLocalization(context: Context): String {
        val config: Configuration = context.resources.configuration
        return config.locale.language
    }

    private fun getListQuestionByProfileLang(
        questionThisListAll: List<QuestionEntity>,
        listMap: MutableMap<Int, Boolean>,
        context: Context
    ): ArrayList<QuestionEntity> {
        val userLocalization: String = getUserLocalization(context)

        val questionList = ArrayList<QuestionEntity>()

        listMap.forEach { map ->
            var filteredList = questionThisListAll
                .filter { it.numQuestion == map.key }
                .filter { it.language == userLocalization }

            if (filteredList.isNotEmpty()) {
                questionList.add(filteredList[0])
            } else {
                filteredList = questionThisListAll
                    .filter { it.numQuestion == map.key }

                if (filteredList.isNotEmpty()) {
                    questionList.add(filteredList[0])
                }
            }
        }
        return questionList
    }

    fun didFoundAllQuestion(
        questionList: List<QuestionEntity>,
        listMap: MutableMap<Int, Boolean>
    ): Boolean {
        var foundQuestion = listMap.isNotEmpty()

        listMap.forEach {

            try {
                if (questionList[it.key - 1].id == null) foundQuestion = false
            } catch (e: Exception) {

                foundQuestion = false
            }
        }

        return foundQuestion
    }

    fun getListQuestionListByLocal(
        listMap: MutableMap<Int, Boolean>,
        questionThisListAll: List<QuestionEntity>,
        context: Context
    ): ArrayList<QuestionEntity> {
        val userLocalization: String = getUserLocalization(context)

        val questionList = ArrayList<QuestionEntity>()
        listMap.forEach { map ->
            val filteredList = questionThisListAll
                .filter { it.numQuestion == map.key }
                .filter { it.language == userLocalization }

            if (filteredList.isNotEmpty()) questionList.add(filteredList[0])
        }

        return questionList
    }

    fun initQuestionListByIds() = viewModelScope.launch {
        Log.d("ksjergfkjkseklf", "initQuestionListByIds()")
        val handler = pathStructure?.nameEvent?.let { getEventHandler(it) }
        _listStructureDataLocalFlow.value = handler?.invoke(pathStructure!!) ?: emptyList()
    }

    private fun getEventHandler(event: String): suspend (PathStructure) -> List<StructureDataLocal> {
        return when (event) {
            EventQuiz.QUIZ_HOME.name -> ::handleHomeEvent
            EventQuiz.QUIZ_TOURNIRE.name -> ::handleTournamentEvent
            EventQuiz.QUIZ_ARENA.name -> ::handleArenaEvent
//            EventQuiz.QUIZ_FOR_ADMIN.name  -> ::handleAdminEvent
//            EventQuiz.QUIZ_FOR_MODERATOR.name  -> ::handleModeratorEvent
//            EventQuiz.QUIZ_FOR_TESTER.name  -> ::handleTesterEvent
            EventQuiz.QUIZ_BY_USER.name -> ::handleUserEvent
            else -> ::handleDefaultEvent
        }
    }

    private suspend fun handleTournamentEvent(pathStructure: PathStructure): List<StructureDataLocal> {
        // Логика для турниров
        return emptyList() // Замените своей реализацией
    }

    private suspend fun handleHomeEvent(pathStructure: PathStructure): List<StructureDataLocal> {
        val flattenedList: MutableList<StructureDataLocal> = mutableListOf()
        val category = structureUseCase.getStructureEventData(EventQuiz.QUIZ_HOME)
            ?.find { it.nameItem == pathStructure.nameCategory}

        nameCategory = category?.nameItem ?: ""
        category?.children?.forEach {
            if (pathStructure.nameSubCategory == "") flattenedList.add(it)
            else it.children?.find { it.nameItem == pathStructure.nameSubCategory }?.children?.forEach {
                nameSubCategory = it.nameItem
                flattenedList.add(it)
            }
        }
        return flattenedList
    }

    var getHomePath: MutableMap<StructureDataLocal, PathStructure> = mutableMapOf()
    private suspend fun handleUserEvent(pathStructure: PathStructure): List<StructureDataLocal> {
        val flattenedList: MutableList<StructureDataLocal> = mutableListOf()
        val category = structureUseCase.getStructureEventData(EventQuiz.QUIZ_BY_USER)
            ?.find {it.nameItem == pathStructure.nameCategory }

        category?.children?.forEach { subCat ->
            subCat.children?.forEach { subsubCat ->
                subsubCat.children?.forEach { quiz ->
                    getHomePath[quiz] = PathStructure(pathStructure?.nameEvent!!, nameCategory, subCat.nameItem!!, subsubCat.nameItem!!, quiz.nameItem!!)
                    flattenedList.add(quiz)
                }
            }
        }
        return flattenedList
    }

    private suspend fun handleArenaEvent(pathStructure: PathStructure): List<StructureDataLocal> {
        // Логика для арены
        return emptyList() // Замените своей реализацией
    }

    private suspend fun handleDefaultEvent(
        pathStructure: PathStructure
    ): List<StructureDataLocal> {
        return emptyList()
    }
}
