package com.tpov.common.presentation.quiz

import androidx.lifecycle.ViewModel
import com.tpov.common.data.model.local.FlattenedQuizData
import com.tpov.common.data.model.local.StructureData
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@InternalCoroutinesApi
class QuizActivityViewModel @Inject constructor(
) : ViewModel() {

    val listFlattenedQuizDataFlow: StateFlow<List<FlattenedQuizData>> get() = _listFlattenedQuizDataFlow
    private val _listFlattenedQuizDataFlow = MutableStateFlow<List<FlattenedQuizData>>(emptyList())


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
                    // Добавляем данные из SubCategoryData
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


}