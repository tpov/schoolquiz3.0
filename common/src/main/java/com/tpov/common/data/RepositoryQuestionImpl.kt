package com.tpov.common.data

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.common.data.core.Core.tpovId
import com.tpov.common.data.dao.QuestionDao
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.remote.Question
import com.tpov.common.data.model.remote.toQuestion
import com.tpov.common.domain.repository.RepositoryQuestion
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject

class RepositoryQuestionImpl @Inject constructor(
    private val questionDao: QuestionDao,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : RepositoryQuestion {

    private val baseCollection = firestore.collection("questions")

    override suspend fun fetchQuestion(
        typeId: Int,
        categoryId: String,
        subcategoryId: String,
        pathLanguage: String,
        idQuiz: Int
    ): List<Question> {
        var collectionReference = baseCollection
            .document("question$typeId")
            .collection("question$typeId")
            .document(idQuiz.toString())
            .collection(idQuiz.toString())
            .document(pathLanguage)
            .collection(pathLanguage)

        if (typeId == 1) collectionReference =
            collectionReference.document(tpovId.toString()).collection(tpovId.toString())

        val questions = collectionReference
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Question::class.java) }

        questions.forEach { it.pathPictureQuestion?.let { downloadPhotoToLocalPath(it) } }

        return questions
    }

    override suspend fun getQuestionByIdQuiz(idQuiz: Int) = questionDao.getQuestionByIdQuiz(idQuiz)

    override suspend fun saveQuestion(questionEntity: QuestionEntity) {
        questionDao.insertQuestion(questionEntity)
    }

    override suspend fun pushQuestion(questionEntity: QuestionEntity, pathLanguage: String, event: Int) {
        questionEntity.pathPictureQuestion?.let { uploadPhotoToServer(it) }

        var docRef = baseCollection
            .document("question${event}")
            .collection("question${event}")
            .document(questionEntity.idQuiz.toString())
            .collection(questionEntity.idQuiz.toString())
            .document(pathLanguage)
            .collection(pathLanguage)

        if (event == 1) docRef = docRef.document(tpovId.toString()).collection(tpovId.toString())

        val questionNumber = if (questionEntity.hardQuestion) "-${questionEntity.numQuestion}"
        else questionEntity.numQuestion.toString()

        docRef.document(questionNumber).set(questionEntity.toQuestion()).await()
    }

    override suspend fun updateQuestion(questionEntity: QuestionEntity) {
        questionDao.updateQuestion(questionEntity)
    }

    override suspend fun deleteQuestionByIdQuiz(idQuiz: Int) {
        questionDao.deleteQuestion(idQuiz)
    }

    override suspend fun deleteRemoteQuestionByIdQuiz(
        idQuiz: Int,
        pathLanguage: String,
        typeId: Int,
        numQuestion: Int,
        hardQuestion: Boolean
    ) {
        var collectionReference = baseCollection
            .document("question$typeId")
            .collection("question$typeId")
            .document(idQuiz.toString())
            .collection(idQuiz.toString())
            .document(pathLanguage)
            .collection(pathLanguage)

        if (typeId == 1) {
            collectionReference = collectionReference
                .document(tpovId.toString())
                .collection(tpovId.toString())
        }

        val questions = collectionReference
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Question::class.java) }

        questions.forEach { question ->
            question.pathPictureQuestion?.let { deletePhotoFromServer(it) }

            val questionNumber = if (question.hardQuestion) "-$numQuestion"
            else numQuestion.toString()

            collectionReference.document(questionNumber).delete().await()
        }
    }

    private suspend fun uploadPhotoToServer(pathPhoto: String) {
        val storageRef = storage.reference
        val localFile = File(pathPhoto)
        val photoRef = storageRef.child("questionPhoto/${localFile.name}")

        photoRef.putFile(Uri.fromFile(localFile)).await()
    }

    private suspend fun downloadPhotoToLocalPath(pathPhoto: String): String? {
        val storageRef = storage.reference
        val photoRef = storageRef.child(pathPhoto)
        val localFile = File(pathPhoto, File(pathPhoto).name)

        return try {
            photoRef.getFile(localFile).await()
            localFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun deletePhotoFromServer(pathPhoto: String) {
        val storageRef = storage.reference
        val photoRef = storageRef.child(pathPhoto)

        try {
            photoRef.delete().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}