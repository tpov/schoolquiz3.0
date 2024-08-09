package com.tpov.common.data

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.tpov.common.data.core.Core.tpovId
import com.tpov.common.data.dao.QuestionDao
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.remote.QuestionRemote
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
        event: Int,
        categoryId: String,
        subcategoryId: String,
        language: String,
        idQuiz: Int
    ): List<QuestionEntity> {

        var baseCollectionReference = baseCollection
            .document("question$event")
            .collection("question$event")
            .document(idQuiz.toString())
            .collection(idQuiz.toString())

        if (event == 1) {
            baseCollectionReference = baseCollectionReference
                .document(tpovId.toString())
                .collection(tpovId.toString())
        }

        val questionRemotes = mutableListOf<QuestionEntity>()

        val questionDirectories = baseCollectionReference.get().await().documents

        for (questionDirectory in questionDirectories) {
            val numQuestionStr = questionDirectory.id
            val numQuestion = numQuestionStr.toIntOrNull() ?: 0
            val hardQuestion = numQuestion < 0
            val languageCollection = baseCollectionReference
                .document(numQuestionStr)
                .collection(language)
            val documents = languageCollection.get().await().documents
            val questions = documents.mapNotNull {
                it.toObject(QuestionRemote::class.java)
                    ?.toQuizEntity(numQuestion = numQuestion, hardQuestion = hardQuestion, id = 0, idQuiz = idQuiz, language = language)
            }

            questionRemotes.addAll(questions)
        }

        questionRemotes.forEach { it.pathPictureQuestion?.let { downloadPhotoToLocalPath(it) } }
        return questionRemotes
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

        if (event == 1) {
            docRef = docRef
                .document(tpovId.toString())
                .collection(tpovId.toString())
        }

        val questionNumber = if (questionEntity.hardQuestion) "-${questionEntity.numQuestion}"
        else questionEntity.numQuestion.toString()

        docRef = docRef.document(questionNumber).collection(pathLanguage)
        docRef.document(pathLanguage).set(questionEntity.toQuestionRemote()).await()
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

        val questionRemotes = collectionReference
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(QuestionRemote::class.java) }

        questionRemotes.forEach { question ->
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