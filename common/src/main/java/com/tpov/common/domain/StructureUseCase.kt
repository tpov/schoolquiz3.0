package com.tpov.common.domain

import com.tpov.common.data.RepositoryStuctureImpl
import com.tpov.common.data.model.local.StructureData
import com.tpov.common.data.model.remote.StructureLocalData
import javax.inject.Inject

class StructureUseCase @Inject constructor(private val repositoryStructureImpl: RepositoryStuctureImpl) {

    suspend fun fetchQuizzes() = repositoryStructureImpl.fetchStructureData()

    suspend fun pushStructureLocalData(ratingData: StructureLocalData) {
        repositoryStructureImpl.pushStructureRating(ratingData)
    }

    suspend fun retryFailedLocalData() {
        repositoryStructureImpl.retryFailedRatings()
    }

    suspend fun syncData(): List<String> {
        val dataRemote = repositoryStructureImpl.fetchStructureData()
        val dataLocal = repositoryStructureImpl.loadStructureData()
        val newDataLocalEmpty = dataRemote.toStructureDataLocal()
        val changedQuizzes = mutableListOf<String>()

        val newDataLocal: StructureData = if (dataLocal == null) {
            newDataLocalEmpty
        } else {
            mergeData(dataLocal, newDataLocalEmpty, changedQuizzes)
        }

        repositoryStructureImpl.saveStructureData(newDataLocal)
        return changedQuizzes
    }

    private fun mergeData(dataLocal: StructureData, newDataLocalEmpty: StructureData, changedQuizzes: MutableList<String>): StructureData {
        return StructureData(
            event = newDataLocalEmpty.event.map { newEvent ->
                val localEvent = dataLocal.event.find { it.id == newEvent.id }
                newEvent.copy(
                    category = newEvent.category.map { newCategory ->
                        val localCategory = localEvent?.category?.find { it.id == newCategory.id }
                        newCategory.copy(
                            subcategory = newCategory.subcategory.map { newSubcategory ->
                                val localSubcategory = localCategory?.subcategory?.find { it.id == newSubcategory.id }
                                newSubcategory.copy(
                                    subSubcategory = newSubcategory.subSubcategory.map { newSubsub ->
                                        val localSubsub = localSubcategory?.subSubcategory?.find { it.id == newSubsub.id }
                                        newSubsub.copy(
                                            quizData = newSubsub.quizData.map { newQuiz ->
                                                val localQuiz = localSubsub?.quizData?.find { it.idQuiz == newQuiz.idQuiz }
                                                if (localQuiz != null && newQuiz.dataUpdate != localQuiz.dataUpdate) {
                                                    changedQuizzes.add("${newEvent.id}/${newCategory.nameQuiz}/${newSubcategory.nameQuiz}/${newSubsub.nameQuiz}/${newQuiz.nameQuiz}")
                                                }
                                                newQuiz.copy(
                                                    ratingLocal = localQuiz?.ratingLocal ?: newQuiz.ratingLocal,
                                                    starsMaxLocal = localQuiz?.starsMaxLocal ?: newQuiz.starsMaxLocal,
                                                    isShowArchive = localQuiz?.isShowArchive ?: newQuiz.isShowArchive,
                                                    isShowDownload = localQuiz?.isShowDownload ?: newQuiz.isShowDownload
                                                )
                                            },
                                            ratingLocal = localSubsub?.ratingLocal ?: newSubsub.ratingLocal,
                                            starsMaxLocal = localSubsub?.starsMaxLocal ?: newSubsub.starsMaxLocal,
                                            isShowArchive = localSubsub?.isShowArchive ?: newSubsub.isShowArchive,
                                            isShowDownload = localSubsub?.isShowDownload ?: newSubsub.isShowDownload
                                        )
                                    },
                                    ratingLocal = localSubcategory?.ratingLocal ?: newSubcategory.ratingLocal,
                                    starsMaxLocal = localSubcategory?.starsMaxLocal ?: newSubcategory.starsMaxLocal,
                                    isShowArchive = localSubcategory?.isShowArchive ?: newSubcategory.isShowArchive,
                                    isShowDownload = localSubcategory?.isShowDownload ?: newSubcategory.isShowDownload
                                )
                            },
                            ratingLocal = localCategory?.ratingLocal ?: newCategory.ratingLocal,
                            starsMaxLocal = localCategory?.starsMaxLocal ?: newCategory.starsMaxLocal,
                            isShowArchive = localCategory?.isShowArchive ?: newCategory.isShowArchive,
                            isShowDownload = localCategory?.isShowDownload ?: newCategory.isShowDownload
                        )
                    },
                    isShowArchive = localEvent?.isShowArchive ?: newEvent.isShowArchive,
                    isShowDownload = localEvent?.isShowDownload ?: newEvent.isShowDownload
                )
            }
        )
    }

    suspend fun getStructureData(): StructureData? {
        return repositoryStructureImpl.loadStructureData()
    }
}