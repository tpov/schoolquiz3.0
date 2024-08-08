package com.tpov.common.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.tpov.common.data.dao.QuizDao
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.remote.StructureData
import com.tpov.common.data.model.remote.StructureRatingData
import com.tpov.common.data.model.remote.toMap
import com.tpov.common.domain.repository.RepositoryStructure
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class RepositoryStuctureImpl @Inject constructor(
    private val quizDao: QuizDao,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : RepositoryStructure {

    override suspend fun fetchQuizzes(): StructureData {
        return try {
            val documentSnapshot = firestore.collection("structureData").document("structureData").get().await()
            if (documentSnapshot.exists()) {
                documentSnapshot.toObject(StructureData::class.java) ?: throw Exception("Data parsing error")
            } else {
                throw Exception("No such document")
            }
        } catch (e: Exception) {
            throw Exception("Error fetching data", e)
        }
    }

    override suspend fun getQuizzes(): StructureData {

    }

        override suspend fun saveStructureRating(ratingData: StructureRatingData) {
            try {
                val dataMap = ratingData.toMap()

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

    override suspend fun pushQuiz(quiz: StructureData) {
        TODO("Not yet implemented")
    }

    override suspend fun insertQuiz(quiz: StructureData) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteQuizById(idQuiz: Int) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteRemoteQuizById(quiz: QuizEntity) {
        TODO("Not yet implemented")
    }

}
