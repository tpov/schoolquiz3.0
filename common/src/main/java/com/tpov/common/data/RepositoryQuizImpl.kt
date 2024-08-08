package com.tpov.common.data

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.tpov.common.data.core.Core.tpovId
import com.tpov.common.data.dao.QuizDao
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.remote.Quiz
import com.tpov.common.domain.repository.RepositoryQuiz
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject

class RepositoryQuizImpl @Inject constructor(
    private val quizDao: QuizDao,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : RepositoryQuiz {

    private val baseCollection = firestore.collection("quizzes")

    override suspend fun fetchQuizzes(
        typeId: Int,
        categoryId: String,
        subcategoryId: String,
        subsubcategoryId: String
    ): List<Quiz> {
        var collectionReference = baseCollection
            .document("quiz$typeId")
            .collection("quiz$typeId")
            .document(categoryId)
            .collection(categoryId)
            .document(subcategoryId)
            .collection(subcategoryId)
            .document(subsubcategoryId)
            .collection(subsubcategoryId)

        if (typeId == 1) collectionReference =
            collectionReference.document(tpovId.toString()).collection(tpovId.toString())

        val quizzes = collectionReference
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Quiz::class.java) }

        quizzes.forEach { quiz ->
            downloadPhotoToLocalPath(quiz.picture)
        }

        return quizzes
    }

    override suspend fun getQuizzes() = quizDao.getQuiz()

    override suspend fun insertQuiz(quiz: QuizEntity) {
        quizDao.insertQuiz(quiz)
    }

    override suspend fun saveQuiz(quiz: QuizEntity) {
        quizDao.insertQuiz(quiz)
    }

    override suspend fun pushQuiz(quiz: QuizEntity) {
        if (quiz.id == null || quiz.id!! < 100) quiz.id = fetchLastQuizId()

        quiz.picture?.let {
            uploadPhotoToServer(it)
        }

        firestore.runTransaction { transaction ->
            var docRef = baseCollection
                .document("quiz${quiz.event}")
                .collection("quiz${quiz.event}")
                .document(quiz.idCategory.toString())
                .collection(quiz.idCategory.toString())
                .document(quiz.idSubcategory.toString())
                .collection(quiz.idSubcategory.toString())
                .document(quiz.idSubsubcategory.toString())
                .collection(quiz.idSubsubcategory.toString())

            if (quiz.event == 1) {
                docRef = docRef.document(quiz.event.toString()).collection(quiz.event.toString())
            }

            transaction.set(docRef.document(quiz.id.toString()), quiz)
        }.await()
    }

    private suspend fun uploadPhotoToServer(pathPhoto: String) {
        val storageRef = storage.getInstance().reference
        val localFile = File(pathPhoto)
        val photoRef = storageRef.child("quizPhoto/$pathPhoto/${localFile.name}.jpg")

        photoRef.putFile(Uri.fromFile(localFile)).await()
    }

    private suspend fun downloadPhotoToLocalPath(pathPhoto: String): String? {
        val storageRef = storage.getInstance().reference
        val photoRef = storageRef.child(pathPhoto)
        val localFile = File(pathPhoto,  File(pathPhoto).name)

        return try {
            photoRef.getFile(localFile).await()
            localFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun fetchLastQuizId(): Int {
        val snapshot =
            baseCollection.orderBy("id", Query.Direction.DESCENDING).limit(1).get().await()
        return if (!snapshot.isEmpty) (snapshot.documents.firstOrNull()?.getLong("id")
            ?: 99).toInt() + 1
        else 100
    }

    override suspend fun deleteQuizById(idQuiz: Int) {
        quizDao.deleteQuizById(idQuiz)
    }

    override suspend fun deleteRemoteQuizById(quiz: QuizEntity) {
        var docRef = baseCollection
            .document("quiz${quiz.event}")
            .collection("quiz${quiz.event}")
            .document(quiz.idCategory.toString())
            .collection(quiz.idCategory.toString())
            .document(quiz.idSubcategory.toString())
            .collection(quiz.idSubcategory.toString())
            .document(quiz.idSubsubcategory.toString())
            .collection(quiz.idSubsubcategory.toString())

        if (quiz.event == 1) docRef =
            docRef.document(quiz.event.toString()).collection(quiz.event.toString())
        docRef.document(quiz.id.toString()).delete().await()

        quiz.picture?.let {
            deletePhotoFromServer(it)
        }
    }


    private suspend fun deletePhotoFromServer(pathPhoto: String) {
        val storageRef = storage.getInstance().reference
        val photoRef = storageRef.child(pathPhoto)

        try {
            photoRef.delete().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
