package com.tpov.common.data

import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.common.data.core.Core
import com.tpov.common.data.dao.QuestionDao
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.remote.Question
import com.tpov.common.domain.repository.RepositoryQuestion
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class RepositoryQuestionImpl @Inject constructor(
    private val questionDao: QuestionDao,
    private val firestore: FirebaseFirestore
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

        if (typeId == 1) {
            collectionReference = collectionReference.document(Core.tpovId.toString()).collection(
                Core.tpovId.toString())
        }

        return collectionReference
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Question::class.java) }
    }

    override suspend fun getQuestionByIdQuiz(idQuiz: Int) = questionDao.getQuestionByIdQuiz(idQuiz)

    override suspend fun saveQuestion(questionEntity: QuestionEntity) {
        questionDao.insertQuestion(questionEntity)
    }

    override suspend fun updateQuestion(questionEntity: QuestionEntity) {
        questionDao.updateQuestion(questionEntity)
    }

    override suspend fun deleteQuestionByIdQuiz(idQuiz: Int) {
        questionDao.deleteQuestion(idQuiz)
    }

    override suspend fun deleteRemoteQuestionByIdQuiz(idQuiz: Int, pathLanguage: String, typeId: Int) {
        var collectionReference = baseCollection
            .document("question$typeId")
            .collection("question$typeId")
            .document(idQuiz.toString())
            .collection(idQuiz.toString())
            .document(pathLanguage)
            .collection(pathLanguage)

        if (typeId == 1) {
            collectionReference = collectionReference.document(Core.tpovId.toString()).collection(
                Core.tpovId.toString())
        }

        collectionReference.document(idQuiz.toString()).delete().await()
    }
}