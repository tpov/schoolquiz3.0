package com.tpov.common.data

import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.common.data.core.Core.tpovId
import com.tpov.common.data.dao.QuestionDetailDao
import com.tpov.common.data.model.local.QuestionDetailEntity
import com.tpov.common.data.model.remote.QuestionDetail
import com.tpov.common.domain.repository.RepositoryQuestionDetail
import kotlinx.coroutines.tasks.await
import javax.inject.Inject


class RepositoryQuestionDetailImpl @Inject constructor(
    private val questionDetailDao: QuestionDetailDao,
    private val firestore: FirebaseFirestore
) : RepositoryQuestionDetail {

    private val baseCollection = firestore.collection("questionsDetail")
    override suspend fun fetchQuestionDetail(
        typeId: Int,
        categoryId: String,
        subcategoryId: String,
        subsubcategoryId: String,
        idQuiz: Int
    ): List<QuestionDetail> {
        val collectionReference = baseCollection
            .document("questionDetail$typeId")
            .collection("questionDetail$typeId")
            .document(idQuiz.toString())
            .collection(idQuiz.toString())
            .document(tpovId.toString())
            .collection(tpovId.toString())

        return collectionReference
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(QuestionDetail::class.java) }
    }

    override suspend fun getQuestionDetailByIdQuiz(idQuiz: Int) = questionDetailDao.getQuestionDetailByIdQuiz(idQuiz)

    override suspend fun saveQuestionDetail(questionDetailEntity: QuestionDetailEntity) {
        questionDetailDao.insertQuestionDetail(questionDetailEntity)
    }

    override suspend fun updateQuestionDetail(questionDetailEntity: QuestionDetailEntity) {
        questionDetailDao.updateQuizDetail(questionDetailEntity)
    }

    override suspend fun deleteQuestionDetailById(id: Int) {
        questionDetailDao.deleteQuestionDetail(id)
    }

    override suspend fun deleteRemoteQuestionDetailByIdQuiz(idQuiz: Int, typeId: Int) {
        val collectionReference = baseCollection
            .document("questionDetail$typeId")
            .collection("questionDetail$typeId")
            .document(idQuiz.toString())
            .collection(idQuiz.toString())
            .document(tpovId.toString())
            .collection(tpovId.toString())

        collectionReference.document(idQuiz.toString()).delete().await()
    }


}