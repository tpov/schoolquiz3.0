package com.tpov.common.data

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.tpov.common.data.core.Core.tpovId
import com.tpov.common.data.database.QuizDao
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity
import com.tpov.common.data.model.local.fromJson
import com.tpov.common.data.model.remote.QuizRemote
import com.tpov.common.domain.repository.RepositoryQuiz
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class RepositoryQuizImpl @Inject constructor(
    private val quizDao: QuizDao,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : RepositoryQuiz {

    private val baseCollection = firestore.collection("quizzes")

    override suspend fun fetchQuizzes(
        typeId: Int,
        categoryId: Int,
        subcategoryId: Int,
        subsubcategoryId: Int,
        idQuiz: Int
    ): QuizRemote {

        var documentReference = baseCollection
            .document("quiz$typeId")
            .collection("quiz$typeId")
            .document(categoryId.toString())
            .collection(categoryId.toString())
            .document(subcategoryId.toString())
            .collection(subcategoryId.toString())
            .document(subsubcategoryId.toString())
            .collection(subsubcategoryId.toString())
            .document(idQuiz.toString())

        if (typeId == 1) {
            documentReference = documentReference.collection(tpovId.toString()).document(tpovId.toString())
        }

        return try {
            val documentSnapshot = documentReference.get().await()
            val quizRemote = documentSnapshot.toObject(QuizRemote::class.java)
                ?: throw NoSuchElementException("No quiz found with the specified criteria")

            downloadPhotoToLocalPath(quizRemote.picture)

            quizRemote
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    override suspend fun getQuizzes() = quizDao.getQuiz()

    override suspend fun insertQuiz(quiz: QuizEntity) {
        quizDao.insertQuiz(quiz)
    }

    override suspend fun saveQuiz(quiz: QuizEntity) {
        quizDao.insertQuiz(quiz)
    }

    override suspend fun pushQuiz(quizRemote: QuizRemote, idQuiz: Int) {
        // Получаем ссылку на коллекцию "quizzes/quiz${quiz.event}"
        val quizDocumentRef = firestore.collection("quizzes")
            .document("quiz${quizRemote.event}")
            .collection("quiz${quizRemote.event}")
            .document(idQuiz.toString())

        try {
            // Сохраняем или обновляем документ в Firestore
            quizDocumentRef.set(quizRemote.toMap())
                .addOnSuccessListener {
                    uploadPhotoToServer(quizRemote.picture)
                }
                .await()

            Log.d("Firestore", "Quiz успешно сохранен в Firestore.")
        } catch (e: Exception) {
            Log.e("Firestore", "Ошибка при сохранении Quiz в Firestore", e)
            throw e
        }
    }

    override suspend fun pushStructureCategory(
        structureCategoryDataEntity: StructureCategoryDataEntity
    ): StructureCategoryDataEntity {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val url = "https://create-quiz-function-762375057396.us-west3.run.app"
        val data = JSONObject()
        data.put("structureCategory", JSONObject(structureCategoryDataEntity.toMap()))

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = data.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseData = response.body?.string()
                if (responseData != null) {
                    // Парсинг JSON-ответа
                    val jsonResponse = JSONObject(responseData)
                    val updatedCategory = fromJson(jsonResponse)  // Обращение к методу через объект-компаньон
                    Log.d("OkHttp", "Response: $responseData")
                    updatedCategory  // Возвращаем обновленное значение
                } else {
                    Log.e("OkHttp", "Empty response body")
                    throw Exception("Empty response from server")
                }
            } else {
                Log.e("OkHttp", "Server returned error: ${response.code} - ${response.message}")
                throw Exception("Error from server: ${response.code} - ${response.message}")
            }
        } catch (e: SocketTimeoutException) {
            Log.e("OkHttp", "Request timed out", e)
            throw Exception("Request timed out")
        } catch (e: Exception) {
            Log.e("OkHttp", "Request failed", e)
            throw e
        }
    }

    private fun deleteStructureCategory(idCategory: Int) {

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
        val photoRef = storageRef.child("quizPhoto/${localFile.name}")

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


    private suspend fun downloadPhotoToLocalPath(pathPhoto: String) {
        if (pathPhoto.isNotBlank()) {
            val storageRef = storage.reference
            val photoRef = storageRef.child(pathPhoto)
            val localFile = File(pathPhoto, File(pathPhoto).name)

            photoRef.getFile(localFile).await()
            localFile.absolutePath
        }
    }

    override suspend fun deleteQuizById(idQuiz: Int) {
        quizDao.deleteQuizById(idQuiz)
    }

    override suspend fun deleteRemoteQuizById(quizRemote: QuizRemote, idQuiz: Int, categoryId: Int, subcategoryId: Int, subsubcategoryId: Int) {
        var docRef = baseCollection
            .document("quiz${quizRemote.event}")
            .collection("quiz${quizRemote.event}")
            .document(categoryId.toString())
            .collection(categoryId.toString())
            .document(subcategoryId.toString())
            .collection(subcategoryId.toString())
            .document(subsubcategoryId.toString())
            .collection(subsubcategoryId.toString())

        if (quizRemote.event == 1) docRef = docRef.document(quizRemote.event.toString()).collection(quizRemote.event.toString())
        docRef.document(idQuiz.toString()).delete().await()

        quizRemote.picture?.let { deletePhotoFromServer(it) }
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
