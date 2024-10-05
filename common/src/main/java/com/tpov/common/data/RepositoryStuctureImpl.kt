package com.tpov.common.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.gson.Gson
import com.tpov.common.data.core.FirebaseRequestInterceptor
import com.tpov.common.data.core.FirebaseRequestInterceptor.executeWithChecksSingleTask
import com.tpov.common.data.database.StructureCategoryDataDao
import com.tpov.common.data.database.StructureRatingDataDao
import com.tpov.common.data.model.local.StructureCategoryDataEntity
import com.tpov.common.data.model.remote.StructureData
import com.tpov.common.data.model.remote.StructureLocalData
import com.tpov.common.domain.repository.RepositoryStructure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RepositoryStuctureImpl @Inject constructor(
    private val structureRatingDataDao: StructureRatingDataDao,
    private val structureCategoryDataDao: StructureCategoryDataDao,
    private val firestore: FirebaseFirestore,
    private val context: Context
) : RepositoryStructure {

    private val gson = Gson()
    private val fileName = "structure_data.json"
    private val ratingFileName = "structure_rating.json"

    override suspend fun fetchStructureData(): StructureData? {
        val fetchTask = {
            val taskCompletionSource = TaskCompletionSource<StructureData?>()
            val documentRef = firestore.collection("structures").document("structureData")

            documentRef.get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        Log.d("SyncData", "Document 'structureData' exists. Converting to StructureData object.")
                        val structureData = documentSnapshot.toObject(StructureData::class.java)
                        if (structureData != null) {
                            Log.d("SyncData", "Successfully parsed 'structureData': $structureData")
                            taskCompletionSource.setResult(structureData)
                        } else {
                            taskCompletionSource.setException(Exception("Data parsing error"))
                        }
                    } else {
                        Log.d("SyncData", "Document 'structureData' does not exist, returning null.")
                        taskCompletionSource.setResult(null)
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("SyncData", "Error fetching document: ", exception)
                    taskCompletionSource.setException(exception)
                }

            taskCompletionSource.task
        }

        return try {
            executeWithChecksSingleTask(fetchTask)
                .await()
        } catch (e: FirebaseFirestoreException) {
            Log.e("SyncData", "Firestore exception: ${e.message}")
            null
        } catch (e: TimeoutCancellationException) {
            Log.e("SyncData", "Request timeout: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e("SyncData", "General error in fetchStructureData: ${e.message}")
            null
        }
    }


    override suspend fun pushStructureRating(ratingData: StructureLocalData) {
        val pushTask = {
            val taskCompletionSource = TaskCompletionSource<Void>()
            val dataMap = ratingData.toMap()

            firestore.collection("structureRatings")
                .add(dataMap)
                .addOnSuccessListener { documentReference ->
                    Log.d("Firestore", "DocumentSnapshot added with ID: ${documentReference.id}")
                    taskCompletionSource.setResult(null)
                }
                .addOnFailureListener { exception ->
                    Log.e("Firestore", "Error adding document", exception)
                    taskCompletionSource.setException(exception)
                }

            taskCompletionSource.task
        }

        try {
            // Используем FirebaseRequestInterceptor для выполнения задачи
            executeWithChecksSingleTask(pushTask)
                .await()
        } catch (e: Exception) {
            Log.e("Firestore", "Failed to push rating data. Saving locally.", e)
            // Если произошла ошибка, сохраняем данные локально
            saveFailedRatingLocally(ratingData)
        }
    }

    override suspend fun saveStructureData(structureData: com.tpov.common.data.model.local.StructureData) {
        try {
            FirebaseRequestInterceptor.executeWithChecksSingleTask {
                val taskCompletionSource = TaskCompletionSource<Void>()

                    val json = gson.toJson(structureData)
                    context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
                        it.write(json.toByteArray())
                    }
                    taskCompletionSource.setResult(null)

                taskCompletionSource.task
            }.await()

            Log.d("SaveStructureData", "Данные структуры успешно сохранены")
        } catch (e: Exception) {
            Log.e("SaveStructureData", "Ошибка при сохранении данных структуры", e)
            throw e
        }
    }


    override suspend fun pushStructureCategoryData(structureCategoryDataEntity: StructureCategoryDataEntity) {
        try {
            val dataMap = structureCategoryDataEntity.toMap()

            FirebaseRequestInterceptor.executeWithChecksSingleTask {
                val taskCompletionSource = TaskCompletionSource<Void>()

                firestore.collection("structureCategory")
                    .document("structureCategory")  // укажите ваш ID или сгенерируйте его
                    .set(dataMap)
                    .addOnSuccessListener {
                        Log.d("Firestore", "Document successfully written!")
                        taskCompletionSource.setResult(null)
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Error writing document", e)
                        taskCompletionSource.setException(e)

                        // Если произошла ошибка, сохраняем данные локально
                        CoroutineScope(Dispatchers.IO).launch {
                            saveFailedCategoryLocally(structureCategoryDataEntity)
                        }
                    }

                taskCompletionSource.task
            }.await() // Ждём завершения задачи
        } catch (e: Exception) {
            Log.e("Firestore", "Exception while pushing data", e)

            // Если произошла ошибка, сохраняем данные локально
            CoroutineScope(Dispatchers.IO).launch {
                saveFailedCategoryLocally(structureCategoryDataEntity)
            }
        }
    }



    override suspend fun saveListUpdateQuiz(list: List<String>) {
        val sharedPreferences = context.getSharedPreferences("QuizUpdates", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("UpdatedQuizzes", list.joinToString(","))
            apply()
        }
    }

    override suspend fun loadListUpdateQuiz(): List<String> {
        val sharedPreferences = context.getSharedPreferences("QuizUpdates", Context.MODE_PRIVATE)
        val data = sharedPreferences.getString("UpdatedQuizzes", "")
        return data?.split(",")?.filterNot { it.isEmpty() } ?: emptyList()
    }

    override suspend fun insertStructureRating(structureCategoryDataEntity: StructureCategoryDataEntity) {
        structureCategoryDataDao.insert(structureCategoryDataEntity)
    }

    override suspend fun getStructureCategory(): List<StructureCategoryDataEntity> {
        return structureCategoryDataDao.getAllFailedCategory()
    }

    override suspend fun deleteCategoryById(id: Int) {
        structureCategoryDataDao.deleteCategoryById(id)
    }

    override suspend fun getStructureData(): com.tpov.common.data.model.local.StructureData? {
        return withContext(Dispatchers.IO) {
            val file = context.getFileStreamPath(fileName)
            (if (file.exists()) {
                gson.fromJson(
                    context.openFileInput(fileName).bufferedReader().use { it.readText() },
                    com.tpov.common.data.model.local.StructureData::class.java
                )
            } else null)
        }
    }

    private suspend fun saveFailedRatingLocally(ratingData: StructureLocalData) {
        structureRatingDataDao.insert(ratingData.toStructureRatingDataEntity())
    }

    private suspend fun saveFailedCategoryLocally(structureCategoryDataEntity: StructureCategoryDataEntity) {
        structureCategoryDataDao.insert(structureCategoryDataEntity)
    }

    suspend fun retryFailedRatings() {
        val failedRatings = structureRatingDataDao.getAllFailedRatings()
        for (ratingDataEntity in failedRatings) {
            try {
                val dataMap = ratingDataEntity.toStructureRatingData().toMap()

                // Используем перехватчик для отправки данных на сервер
                FirebaseRequestInterceptor.executeWithChecksSingleTask {
                    val taskCompletionSource = TaskCompletionSource<Void>()

                    firestore.collection("structureRatings")
                        .add(dataMap)
                        .addOnSuccessListener { documentReference ->
                            println("DocumentSnapshot added with ID: ${documentReference.id}")
                            taskCompletionSource.setResult(null)
                        }
                        .addOnFailureListener { e ->
                            println("Error adding document: $e")
                            taskCompletionSource.setException(e)
                        }

                    taskCompletionSource.task
                }.await()

            } catch (e: Exception) {
                println("Error saving structure rating: $e")
            }
        }

        // Очищаем локальные записи после успешной попытки
        structureRatingDataDao.clearFailedRatings()
    }
}
