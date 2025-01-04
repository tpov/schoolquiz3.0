package com.tpov.common.presentation.quiz

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tpov.common.EventQuiz
import com.tpov.common.data.model.local.FlattenedQuizData
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.local.StructureData
import com.tpov.common.domain.usecase.StructureUseCase
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

    var nameCategory = ""
    var nameSubCategory = ""

    val listFlattenedQuizDataFlow: StateFlow<List<FlattenedQuizData>> get() = _listFlattenedQuizDataFlow
    private val _listFlattenedQuizDataFlow = MutableStateFlow<List<FlattenedQuizData>>(emptyList())

    fun getNamePathEvent(event: EventQuiz): String {
        return when (event) {
            EventQuiz.QUIZ_HOME -> "Home quiz"
            EventQuiz.QUIZ_BY_USER -> "My quiz"
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

    fun flattenStructureData(structure: StructureData): List<FlattenedQuizData> {
        val result = mutableListOf<FlattenedQuizData>()

        structure.event.forEach { event ->
            event.category.forEach { category ->
                if (category.isShowDownload) {
                    result.add(
                        FlattenedQuizData(
                            id = category.id,
                            name = category.nameQuiz,
                            dataUpdate = category.dataUpdate,
                            userName = "",
                            starsMaxLocal = category.starsMaxLocal,
                            starsMaxRemote = category.starsMaxRemote,
                            picture = category.picture,
                            ratingRemote = category.ratingRemote,
                            ratingLocal = category.ratingLocal,
                            isShowArchive = category.isShowArchive,
                            isShowDownload = category.isShowDownload
                        )
                    )
                }

                category.subcategory.forEach { subCategory ->
                    if (subCategory.isShowDownload) {
                        result.add(
                            FlattenedQuizData(
                                id = subCategory.id,
                                name = subCategory.nameQuiz,
                                dataUpdate = subCategory.dataUpdate,
                                userName = subCategory.userName,
                                starsMaxLocal = subCategory.starsMaxLocal,
                                starsMaxRemote = subCategory.starsMaxRemote,
                                picture = subCategory.picture,
                                ratingRemote = subCategory.ratingRemote,
                                ratingLocal = subCategory.ratingLocal,
                                isShowArchive = subCategory.isShowArchive,
                                isShowDownload = subCategory.isShowDownload
                            )
                        )
                    }

                    subCategory.subSubcategory.forEach { subSubCategory ->
                        // Добавляем данные из SubsubCategoryData
                        if (subSubCategory.isShowDownload) {
                            result.add(
                                FlattenedQuizData(
                                    id = subSubCategory.id,
                                    name = subSubCategory.nameQuiz,
                                    dataUpdate = subSubCategory.dataUpdate,
                                    userName = subSubCategory.userName,
                                    starsMaxLocal = subSubCategory.starsMaxLocal,
                                    starsMaxRemote = subSubCategory.starsMaxRemote,
                                    picture = subSubCategory.picture,
                                    ratingRemote = subSubCategory.ratingRemote,
                                    ratingLocal = subSubCategory.ratingLocal,
                                    isShowArchive = subSubCategory.isShowArchive,
                                    isShowDownload = subSubCategory.isShowDownload
                                )
                            )
                        }

                        subSubCategory.quizData.forEach { quiz ->
                            // Добавляем данные из QuizData
                            if (quiz.isShowDownload) {
                                result.add(
                                    FlattenedQuizData(
                                        id = quiz.idQuiz,
                                        name = quiz.nameQuiz,
                                        dataUpdate = quiz.dataUpdate,
                                        userName = quiz.userName,
                                        starsMaxLocal = quiz.starsMaxLocal,
                                        starsMaxRemote = quiz.starsMaxRemote,
                                        picture = quiz.picture,
                                        ratingRemote = quiz.ratingRemote,
                                        ratingLocal = quiz.ratingLocal,
                                        isShowArchive = quiz.isShowArchive,
                                        isShowDownload = quiz.isShowDownload
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        return result
    }

    fun initQuestionListByIds(event: EventQuiz, idCat: Int, idSubCat: Int) = viewModelScope.launch {
        val handler = event.let { getEventHandler(it) }
        _listFlattenedQuizDataFlow.value = handler?.invoke(idCat, idSubCat) ?: emptyList()
    }

    private fun getEventHandler(event: EventQuiz): suspend (Int, Int) -> List<FlattenedQuizData> {
        return when (event) {
            EventQuiz.QUIZ_HOME -> ::handleHomeEvent
            EventQuiz.QUIZ_TOURNIRE -> ::handleTournamentEvent
            EventQuiz.QUIZ_ARENA -> ::handleArenaEvent
//            EventQuiz.QUIZ_FOR_ADMIN -> ::handleAdminEvent
//            EventQuiz.QUIZ_FOR_MODERATOR -> ::handleModeratorEvent
//            EventQuiz.QUIZ_FOR_TESTER -> ::handleTesterEvent
            EventQuiz.QUIZ_BY_USER -> ::handleUserEvent
            else -> ::handleDefaultEvent
        }
    }

    private suspend fun handleTournamentEvent(idCat: Int, idSubCat: Int): List<FlattenedQuizData> {
        // Логика для турниров
        return emptyList() // Замените своей реализацией
    }
    private suspend fun handleHomeEvent(idCat: Int, idSubCat: Int): List<FlattenedQuizData> {
        Log.d("jij", "idCat: $idCat, idSubCat: $idSubCat")
        val flattenedList: MutableList<FlattenedQuizData> = mutableListOf()
        val category = structureUseCase.getStructureData()?.event
            ?.find { it.id == EventQuiz.QUIZ_HOME.id }?.category?.find { it.id == idCat }

        Log.d("jij", " $category")
        nameCategory = category?.nameQuiz ?: ""
        category?.subcategory?.forEach {
            Log.d("jij", "subCatData: $it")
            if (idSubCat == -1) flattenedList.add(it.toFlattenedQuizData())
            else it.subSubcategory.find { it.id == idSubCat }?.quizData?.forEach {
                Log.d("jij", "quizData: $it")
                nameSubCategory = it.nameQuiz
                flattenedList.add(it.toFlattenedQuizData())
            }
        }
        return flattenedList
    }

    private suspend fun handleUserEvent(idCat: Int, idSubCat: Int): List<FlattenedQuizData> {
        Log.d("jij", "handleUserEvent()")
        val flattenedList: MutableList<FlattenedQuizData> = mutableListOf()
        val category = structureUseCase.getStructureData()?.event
            ?.find { it.id == EventQuiz.QUIZ_BY_USER.id }?.category?.find {
                Log.d("jij", "it.id: ${it.id}")
                Log.d("jij", "idCat: ${idCat}")

                it.id == idCat }

        category?.subcategory?.forEach {
            Log.d("jij", "subCatData.name: ${it.nameQuiz}")
            it.subSubcategory.forEach {subsubCat ->
                Log.d("jij", "subsubCat.name: ${subsubCat.nameQuiz}")
                subsubCat.quizData.forEach {
                    Log.d("jij", "quizData.name: ${it.nameQuiz}")
                    flattenedList.add(it.toFlattenedQuizData())
                }
            }
        }
        return flattenedList
    }

    private suspend fun handleArenaEvent(idCat: Int, idSubCat: Int): List<FlattenedQuizData> {
        // Логика для арены
        return emptyList() // Замените своей реализацией
    }

    private suspend fun handleDefaultEvent(
        idCategory: Int,
        idSubCategory: Int
    ): List<FlattenedQuizData> {
        // Универсальный обработчик для необработанных событий
        println("Обработка по умолчанию для категории $idCategory и подкатегории $idSubCategory")
        return emptyList()
    }

}