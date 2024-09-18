package com.tpov.common.domain

import android.annotation.SuppressLint
import android.util.Log
import com.tpov.common.data.RepositoryStuctureImpl
import com.tpov.common.data.model.local.StructureCategoryDataEntity
import com.tpov.common.data.model.local.StructureData
import com.tpov.common.data.model.remote.StructureLocalData
import javax.inject.Inject

class StructureUseCase @Inject constructor(private val repositoryStructureImpl: RepositoryStuctureImpl) {

    suspend fun fetchStructureData() = repositoryStructureImpl.fetchStructureData()

    suspend fun pushStructureLocalData(ratingData: StructureLocalData) {
        repositoryStructureImpl.pushStructureRating(ratingData)
    }

    suspend fun pushStructureCategoryData(structureCategoryDataEntity: StructureCategoryDataEntity) {
        repositoryStructureImpl.pushStructureCategoryData(structureCategoryDataEntity)
    }

    suspend fun retryFailedLocalData() {
        repositoryStructureImpl.retryFailedRatings()
    }


    private val changedQuizzes = mutableListOf<Pair<String, String>>()

    fun logger(i: Int) {
        Log.d("logger", i.toString())
    }

    @SuppressLint("SuspiciousIndentation")
    suspend fun syncStructureDataANDquizzes(): MutableList<Pair<String, String>> {
        Log.d("SyncData", "Starting data synchronization")

        val dataRemote = try {
            repositoryStructureImpl.fetchStructureData()
        } catch (e: Exception) {
            Log.e("SyncData", "Error syncData: ${e.message}")
            null
        }

        Log.d("SyncData", "Remote data fetched: $dataRemote")

        val dataLocal = try {
            repositoryStructureImpl.getStructureData()
        } catch (e: Exception) {
            Log.e("SyncData", "Error loading local data: ${e.message}")
            throw e
        }
        Log.d("SyncData", "Local data loaded: $dataLocal")

        var newDataLocalEmpty: StructureData? = dataRemote?.toStructureDataLocal()
        Log.d("SyncData", "Converted remote data to local format: $newDataLocalEmpty")
        if (newDataLocalEmpty == null) {
            // Выводим тост и возвращаем пустой список
            Log.d("SyncData", "No structure data found on the server. Sync canceled.")
            return emptyList<Pair<String, String>>().toMutableList()

        }

        changedQuizzes.clear()
        val newDataLocal: StructureData =
            if (dataLocal == null) { //Приложение было запущено первый раз
                Log.d("SyncData", "No local data found, using new data")
                newDataLocalEmpty.event.forEach { newEvent ->
                    newEvent.isShowArchive = isShowArhive()
                    newEvent.isShowDownload = isShowDownload(newEvent.id)
                    Log.d("SyncData", "Processing event id: ${newEvent.id}")
                    newEvent.category.forEach { newCategory ->
                        newCategory.isShowArchive = isShowArhive()
                        newEvent.isShowDownload = isShowDownload(newEvent.id)
                        Log.d("SyncData", "Processing category: ${newCategory.nameQuiz}")
                        newCategory.subcategory.forEach { newSubcategory ->
                            newSubcategory.isShowArchive = isShowArhive()
                            newEvent.isShowDownload = isShowDownload(newEvent.id)
                            Log.d("SyncData", "Processing subcategory: ${newSubcategory.nameQuiz}")
                            newSubcategory.subSubcategory.forEach { newSubsub ->
                                newSubsub.isShowArchive = isShowArhive()
                                newEvent.isShowDownload = isShowDownload(newEvent.id)
                                Log.d(
                                    "SyncData",
                                    "Processing subsubcategory: ${newSubsub.nameQuiz}"
                                )
                                newSubsub.quizData.forEach { newQuiz ->
                                    newQuiz.isShowArchive = isShowArhive()
                                    newEvent.isShowDownload = isShowDownload(newEvent.id)
                                    val quizString =
                                        "${newEvent.id}>${newCategory.nameQuiz}>${newSubcategory.nameQuiz}>${newSubsub.nameQuiz}>${newQuiz.nameQuiz}"
                                    val quizInt =
                                        "${newEvent.id}>${newCategory.id}>${newSubcategory.id}>${newSubsub.id}>${newQuiz.idQuiz}>${newQuiz.tpovId}"

                                    Log.d("SyncData", "Adding quiz: $quizString")
                                    if (newQuiz.isShowDownload) changedQuizzes.add(
                                        Pair(
                                            quizString,
                                            quizInt
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                newDataLocalEmpty
            } else {
                Log.d("SyncData", "Merging local and new data")
                mergeData(dataLocal, newDataLocalEmpty)
            }


        try {
            repositoryStructureImpl.saveStructureData(newDataLocal)
            Log.d("SyncData", "Data saved successfully $newDataLocal")
        } catch (e: Exception) {
            Log.e("SyncData", "Error saving data: ${e.message}")
            throw e
        }

        Log.d("SyncData", "Data synchronization completed with changes: $changedQuizzes")
        return changedQuizzes
    }

    private fun isShowDownload(newEventId: Int): Boolean {
        return newEventId == 8
    }

    private fun isShowArhive(): Boolean {
return true
    }

    private fun updateIsShow(newDataLocalEmpty: StructureData) =
        newDataLocalEmpty.event.forEach { newEvent ->
            newEvent.isShowArchive = true

            newEvent.category.forEach { newCategory ->
                newCategory.isShowArchive = true
                newCategory.subcategory.forEach { newSubcategory ->
                    newSubcategory.isShowArchive = true
                    newSubcategory.subSubcategory.forEach { newSubsub ->
                        newSubsub.isShowArchive = true
                        newSubsub.quizData.forEach { newQuiz ->
                            newQuiz.isShowArchive = true
                        }
                    }
                }
            }
        }

    private fun mergeData(
        dataLocal: StructureData,
        newDataLocalEmpty: StructureData
    ): StructureData {

        return StructureData(
            event = newDataLocalEmpty.event.map { newEvent ->
                val localEvent = dataLocal.event.find { it.id == newEvent.id }
                newEvent.copy(
                    category = newEvent.category.map { newCategory ->
                        val localCategory = localEvent?.category?.find { it.id == newCategory.id }
                        newCategory.copy(
                            subcategory = newCategory.subcategory.map { newSubcategory ->
                                val localSubcategory =
                                    localCategory?.subcategory?.find { it.id == newSubcategory.id }
                                newSubcategory.copy(
                                    subSubcategory = newSubcategory.subSubcategory.map { newSubsub ->
                                        val localSubsub =
                                            localSubcategory?.subSubcategory?.find { it.id == newSubsub.id }
                                        newSubsub.copy(
                                            quizData = newSubsub.quizData.map { newQuiz ->
                                                val localQuiz =
                                                    localSubsub?.quizData?.find { it.idQuiz == newQuiz.idQuiz }
                                                if (localQuiz != null && newQuiz.dataUpdate != localQuiz.dataUpdate) {

                                                    val quizString =
                                                        "${newEvent.id}>${newCategory.nameQuiz}>${newSubcategory.nameQuiz}>${newSubsub.nameQuiz}>${newQuiz.nameQuiz}"
                                                    val quizInt =
                                                        "${newEvent.id}>${newCategory.id}>${newSubcategory.id}>${newSubsub.id}>${newQuiz.idQuiz}>${newQuiz.tpovId}"

                                                    if (newQuiz.isShowDownload && newQuiz.dataUpdate.toLong() > localQuiz.dataUpdate.toLong()) changedQuizzes.add(
                                                        Pair(quizString, quizInt)
                                                    )
                                                }
                                                newQuiz.copy(
                                                    ratingLocal = localQuiz?.ratingLocal
                                                        ?: newQuiz.ratingLocal,
                                                    starsMaxLocal = localQuiz?.starsMaxLocal
                                                        ?: newQuiz.starsMaxLocal,
                                                    isShowArchive = localQuiz?.isShowArchive
                                                        ?: newQuiz.isShowArchive,
                                                    isShowDownload = localQuiz?.isShowDownload
                                                        ?: newQuiz.isShowDownload
                                                )
                                            },
                                            ratingLocal = localSubsub?.ratingLocal
                                                ?: newSubsub.ratingLocal,
                                            starsMaxLocal = localSubsub?.starsMaxLocal
                                                ?: newSubsub.starsMaxLocal,
                                            isShowArchive = localSubsub?.isShowArchive
                                                ?: newSubsub.isShowArchive,
                                            isShowDownload = localSubsub?.isShowDownload
                                                ?: newSubsub.isShowDownload
                                        )
                                    },
                                    ratingLocal = localSubcategory?.ratingLocal
                                        ?: newSubcategory.ratingLocal,
                                    starsMaxLocal = localSubcategory?.starsMaxLocal
                                        ?: newSubcategory.starsMaxLocal,
                                    isShowArchive = localSubcategory?.isShowArchive
                                        ?: newSubcategory.isShowArchive,
                                    isShowDownload = localSubcategory?.isShowDownload
                                        ?: newSubcategory.isShowDownload
                                )
                            },
                            ratingLocal = localCategory?.ratingLocal ?: newCategory.ratingLocal,
                            starsMaxLocal = localCategory?.starsMaxLocal
                                ?: newCategory.starsMaxLocal,
                            isShowArchive = localCategory?.isShowArchive
                                ?: newCategory.isShowArchive,
                            isShowDownload = localCategory?.isShowDownload
                                ?: newCategory.isShowDownload
                        )
                    },
                    isShowArchive = localEvent?.isShowArchive ?: newEvent.isShowArchive,
                    isShowDownload = localEvent?.isShowDownload ?: newEvent.isShowDownload
                )
            }
        )
    }

    suspend fun getStructureData(): StructureData? {
        return repositoryStructureImpl.getStructureData()
    }
}