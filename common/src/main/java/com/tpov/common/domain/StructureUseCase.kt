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

    suspend fun pushStructureRating(ratingData: StructureLocalData) {
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
            Log.d("SyncData", "No structure data found on the server. Sync canceled.")
            return emptyList<Pair<String, String>>().toMutableList()

        }

        changedQuizzes.clear()
        val newDataLocal: StructureData =
            if (dataLocal == null) {
                Log.d("SyncData", "No local data found, using new data")
                newDataLocalEmpty.event.forEach { newEvent ->
                    newEvent.isShowArchive = isShowArhive()
                    newEvent.isShowDownload = isShowDownload(newEvent.id)
                    Log.d("SyncData", "Processing event id: ${newEvent.id}")
                    newEvent.category.forEach { newCategory ->
                        newCategory.isShowArchive = isShowArhive()
                        newCategory.isShowDownload = isShowDownload(newEvent.id)
                        if (newCategory.isShowDownload) fetchPictureStructure(newCategory.picture)
                        Log.d("SyncData", "Processing category: ${newCategory.nameQuiz}")
                        newCategory.subcategory.forEach { newSubcategory ->
                            newSubcategory.isShowArchive = isShowArhive()
                            newSubcategory.isShowDownload = isShowDownload(newEvent.id)
                            if (newSubcategory.isShowDownload) fetchPictureStructure(newCategory.picture)
                            Log.d("SyncData", "Processing subcategory: ${newSubcategory.nameQuiz}")
                            newSubcategory.subSubcategory.forEach { newSubsub ->
                                newSubsub.isShowArchive = isShowArhive()
                                newSubsub.isShowDownload = isShowDownload(newEvent.id)
                                if (newSubsub.isShowDownload) fetchPictureStructure(newCategory.picture)

                                newSubsub.quizData.forEach { newQuiz ->
                                    newQuiz.isShowArchive = isShowArhive()
                                    newQuiz.isShowDownload = isShowDownload(newEvent.id)
                                    val quizString =
                                        "${newEvent.id}>${newCategory.nameQuiz}>${newSubcategory.nameQuiz}>${newSubsub.nameQuiz}>${newQuiz.nameQuiz}"
                                    val quizInt =
                                        "${newEvent.id}>${newCategory.id}>${newSubcategory.id}>${newSubsub.id}>${newQuiz.idQuiz}>${newQuiz.tpovId}"

                                    Log.d("SyncData", "Adding quiz path: $quizString")
                                    Log.d("SyncData", "Adding quiz: $newQuiz")
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
            saveStructureData(newDataLocal)
            Log.d("SyncData", "Data saved successfully $newDataLocal")
        } catch (e: Exception) {
            Log.e("SyncData", "Error saving data: ${e.message}")
            throw e
        }

        Log.d("SyncData", "Data synchronization completed with changes: $changedQuizzes")
        return changedQuizzes
    }

    suspend fun deleteStructureCategoryById(id: Int) {
        repositoryStructureImpl.deleteCategoryById(id)
    }

    private fun isShowDownload(newEventId: Int): Boolean {
        return newEventId == 1 || newEventId == 8
    }

    private fun isShowArhive(): Boolean {
        return true
    }

    suspend fun saveStructureData(structureData: StructureData) {
        repositoryStructureImpl.saveStructureData(structureData)
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

    @SuppressLint("SuspiciousIndentation")
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

                        if (localCategory != null && newCategory.picture != localCategory.picture && newCategory.isShowDownload) {
                            deleteLocalPictureStructure(localCategory.picture)
                            fetchPictureStructure(newCategory.picture)
                        }

                        newCategory.copy(
                            picture = newCategory.picture,
                            subcategory = newCategory.subcategory.map { newSubcategory ->
                                val localSubcategory =
                                    localCategory?.subcategory?.find { it.id == newSubcategory.id }

                                if (localSubcategory != null && newSubcategory.picture != localSubcategory.picture && newSubcategory.isShowDownload) {
                                    deleteLocalPictureStructure(localSubcategory.picture)
                                    fetchPictureStructure(newSubcategory.picture)
                                }

                                newSubcategory.copy(
                                    picture = newSubcategory.picture,
                                    subSubcategory = newSubcategory.subSubcategory.map { newSubsub ->
                                        val localSubsub =
                                            localSubcategory?.subSubcategory?.find { it.id == newSubsub.id }

                                        if (localSubsub != null && newSubsub.picture != localSubsub.picture && newSubsub.isShowDownload) {
                                            deleteLocalPictureStructure(localSubsub.picture)
                                            fetchPictureStructure(newSubsub.picture)
                                        }

                                        newSubsub.copy(
                                            picture = newSubsub.picture,
                                            quizData = newSubsub.quizData.map { newQuiz ->
                                                val localQuiz =
                                                    localSubsub?.quizData?.find { it.idQuiz == newQuiz.idQuiz }

                                                if (localQuiz != null && newQuiz.dataUpdate > localQuiz.dataUpdate) {
                                                    val quizString =
                                                        "${newEvent.id}>${newCategory.nameQuiz}>${newSubcategory.nameQuiz}>${newSubsub.nameQuiz}>${newQuiz.nameQuiz}"
                                                    val quizInt =
                                                        "${newEvent.id}>${newCategory.id}>${newSubcategory.id}>${newSubsub.id}>${newQuiz.idQuiz}>${newQuiz.tpovId}"

                                                    if (newQuiz.isShowDownload && newQuiz.dataUpdate.toLong() > localQuiz.dataUpdate.toLong()) {
                                                        changedQuizzes.add(
                                                            Pair(
                                                                quizString,
                                                                quizInt
                                                            )
                                                        )
                                                    }
                                                } else {
                                                    val quizString =
                                                        "${newEvent.id}>${newCategory.nameQuiz}>${newSubcategory.nameQuiz}>${newSubsub.nameQuiz}>${newQuiz.nameQuiz}"
                                                    val quizInt =
                                                        "${newEvent.id}>${newCategory.id}>${newSubcategory.id}>${newSubsub.id}>${newQuiz.idQuiz}>${newQuiz.tpovId}"

                                                       changedQuizzes.add(
                                                            Pair(
                                                                quizString,
                                                                quizInt
                                                            )
                                                        )
                                                }

                                                newQuiz.copy(
                                                    picture = newQuiz.picture,
                                                    ratingLocal = localQuiz?.ratingLocal
                                                        ?: newQuiz.ratingLocal,
                                                    starsMaxLocal = localQuiz?.starsMaxLocal
                                                        ?: newQuiz.starsMaxLocal,
                                                    isShowArchive = localQuiz?.isShowArchive
                                                        ?: isShowArhive(),
                                                    isShowDownload = localQuiz?.isShowDownload
                                                        ?: isShowDownload(newEvent.id)
                                                )
                                            },
                                            ratingLocal = localSubsub?.ratingLocal
                                                ?: newSubsub.ratingLocal,
                                            starsMaxLocal = localSubsub?.starsMaxLocal
                                                ?: newSubsub.starsMaxLocal,
                                            isShowArchive = localSubsub?.isShowArchive
                                                ?: isShowArhive(),
                                            isShowDownload = localSubsub?.isShowDownload
                                                ?: isShowDownload(newEvent.id)
                                        )
                                    },
                                    ratingLocal = localSubcategory?.ratingLocal
                                        ?: newSubcategory.ratingLocal,
                                    starsMaxLocal = localSubcategory?.starsMaxLocal
                                        ?: newSubcategory.starsMaxLocal,
                                    isShowArchive = localSubcategory?.isShowArchive
                                        ?: isShowArhive(),
                                    isShowDownload = localSubcategory?.isShowDownload
                                        ?: isShowDownload(newEvent.id)
                                )
                            },
                            ratingLocal = localCategory?.ratingLocal ?: newCategory.ratingLocal,
                            starsMaxLocal = localCategory?.starsMaxLocal
                                ?: newCategory.starsMaxLocal,
                            isShowArchive = localCategory?.isShowArchive
                                ?: isShowArhive(),
                            isShowDownload = localCategory?.isShowDownload
                                ?: isShowDownload(newEvent.id)
                        )
                    },
                    isShowArchive = localEvent?.isShowArchive ?: isShowArhive(),
                    isShowDownload = localEvent?.isShowDownload ?: isShowDownload(newEvent.id)
                )
            }
        )
    }

    private fun deleteLocalPictureStructure(name: String) {
        repositoryStructureImpl.deleteLocalPictureStructure(name)
    }

    private fun fetchPictureStructure(name: String) {
        repositoryStructureImpl.fetchPictureStructure(name)
    }

    suspend fun getStructureData(): StructureData? {
        return repositoryStructureImpl.getStructureData()
    }

    suspend fun insertStructureCategoryData(structureCategoryDataEntity: StructureCategoryDataEntity) {
        Log.d("pushQuizData", "structureCategory: $structureCategoryDataEntity")
        repositoryStructureImpl.insertStructureRating(structureCategoryDataEntity)
    }

    suspend fun getStructureCategory(): List<StructureCategoryDataEntity> {
        return repositoryStructureImpl.getStructureCategory()
    }
}