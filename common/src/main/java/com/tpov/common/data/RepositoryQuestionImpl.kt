package com.tpov.common.data

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.tpov.common.data.core.FirebaseRequestInterceptor
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

        try {
            val task = FirebaseRequestInterceptor.executeWithChecksSingleTask {
                baseCollectionReference.get()
            }.await()

            val questionDocuments = task.documents

            for (questionDocument in questionDocuments) {
                val questionEntity = questionDocument.toObject(QuestionRemote::class.java)
                Log.d("fetchQuestion", "questionEntity: ${questionEntity?.nameQuestion}")
                questionEntity?.let { questionRemotes.add(it) }
                questionEntity?.pathPictureQuestion?.let { downloadPhotoToLocalPath(it) }
            }
        } catch (e: Exception) {
            Log.w("Firestore", "Error fetching questions", e)
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

        try {
            FirebaseRequestInterceptor.executeWithChecksSingleTask {
                docRef.set(questionEntity)
            }.await()
        } catch (e: Exception) {
            Log.w("Firestore", "Error pushing question", e)
        }
    }

    override suspend fun updateQuestion(questionEntity: QuestionEntity) {
        questionDao.updateQuestion(questionEntity)
    }

    override suspend fun deleteQuestionByIdQuiz(idQuiz: Int) {
        questionDao.deleteQuestion(idQuiz)
    }

    override suspend fun deleteRemoteQuestionByIdQuiz(idQuiz: Int, event: Int) {
        Log.d("FirebaseRequestInterceptor", "deleteRemoteQuestionByIdQuiz")
        val baseCollectionReference = baseCollection
            .document("question$event")
            .collection(idQuiz.toString())

        try {
            // Используем перехватчик для получения всех документов в коллекции
            val task = FirebaseRequestInterceptor.executeWithChecksSingleTask {
                baseCollectionReference.get()
            }.await()

            val questionDocuments = task.documents

            for (document in questionDocuments) {
                val questionEntity = document.toObject(QuestionEntity::class.java)

                // Если есть путь к изображению, удаляем его из Storage
                questionEntity?.pathPictureQuestion?.let { path ->
                    val photoRef = storage.reference.child(path)
                    try {
                        FirebaseRequestInterceptor.executeWithChecksSingleTask {
                            photoRef.delete()
                        }.await() // Удаление фото через перехватчик
                    } catch (e: Exception) {
                        Log.e("Firestore", "Ошибка при удалении изображения: $path", e)
                    }
                }

                // Удаление документа
                FirebaseRequestInterceptor.executeWithChecksSingleTask {
                    document.reference.delete()
                }.await()
            }
        } catch (e: Exception) {
            Log.w("Firestore", "Error deleting remote questions", e)
        }
    }

    private fun uploadPhotoToServer(pathPhoto: String) {
        if (pathPhoto.isNotBlank()) {
            // Проверка на авторизацию
            val currentUser: FirebaseUser? = FirebaseAuth.getInstance().currentUser

            if (currentUser == null) {
                // Если пользователь не авторизован, авторизуем анонимно
                FirebaseAuth.getInstance().signInAnonymously()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("FirebaseAuth", "Анонимный пользователь создан")
                            // Продолжаем загрузку файла после успешной аутентификации
                            uploadFileWithInterceptor(pathPhoto)
                        } else {
                            Log.e("FirebaseAuth", "Ошибка анонимной аутентификации", task.exception)
                        }
                    }
            } else {
                // Если пользователь уже авторизован, продолжаем загрузку
                uploadFileWithInterceptor(pathPhoto)
            }
        }
    }

    private fun uploadFileWithInterceptor(pathPhoto: String) {
        Log.d("FirebaseRequestInterceptor", "uploadFileWithInterceptor")
        FirebaseRequestInterceptor.executeWithChecksSingleTask {
            val storageRef = storage.reference.child(pathPhoto)
            storageRef.putFile(Uri.parse(pathPhoto)) // Загружаем фото на сервер
        }.addOnSuccessListener {
            Log.d("FirebaseStorage", "Фото загружено успешно")
        }.addOnFailureListener {
            Log.e("FirebaseStorage", "Ошибка при загрузке фото", it)
        }
    }



    private suspend fun downloadPhotoToLocalPath(pathPhoto: String): String? {
        Log.d("FirebaseRequestInterceptor", "downloadPhotoToLocalPath")
        return if (pathPhoto.isNotBlank()) {
            val storageRef = storage.reference
            val photoRef = storageRef.child(pathPhoto)
            val localFile = File(pathPhoto, File(pathPhoto).name)

            try {
                FirebaseRequestInterceptor.executeWithChecksSingleTask {
                    photoRef.getFile(localFile)
                }.await()

                localFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else ""
    }


    private suspend fun deletePhotoFromServer(pathPhoto: String) {
        Log.d("FirebaseRequestInterceptor", "deletePhotoFromServer")
        if (pathPhoto.isNotBlank()) {
            val storageRef = storage.reference
            val photoRef = storageRef.child(pathPhoto)

            try {
                FirebaseRequestInterceptor.executeWithChecksSingleTask {
                    photoRef.delete()
                }.await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

}