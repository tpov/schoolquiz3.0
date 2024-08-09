package com.tpov.common.data

import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.common.data.dao.StructureRatingDataDao
import com.tpov.common.data.model.remote.StructureData
import com.tpov.common.data.model.remote.StructureRatingData
import com.tpov.common.domain.repository.RepositoryStructure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class RepositoryStuctureImpl @Inject constructor(
    private val structureRatingDataDao: StructureRatingDataDao,
    private val firestore: FirebaseFirestore
) : RepositoryStructure {

    override suspend fun fetchQuizzes(): StructureData {
        return try {
            val documentSnapshot =
                firestore.collection("structureData").document("structureData").get().await()
            if (documentSnapshot.exists()) {
                documentSnapshot.toObject(StructureData::class.java)
                    ?: throw Exception("Data parsing error")
            } else {
                throw Exception("No such document")
            }
        } catch (e: Exception) {
            throw Exception("Error fetching data", e)
        }
    }

    override suspend fun pushStructureRating(ratingData: StructureRatingData) {
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

    private suspend fun saveFailedRatingLocally(ratingData: StructureRatingData) {
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
