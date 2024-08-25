package com.tpov.common.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.tpov.common.data.database.StructureRatingDataDao
import com.tpov.common.data.model.remote.StructureData
import com.tpov.common.data.model.remote.StructureLocalData
import com.tpov.common.domain.repository.RepositoryStructure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RepositoryStuctureImpl @Inject constructor(
    private val structureRatingDataDao: StructureRatingDataDao,
    private val firestore: FirebaseFirestore,
    private val context: Context
) : RepositoryStructure {

    private val gson = Gson()
    private val fileName = "structure_data.json"
    private val ratingFileName = "structure_rating.json"

    override suspend fun fetchStructureData(): StructureData? {
        return try {
            val documentSnapshot = firestore.collection("structureData")
                .document("structureData")
                .get().await()
            if (documentSnapshot.exists()) {
                documentSnapshot.toObject(StructureData::class.java) ?: throw Exception("Data parsing error")
            } else {
                Log.d("Firestore", "Document 'structureData' does not exist, returning null.")
                null // Возвращаем null, если документ не найден
            }
        } catch (e: Exception) {
            Log.e("Firestore", "Error fetching document: ${e.message}")
            throw Exception("Error fetching data", e)
        }
    }



    override suspend fun pushStructureRating(ratingData: StructureLocalData) {
        try {
            val dataMap = ratingData.toMap()

            firestore.collection("structureRatings")
                .add(dataMap)
                .addOnSuccessListener { documentReference ->
                    println("DocumentSnapshot added with ID: ${documentReference.id}")
                }
                .addOnFailureListener {
                    CoroutineScope(Dispatchers.IO).launch {
                        saveFailedRatingLocally(ratingData)
                    }
                }
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.IO).launch {
                saveFailedRatingLocally(ratingData)
            }
        }
    }

    override suspend fun saveStructureData(structureData: com.tpov.common.data.model.local.StructureData) {
        withContext(Dispatchers.IO) {
            val json = gson.toJson(structureData)
            context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
                it.write(json.toByteArray())
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

    override suspend fun loadStructureData(): com.tpov.common.data.model.local.StructureData? {
        return withContext(Dispatchers.IO) {
            val file = context.getFileStreamPath(ratingFileName)
            (if (file.exists()) {
                gson.fromJson(
                    context.openFileInput(ratingFileName).bufferedReader().use { it.readText() },
                    com.tpov.common.data.model.local.StructureData::class.java
                )
            } else null)
        }
    }

    private suspend fun saveFailedRatingLocally(ratingData: StructureLocalData) {
        structureRatingDataDao.insert(ratingData.toStructureRatingDataEntity())
    }

    suspend fun retryFailedRatings() {
        val failedRatings = structureRatingDataDao.getAllFailedRatings()
        for (ratingDataEntity in failedRatings) {
            try {
                val dataMap = ratingDataEntity.toStructureRatingData().toMap()

                firestore.collection("structureRatings")
                    .add(dataMap)
                    .addOnSuccessListener { documentReference ->
                        println("DocumentSnapshot added with ID: ${documentReference.id}")
                    }
                    .addOnFailureListener { e ->
                        println("Error adding document: $e")
                    }
            } catch (e: Exception) {
                println("Error saving structure rating: $e")
            }
        }
        structureRatingDataDao.clearFailedRatings()
    }
}
