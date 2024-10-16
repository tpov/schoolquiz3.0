package com.tpov.common.data

import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.tpov.common.data.core.FirebaseRequestInterceptor
import com.tpov.common.data.database.QuizDao
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.common.data.model.local.StructureCategoryDataEntity
import com.tpov.common.data.model.local.fromJson
import com.tpov.common.data.model.remote.QuizRemote
import com.tpov.common.domain.repository.RepositoryQuiz
import com.tpov.common.presentation.utils.Values.context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class RepositoryQuizImpl @Inject constructor(
    private val quizDao: QuizDao,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : RepositoryQuiz {

    private val baseCollection = firestore.collection("quizzes")

    override suspend fun fetchQuizzes(
        typeId: Int,
        tpovId: Int,
        idQuiz: Int
    ): QuizRemote {
        Log.d("FirebaseRequestInterceptor", "fetchQuizzes")
        val documentReference = baseCollection
            .document("quiz$typeId")
            .collection("$tpovId")
            .document("$idQuiz")

        val fetchQuizTask = {
            val taskCompletionSource = TaskCompletionSource<QuizRemote>()
            documentReference.get()
                .addOnSuccessListener { documentSnapshot ->
                    val quizRemote = documentSnapshot.toObject(QuizRemote::class.java)
                    if (quizRemote != null) {
                        taskCompletionSource.setResult(quizRemote)
                    } else {
                        taskCompletionSource.setException(
                            NoSuchElementException("No quiz found with the specified criteria")
                        )
                    }
                }
                .addOnFailureListener { e ->
                    taskCompletionSource.setException(e)
                }
            taskCompletionSource.task
        }

        return try {
            val quizRemote = FirebaseRequestInterceptor
                .executeWithChecksSingleTask(fetchQuizTask)
                .await()
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
        Log.d("FirebaseRequestInterceptor", "pushQuiz")
        val quizDocumentRef = firestore.collection("quizzes")
            .document("quiz${quizRemote.event}")
            .collection("${quizRemote.tpovId}")
            .document(idQuiz.toString())

        val pushQuizTask = {
            quizDocumentRef.set(quizRemote.toMap())
        }

        try {
            FirebaseRequestInterceptor
                .executeWithChecksSingleTask(pushQuizTask)
                .await()
            uploadFileToFirebaseStorage(quizRemote.picture)
            Log.d("Firestore", "Quiz успешно сохранен в Firestore.")
        } catch (e: Exception) {
            Log.e("Firestore", "Ошибка при сохранении Quiz в Firestore", e)
            throw e
        }
    }

    override suspend fun pushStructureCategory(
        structureCategoryDataEntity: StructureCategoryDataEntity
    ): StructureCategoryDataEntity {
        Log.d("FirebaseRequestInterceptor", "pushStructureCategory")
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

        Log.d("OkHttp", "Sending request to URL: $url")
        Log.d("OkHttp", "Request body: ${data.toString()}")

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val pushTask = {
            val taskCompletionSource = TaskCompletionSource<StructureCategoryDataEntity>()
            val call = client.newCall(request)

            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    taskCompletionSource.setException(e)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    if (response.isSuccessful) {
                        val responseData = response.body?.string()
                        if (responseData != null) {
                            Log.d("OkHttp", "Received response body: $responseData")
                            val jsonResponse = JSONObject(responseData)
                            val updatedCategory = fromJson(jsonResponse)
                            CoroutineScope(Dispatchers.IO).launch {

                                uploadFileToFirebaseStorage(structureCategoryDataEntity.newCategoryPhoto)
                                uploadFileToFirebaseStorage(structureCategoryDataEntity.newSubCategoryPhoto)
                                uploadFileToFirebaseStorage(structureCategoryDataEntity.newSubsubCategoryPhoto)
                            }
                            taskCompletionSource.setResult(updatedCategory)
                        } else {
                            taskCompletionSource.setException(Exception("Empty response from server"))
                        }
                    } else {
                        taskCompletionSource.setException(Exception("Server error: ${response.code}"))
                    }
                }
            })

            taskCompletionSource.task
        }

        return try {
            FirebaseRequestInterceptor
                .executeWithChecksSingleTask(pushTask)
                .await()  // Ожидаем завершения задачи
        } catch (e: Exception) {
            Log.e("OkHttp", "Request failed", e)
            throw e
        }
    }


    private fun deleteStructureCategory(idCategory: Int) {

    }


    private suspend fun signInAnonymously() {
        return suspendCoroutine<Unit> { continuation ->
            FirebaseAuth.getInstance().signInAnonymously()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("FirebaseAuth", "Анонимный пользователь создан")
                        continuation.resume(Unit)
                    } else {
                        Log.e("FirebaseAuth", "Ошибка анонимной аутентификации", task.exception)
                        continuation.resumeWith(
                            Result.failure(
                                task.exception ?: Exception("Неизвестная ошибка аутентификации")
                            )
                        )
                    }
                }
        }
    }


    private suspend fun uploadFileToFirebaseStorage(pathPhoto: String) {
        Log.d("FirebaseRequestInterceptor", "uploadFileToFirebaseStorage")
        if (pathPhoto.isNotBlank()) {
            val storageRef = FirebaseStorage.getInstance().reference
            val localFile = File(context.filesDir, pathPhoto)
            val photoRef = storageRef.child("quizPhoto/${localFile.name}")

            val uploadTask = {
                val taskCompletionSource = TaskCompletionSource<Void>()
                photoRef.putFile(Uri.fromFile(localFile))
                    .addOnSuccessListener {
                        Log.d("FirebaseStorage", "Файл успешно загружен: ${localFile.name}")
                        taskCompletionSource.setResult(null)
                    }
                    .addOnFailureListener { exception ->
                        // Обработка ошибок
                        Log.e("FirebaseStorage", "Ошибка загрузки файла", exception)
                        taskCompletionSource.setException(exception)
                    }
                taskCompletionSource.task
            }

            try {
                FirebaseRequestInterceptor
                    .executeWithChecksSingleTask(uploadTask)
                    .await()  // Ждём завершения задачи
                Log.d("FirebaseStorage", "Загрузка завершена успешно")
            } catch (e: Exception) {
                Log.e("FirebaseStorage", "Ошибка при загрузке файла: ${e.message}", e)
                throw e
            }
        }
    }


    private suspend fun downloadPhotoToLocalPath(pathPhoto: String): String? {
        Log.d("FirebaseRequestInterceptor", "downloadPhotoToLocalPath")
        if (pathPhoto.isNotBlank()) {
            val storageRef = FirebaseStorage.getInstance().reference
            val photoRef = storageRef.child(pathPhoto)
            val localFile = File(pathPhoto, File(pathPhoto).name)

            val downloadTask = {
                val taskCompletionSource = TaskCompletionSource<File>()
                photoRef.getFile(localFile)
                    .addOnSuccessListener {
                        taskCompletionSource.setResult(localFile)
                    }
                    .addOnFailureListener { exception ->
                        taskCompletionSource.setException(exception)
                    }
                taskCompletionSource.task
            }

            return try {
                val file = FirebaseRequestInterceptor
                    .executeWithChecksSingleTask(downloadTask)
                    .await()
                file.absolutePath  // Возвращаем путь к загруженному файлу
            } catch (e: Exception) {
                Log.e("FirebaseStorage", "Ошибка при загрузке фото: ${e.message}", e)
                throw e
            }
        }
        return null
    }


    override suspend fun deleteQuizById(idQuiz: Int) {
        quizDao.deleteQuizById(idQuiz)
    }

    override suspend fun deleteRemoteQuizById(quizRemote: QuizRemote, idQuiz: Int) {
        Log.d("FirebaseRequestInterceptor", "deleteRemoteQuizById")
        val deleteTask = {
            val taskCompletionSource = TaskCompletionSource<Void>()
            val docRef = baseCollection
                .document("quiz${quizRemote.event}")
                .collection("${quizRemote.tpovId}")
                .document("$idQuiz")

            docRef.delete()
                .addOnSuccessListener {
                    taskCompletionSource.setResult(null)
                }
                .addOnFailureListener { exception ->
                    taskCompletionSource.setException(exception)
                }
            taskCompletionSource.task
        }

        try {
            // Удаляем квиз с помощью перехватчика
            FirebaseRequestInterceptor
                .executeWithChecksSingleTask(deleteTask)
                .await()

            // Если удаление успешно, удаляем картинку
            quizRemote.picture?.let { deletePhotoFromServer(it) }
        } catch (e: Exception) {
            Log.e("Firestore", "Ошибка при удалении квиза: ${e.message}", e)
            throw e
        }
    }

    private suspend fun deletePhotoFromServer(pathPhoto: String) {
        Log.d("FirebaseRequestInterceptor", "deletePhotoFromServer")
        val deletePhotoTask = {
            val taskCompletionSource = TaskCompletionSource<Void>()
            val storageRef = FirebaseStorage.getInstance().reference
            val photoRef = storageRef.child(pathPhoto)

            photoRef.delete()
                .addOnSuccessListener {
                    taskCompletionSource.setResult(null)
                }
                .addOnFailureListener { exception ->
                    taskCompletionSource.setException(exception)
                }
            taskCompletionSource.task
        }

        try {
            // Удаляем фото с помощью перехватчика
            FirebaseRequestInterceptor
                .executeWithChecksSingleTask(deletePhotoTask)
                .await()
        } catch (e: Exception) {
            Log.e("FirebaseStorage", "Ошибка при удалении фото: ${e.message}", e)
            throw e
        }
    }

}
