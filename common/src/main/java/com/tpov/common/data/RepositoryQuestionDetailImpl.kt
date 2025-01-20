package com.tpov.common.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.common.Core.tpovId
import com.tpov.common.data.database.QuestionDetailDao
import com.tpov.common.data.manager.FirebaseRequestInterceptor
import com.tpov.common.data.model.local.QuestionDetailEntity
import com.tpov.common.data.model.remote.QuestionDetailRemote
import com.tpov.common.domain.repository.RepositoryQuestionDetail
import com.tpov.common.presentation.model.PathStructure
import kotlinx.coroutines.tasks.await
import javax.inject.Inject


class RepositoryQuestionDetailImpl @Inject constructor(
    private val questionDetailDao: QuestionDetailDao,
    private val firestore: FirebaseFirestore
) : RepositoryQuestionDetail {

    private val baseCollection = firestore.collection("questionsDetail")

    override suspend fun fetchQuestionDetails(
        pathStructure: PathStructure
    ): List<QuestionDetailEntity> {
        Log.d("FirebaseRequestInterceptor", "fetchQuestionDetails")

        val collectionReference = baseCollection
            .document("questionDetail${pathStructure.idEvent}")
            .collection(
                "${pathStructure.idCategory}_${pathStructure.idSubCategory}_" +
                        "${pathStructure.idSubsubCategory}_${pathStructure.idQuiz}"
            )
            .document("listTpovId")
            .collection(tpovId.toString())

        return try {
            val task = FirebaseRequestInterceptor.executeWithChecksSingleTask {
                collectionReference.get()
            }.await()

            task.documents.mapNotNull { it.toObject(QuestionDetailRemote::class.java)
                ?.toQuestionDetailEntity(pathStructure) }
        } catch (e: Exception) {
            Log.w("Firestore", "Error fetching question details", e)
            emptyList()
        }
    }

    override suspend fun pushQuestionDetails(questionDetailEntity: QuestionDetailEntity) {
        Log.d("FirebaseRequestInterceptor", "pushQuestionDetail")
        val collectionReference = baseCollection
            .document("questionDetail${questionDetailEntity.idEvent}")
            .collection(
                "${questionDetailEntity.idCategory}_${questionDetailEntity.idSubCategory}_" +
                        "${questionDetailEntity.idSubsubCategory}_${questionDetailEntity.idQuiz}"
            )
            .document("listTpovId")
            .collection(tpovId.toString())

        try {
            FirebaseRequestInterceptor.executeWithChecksSingleTask {
                collectionReference.add(questionDetailEntity.toQuestionDetailRemote())
            }.await()

            questionDetailDao.updateQuizDetail(
                questionDetailDao.getQuestionDetail(questionDetailEntity.idQuiz).copy(synth = true)
            )
        } catch (e: Exception) {
            Log.w("Firestore", "Error pushQuestionDetail", e)
        }
    }

    override suspend fun getQuestionDetailByPath(pathStructure: PathStructure) =
        questionDetailDao.getQuestionDetailByPath(pathStructure.idEvent, pathStructure.idCategory, pathStructure.idSubCategory, pathStructure.idSubsubCategory, pathStructure.idQuiz)

    override suspend fun saveQuestionDetail(questionDetailEntity: QuestionDetailEntity) {
        questionDetailDao.insertQuestionDetail(questionDetailEntity)
    }

    override suspend fun updateQuestionDetail(questionDetailEntity: QuestionDetailEntity) {
        questionDetailDao.updateQuizDetail(questionDetailEntity)
    }

    override suspend fun deleteQuestionDetailById(id: Int) {
        questionDetailDao.deleteQuestionDetail(id)
    }

    override suspend fun deleteRemoteQuestionDetailByPath(pathStructure: PathStructure) {
        try {
            val collectionReference = baseCollection
                .document("questionDetail${pathStructure.idEvent}")
                .collection(
                    "${pathStructure.idCategory}_${pathStructure.idSubCategory}_" +
                            "${pathStructure.idSubsubCategory}_${pathStructure.idQuiz}"
                )
                .document("listTpovId")
                .collection(tpovId.toString())

            val documents = collectionReference.get().await()

            // Удаляем каждый документ
            documents.forEach { document ->
                FirebaseRequestInterceptor.executeWithChecksSingleTask {
                    document.reference.delete()
                }.await()
            }
        } catch (e: Exception) {
            Log.w("Firestore", "Error deleting question details", e)
        }
    }
}