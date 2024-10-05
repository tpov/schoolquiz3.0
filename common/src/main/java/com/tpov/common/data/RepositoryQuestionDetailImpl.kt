package com.tpov.common.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.common.data.core.Core.tpovId
import com.tpov.common.data.core.FirebaseRequestInterceptor
import com.tpov.common.data.database.QuestionDetailDao
import com.tpov.common.data.model.local.QuestionDetailEntity
import com.tpov.common.data.model.remote.QuestionDetailRemote
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
    ): List<QuestionDetailRemote> {
        val collectionReference = baseCollection
            .document("questionDetail$typeId")
            .collection("questionDetail$typeId")
            .document(idQuiz.toString())
            .collection(idQuiz.toString())
            .document(tpovId.toString())
            .collection(tpovId.toString())

        return try {
            val task = FirebaseRequestInterceptor.executeWithChecksSingleTask {
                collectionReference.get()
            }.await()

            task.documents.mapNotNull { it.toObject(QuestionDetailRemote::class.java) }
        } catch (e: Exception) {
            Log.w("Firestore", "Error fetchQuestionDetail", e)
            emptyList()
        }
    }


    override suspend fun pushQuestionDetail(
        id: Int,
        event: Int,
        categoryId: String,
        subcategoryId: String,
        subsubcategoryId: String,
        idQuiz: Int,
        questionDetailRemote: QuestionDetailRemote
    ) {
        val collectionReference = baseCollection
            .document("questionDetail$event")
            .collection("questionDetail$event")
            .document(idQuiz.toString())
            .collection(idQuiz.toString())
            .document(tpovId.toString())
            .collection(tpovId.toString())

        try {
            FirebaseRequestInterceptor.executeWithChecksSingleTask {
                collectionReference.add(questionDetailRemote)
            }.await()

            questionDetailDao.updateQuizDetail(
                questionDetailDao.getQuestionDetail(id).copy(synth = true)
            )
        } catch (e: Exception) {
            Log.w("Firestore", "Error pushQuestionDetail", e)
        }
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

        try {
            FirebaseRequestInterceptor.executeWithChecksSingleTask {
                collectionReference.document(idQuiz.toString()).delete()
            }.await()
        } catch (e: Exception) {
            Log.w("Firestore", "Error deleting remote question detail", e)
        }
    }
}