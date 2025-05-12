package com.tpov.common.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.functions
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.tpov.common.data.database.QuestionDao
import com.tpov.common.data.manager.FirebaseRequestInterceptor
import com.tpov.common.data.model.local.QuestionEntity
import com.tpov.common.data.model.remote.QuestionRemote
import com.tpov.common.data.model.remote.TranslateRequest
import com.tpov.common.domain.repository.RepositoryQuestion
import com.tpov.common.presentation.model.PathStructure
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class RepositoryQuestionImpl @Inject constructor(
    private val questionDao: QuestionDao,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val context: Context
) : RepositoryQuestion {

    private val functions = Firebase.functions
    private val baseCollection = firestore.collection("questions")
    override suspend fun getAllMustTrnslLangsPaidQuestions(): Set<String> {
        return try {
            val snapshot = firestore
                .collection("variable")
                .document("translateConfig")
                .get()
                .await()

            val languages = snapshot.get("languagesGoogleTranslate") as? List<String>
            languages?.toSet() ?: emptySet()

        } catch (e: Exception) {
            Log.e("Firestore", "Error getting paid translation languages", e)
            emptySet()
        }
    }

    override suspend fun getAllMustTrnslLangsFreeQuestions(): Set<String> {
        return try {
            val snapshot = firestore
                .collection("variable")
                .document("translateConfig")
                .get()
                .await()

            val languages = snapshot.get("languagesFreeTranslate") as? List<String>
            languages?.toSet() ?: emptySet()

        } catch (e: Exception) {
            Log.e("Firestore", "Error getting free translation languages", e)
            emptySet()
        }
    }

    override suspend fun fetchQuestion(
        pathStructure: PathStructure,
        language: String,
    ): List<QuestionEntity> {

        val baseCollectionReference = baseCollection
            .document("question${pathStructure.nameEvent}")
            .collection("${pathStructure.nameCategory}_${pathStructure.nameSubCategory}_${pathStructure.nameSubsubCategory}")

        val questionRemotes = mutableListOf<QuestionEntity>()

        try {
            val task = FirebaseRequestInterceptor.executeWithChecksSingleTask {
                baseCollectionReference.get()
            }.await()

            val questionDocuments = task.documents

            for (questionDocument in questionDocuments) {
                val questionEntity = questionDocument.toObject(QuestionRemote::class.java)
                questionEntity?.let { questionRemotes.add(it.toQuestionEntity(pathStructure)) }
                questionEntity?.pathPictureQuestion?.let { downloadPhotoToLocalPath(it) }
            }
        } catch (e: Exception) {
            Log.w("Firestore", "Error fetching questions", e)
        }

        return questionRemotes
    }

    override suspend fun getQuestionsByPath(path: PathStructure) = questionDao.getQuestionsByPath(
        path.nameEvent,
        path.nameCategory,
        path.nameSubCategory,
        path.nameSubsubCategory,
        path.nameQuiz
    )

    override suspend fun saveQuestion(questionEntity: QuestionEntity) {
        questionDao.insertQuestion(questionEntity)
    }

    override suspend fun pushQuestion(
        questionEntity: QuestionEntity,
        isUpdate: Boolean
    ) {
        val pathStructure = PathStructure(
            questionEntity.event,
            questionEntity.category,
            questionEntity.subCategory,
            questionEntity.subsubCategory,
            questionEntity.quiz
        )
        questionEntity.pathPictureQuestion?.let { uploadPhotoToServer(it) }

        val docRef = baseCollection
            .document("question${pathStructure.nameEvent}")
            .collection("${pathStructure.nameCategory}_${pathStructure.nameSubCategory}_${pathStructure.nameSubsubCategory}")
            .document()

        Log.d("Translation", "docRef: ${docRef.path}")
        try {
            FirebaseRequestInterceptor.executeWithChecksSingleTask {
                docRef.set(questionEntity)
            }.await()
        } catch (e: Exception) {
            Log.w("Firestore", "Error pushing question", e)
        }
    }

    override suspend fun pushQuestionForTranslate(
        question: QuestionEntity,
        usePaidTranslation: Boolean,
        toLang: String,
    ) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val request = TranslateRequest(question, usePaidTranslation, toLang)

        val gson = Gson()
        val jsonBody = gson.toJson(request)

        Log.d("Translation", "Request payload: $jsonBody")

        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url("https://question-translate-762375057396.us-central1.run.app/translate-question")
            .post(requestBody)
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string()
                if (response.isSuccessful) {
                    Log.d("Translation", "Success: $responseBody")
                } else {
                    Log.e("Translation", "Error ${response.code}: $responseBody")
                }
            }
        } catch (e: Exception) {
            Log.e("Translation", "Network error", e)
        }
    }

    override suspend fun remoteLangsQuestions(
        questionEntity: QuestionEntity
    ): List<String> = suspendCoroutine { continuation ->
        val languages = mutableListOf<String>()

        baseCollection
            .document("question${questionEntity.event}")
            .collection("${questionEntity.category}_${questionEntity.subCategory}_${questionEntity.subsubCategory}")
            .whereEqualTo("hardQuestion", questionEntity.hardQuestion)
            .whereEqualTo("numQuestion", questionEntity.numQuestion)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    document.getString("language")?.let {
                        languages.add(it)
                    }
                }
                continuation.resume(languages)
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }

    override suspend fun updateQuestion(questionEntity: QuestionEntity) {
        questionDao.updateQuestion(questionEntity)
    }

    override suspend fun deleteQuestionByPath(path: PathStructure) {
        questionDao.deleteQuestion(path.nameEvent, path.nameCategory, path.nameSubCategory, path.nameSubsubCategory, path.nameQuiz)
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
            storageRef.putFile(Uri.parse(pathPhoto))
        }.addOnSuccessListener {
            Log.d("FirebaseStorage", "Фото загружено успешно")
        }.addOnFailureListener {
            Log.e("FirebaseStorage", "Ошибка при загрузке фото", it)
        }
    }


    private suspend fun downloadPhotoToLocalPath(pathPhoto: String): String? {
        Log.d("PhotoDebug", "Starting download: $pathPhoto")

        if (pathPhoto.isBlank()) {
            Log.d("PhotoDebug", "Path is blank")
            return null
        }

        // Убедимся что путь содержит только имя файла
        val fileName = if (pathPhoto.contains("/")) {
            pathPhoto.substringAfterLast("/")
        } else {
            pathPhoto
        }

        val photoDir = File(context.filesDir, "questionPhoto").apply {
            if (!exists()) {
                val created = mkdirs()
                Log.d("PhotoDebug", "Created directory: $created")
            }
        }

        val localFile = File(photoDir, fileName)
        Log.d("PhotoDebug", "Local file path: ${localFile.absolutePath}")

        try {
            val storageRef = storage.reference
            // Используем полный путь к файлу в Storage
            val photoRef = storageRef.child("questionPhoto/$fileName")
            Log.d("PhotoDebug", "Storage reference created for: questionPhoto/$fileName")

            // Загружаем файл
            FirebaseRequestInterceptor.executeWithChecksSingleTask {
                photoRef.getFile(localFile)
            }.await()

            if (localFile.exists() && localFile.length() > 0) {
                Log.d("PhotoDebug", "File downloaded successfully: ${localFile.length()} bytes")
                return localFile.absolutePath
            } else {
                Log.e(
                    "PhotoDebug",
                    "File download failed: exists=${localFile.exists()}, size=${localFile.length()}"
                )
                return null
            }
        } catch (e: Exception) {
            Log.e("PhotoDebug", "Download error", e)
            return null
        }
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
