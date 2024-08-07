package com.tpov.common.data

import com.google.firebase.firestore.FirebaseFirestore
import com.tpov.common.data.core.Core.tpovId
import com.tpov.common.data.dao.QuizDao
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.remote.Quiz
import com.tpov.common.domain.repository.RepositoryQuiz
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class RepositoryQuizImpl @Inject constructor(
    private val quizDao: QuizDao,
    private val firestore: FirebaseFirestore
) : RepositoryQuiz {

    private val baseCollection = firestore.collection("quizzes")

    override suspend fun fetchQuizzes(typeId: Int, categoryId: String, subcategoryId: String, subsubcategoryId: String): List<Quiz> {
        var collectionReference = baseCollection
            .document("quiz$typeId")
            .collection("quiz$typeId")
            .document(categoryId)
            .collection(categoryId)
            .document(subcategoryId)
            .collection(subcategoryId)
            .document(subsubcategoryId)
            .collection(subsubcategoryId)

        if (typeId == 1) {
            collectionReference = collectionReference.document(tpovId.toString()).collection(tpovId.toString())
        }

        return collectionReference
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(Quiz::class.java) }
    }

    override suspend fun getQuizzes() = quizDao.getQuiz()

    override suspend fun insertQuiz(quiz: QuizEntity) {
        quizDao.insertQuiz(quiz)
    }

    override suspend fun saveQuiz(quiz: QuizEntity) {
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

        docRef.document(quiz.id.toString()).set(quiz).await()
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

        if (quiz.event == 1) {
            docRef = docRef.document(quiz.event.toString()).collection(quiz.event.toString())
        }

        docRef.document(quiz.id.toString()).delete().await()
    }
}
