package com.tpov.common.presentation.quiz

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpov.common.EventQuiz
import com.tpov.common.UNKNOWN_VALUE
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.domain.model.StructureDataLocal
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
        PathStructure(UNKNOWN_VALUE, UNKNOWN_VALUE, UNKNOWN_VALUE, UNKNOWN_VALUE, UNKNOWN_VALUE)
    var nameCategory = ""
    var nameSubCategory = ""

    val listStructureDataLocalFlow: StateFlow<List<StructureDataLocal>> get() = _listStructureDataLocalFlow
    private val _listStructureDataLocalFlow =
        MutableStateFlow<List<StructureDataLocal>>(emptyList())

    fun getNamePathEvent(event: Int): String {
        return when (event) {
            EventQuiz.QUIZ_HOME.id -> "Home quiz"
            EventQuiz.QUIZ_BY_USER.id -> "My quiz"
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

    fun initQuestionListByIds(event: Int, idCat: Int, idSubCat: Int) = viewModelScope.launch {
        Log.d("ksjergfkjkseklf", "initQuestionListByIds()")
        val handler = event.let { getEventHandler(it) }
        Log.d("ksjergfkjkseklf", "event.id: ${event}")
        _listStructureDataLocalFlow.value = handler?.invoke(idCat, idSubCat) ?: emptyList()
    }

    private fun getEventHandler(event: Int): suspend (Int, Int) -> List<StructureDataLocal> {
        return when (event) {
            EventQuiz.QUIZ_HOME.id -> ::handleHomeEvent
            EventQuiz.QUIZ_TOURNIRE.id -> ::handleTournamentEvent
            EventQuiz.QUIZ_ARENA.id -> ::handleArenaEvent
//            EventQuiz.QUIZ_FOR_ADMIN.id  -> ::handleAdminEvent
//            EventQuiz.QUIZ_FOR_MODERATOR.id  -> ::handleModeratorEvent
//            EventQuiz.QUIZ_FOR_TESTER.id  -> ::handleTesterEvent
            EventQuiz.QUIZ_BY_USER.id -> ::handleUserEvent
            else -> ::handleDefaultEvent
        }
    }

    private suspend fun handleTournamentEvent(idCat: Int, idSubCat: Int): List<StructureDataLocal> {
        // Логика для турниров
        return emptyList() // Замените своей реализацией
    }

    private suspend fun handleHomeEvent(idCat: Int, idSubCat: Int): List<StructureDataLocal> {
        Log.d("jij", "idCat: $idCat, idSubCat: $idSubCat")
        val flattenedList: MutableList<StructureDataLocal> = mutableListOf()
        val category = structureUseCase.getStructureData(EventQuiz.QUIZ_HOME.id)?.childes
            ?.find { it?.id == idCat }

        Log.d("jij", "category $category")
        nameCategory = category?.nameItem ?: ""
        category?.childes?.forEach {
            Log.d("jij", "subCatData: $it")
            if (idSubCat == -1) flattenedList.add(it!!)
            else it?.childes?.find { it?.id == idSubCat }?.childes?.forEach {
                Log.d("jij", "quizData: $it")
                nameSubCategory = it!!.nameItem
                flattenedList.add(it)
            }
        }
        return flattenedList
    }

    private suspend fun handleUserEvent(idCat: Int, idSubCat: Int): List<StructureDataLocal> {
        Log.d("jij", "handleUserEvent()")
        val flattenedList: MutableList<StructureDataLocal> = mutableListOf()
        val category = structureUseCase.getStructureData(EventQuiz.QUIZ_BY_USER.id)?.childes
            ?.find {
                Log.d("jij", "it.id: ${it?.id}")
                Log.d("jij", "idCat: ${idCat}")
                it?.id == idCat
            }

        category?.childes?.forEach {
            Log.d("jij", "subCatData.name: ${it?.nameItem}")
            it?.childes?.forEach { subsubCat ->
                Log.d("jij", "subsubCat.name: ${subsubCat?.nameItem}")
                subsubCat?.childes?.forEach {
                    Log.d("jij", "quizData.name: ${it?.nameItem}")
                    flattenedList.add(it!!)
                }
            }
        }
        return flattenedList
    }

    private suspend fun handleArenaEvent(idCat: Int, idSubCat: Int): List<StructureDataLocal> {
        // Логика для арены
        return emptyList() // Замените своей реализацией
    }

    private suspend fun handleDefaultEvent(
        idCategory: Int,
        idSubCategory: Int
    ): List<StructureDataLocal> {
        // Универсальный обработчик для необработанных событий
        println("Обработка по умолчанию для категории $idCategory и подкатегории $idSubCategory")
        return emptyList()
    }

}