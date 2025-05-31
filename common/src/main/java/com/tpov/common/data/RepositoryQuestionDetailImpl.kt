package com.tpov.common.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.common.Core.tpovId
import com.tpov.common.data.database.QuestionDetailDao
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

    override suspend fun fetchQuestionDetails(path: PathStructure): List<QuestionDetailEntity> {
        Log.d("FirebaseStorage", "fetchQuestionDetails")

        val collectionReference = baseCollection
            .document("questionDetail${path.nameEvent}")
            .collection(
                "${path.nameCategory}_${path.nameSubCategory}_" +
                        "${path.nameSubsubCategory}_${path.nameQuiz}"
            )
            .document("listTpovId")
            .collection(tpovId.toString())

        return try {
            val task = collectionReference.get().await()

            task.documents.mapNotNull { it.toObject(QuestionDetailRemote::class.java)
                ?.toQuestionDetailEntity(path) }
        } catch (e: Exception) {
            Log.w("Firestore", "Error fetching question details", e)
            emptyList()
        }
    }

    override suspend fun pushQuestionDetails(questionDetailEntity: QuestionDetailEntity) {
        Log.d("FirebaseStorage", "pushQuestionDetail")
        val collectionReference = baseCollection
            .document("questionDetail${questionDetailEntity.event}")
            .collection(
                "${questionDetailEntity.category}_${questionDetailEntity.subCategory}_" +
                        "${questionDetailEntity.subsubCategory}_${questionDetailEntity.quiz}"
            )
            .document("listTpovId")
            .collection(tpovId.toString())

        try {
            collectionReference.add(questionDetailEntity.toQuestionDetailRemote()).await()

            questionDetailDao.updateQuizDetail(questionDetailEntity.copy(synth = true))
        } catch (e: Exception) {
            Log.w("Firestore", "Error pushQuestionDetail", e)
        }
    }

    override suspend fun getQuestionDetailByPath(pathStructure: PathStructure) =
        questionDetailDao.getQuestionDetailByPath(pathStructure.nameEvent, pathStructure.nameCategory, pathStructure.nameSubCategory, pathStructure.nameSubsubCategory, pathStructure.nameQuiz)

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
                .document("questionDetail${pathStructure.nameEvent}")
                .collection(
                    "${pathStructure.nameCategory}_${pathStructure.nameSubCategory}_" +
                            "${pathStructure.nameSubsubCategory}_${pathStructure.nameQuiz}"
                )
                .document("listTpovId")
                .collection(tpovId.toString())

            val documents = collectionReference.get().await()

            // Удаляем каждый документ
            documents.forEach { document ->
                document.reference.delete().await()
            }
        } catch (e: Exception) {
            Log.w("Firestore", "Error deleting question details", e)
        }
    }
}
