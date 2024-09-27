package com.tpov.common.data

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.tpov.common.data.database.QuestionDao
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
        categoryId: Int,
        subcategoryId: Int,
        language: String,
        idQuiz: Int
    ): List<QuestionRemote> {

        val baseCollectionReference = baseCollection
            .document("question$event")
            .collection(idQuiz.toString())

        val questionRemotes = mutableListOf<QuestionRemote>()
        val questionDocuments = baseCollectionReference.get().await().documents

        for (questionDocument in questionDocuments) {
            val questionEntity = questionDocument.toObject(QuestionRemote::class.java)
            Log.d("fetchQuestion", "questionEntity: ${questionEntity?.nameQuestion}")
            questionEntity?.let { questionRemotes.add(it) }
            questionEntity?.pathPictureQuestion?.let { downloadPhotoToLocalPath(it) }
        }

        return questionRemotes
    }

    override suspend fun getQuestionByIdQuiz(idQuiz: Int) = questionDao.getQuestionByIdQuiz(idQuiz)

    override suspend fun saveQuestion(questionEntity: QuestionEntity) {
        questionDao.insertQuestion(questionEntity)
    }

    override suspend fun pushQuestion(questionEntity: QuestionRemote, event: Int, idQuiz: Int) {
        questionEntity.pathPictureQuestion?.let { uploadPhotoToServer(it) }

        val docRef = baseCollection
            .document("question${event}")
            .collection(idQuiz.toString())
            .document()

        docRef.set(questionEntity).await()
    }

    override suspend fun updateQuestion(questionEntity: QuestionEntity) {
        questionDao.updateQuestion(questionEntity)
    }

    override suspend fun deleteQuestionByIdQuiz(idQuiz: Int) {
        questionDao.deleteQuestion(idQuiz)
    }

    override suspend fun deleteRemoteQuestionByIdQuiz( idQuiz: Int, event: Int,) {
        // Устанавливаем ссылку на коллекцию вопросов по idQuiz
        val baseCollectionReference = baseCollection
            .document("question$event") // Коллекция с событием
            .collection(idQuiz.toString()) // Папка с idQuiz

        // Получаем все документы в коллекции
        val questionDocuments = baseCollectionReference.get().await().documents

        for (document in questionDocuments) {
            val questionEntity = document.toObject(QuestionEntity::class.java)

            // Если есть путь к изображению, удаляем его из Storage
            questionEntity?.pathPictureQuestion?.let { path ->
                val photoRef = storage.reference.child(path)
                try {
                    photoRef.delete().await() // Удаление фото
                } catch (e: Exception) {
                    println("Ошибка при удалении изображения: $path")
                }
            }

            document.reference.delete().await()
        }
    }


    private fun uploadPhotoToServer(pathPhoto: String) {
        if (pathPhoto.isNotBlank()) {
            // Проверка на авторизацию
            val currentUser: FirebaseUser? = FirebaseAuth.getInstance().currentUser

            if (currentUser == null) {
                // Если пользователь не авторизован, то авторизуем анонимно
                FirebaseAuth.getInstance().signInAnonymously()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // Успешная анонимная аутентификация
                            Log.d("FirebaseAuth", "Анонимный пользователь создан")
                            // Продолжаем загрузку файла после аутентификации
                            uploadFileToFirebaseStorage(pathPhoto)
                        } else {
                            // Обработка ошибки аутентификации
                            Log.e("FirebaseAuth", "Ошибка анонимной аутентификации", task.exception)
                        }
                    }
            } else {
                // Если пользователь уже авторизован, продолжаем загрузку
                uploadFileToFirebaseStorage(pathPhoto)
            }
        }
    }

    private fun uploadFileToFirebaseStorage(pathPhoto: String) {
        val storageRef = FirebaseStorage.getInstance().reference
        val localFile = File(pathPhoto)
        val photoRef = storageRef.child("questionPhoto/${localFile.name}")

        photoRef.putFile(Uri.fromFile(localFile))
            .addOnSuccessListener {
                // Файл успешно загружен
                Log.d("FirebaseStorage", "Файл успешно загружен: ${localFile.name}")
            }
            .addOnFailureListener { exception ->
                // Обработка ошибок
                Log.e("FirebaseStorage", "Ошибка загрузки файла", exception)
            }
    }


    private suspend fun downloadPhotoToLocalPath(pathPhoto: String): String? {

        return if (pathPhoto.isNotBlank()) {
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
        } else ""
    }

    private suspend fun deletePhotoFromServer(pathPhoto: String) {

        if (pathPhoto.isNotBlank()) {
            val storageRef = storage.reference
            val photoRef = storageRef.child(pathPhoto)

            try {
                photoRef.delete().await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}