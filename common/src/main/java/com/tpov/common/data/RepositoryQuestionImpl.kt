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
import com.tpov.common.data.model.entity.QuestionEntity
import com.tpov.common.data.model.local.QuestionLocal
import com.tpov.common.data.model.remote.QuestionRemote
import com.tpov.common.data.model.remote.TranslateRequest
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.repository.RepositoryQuestion
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.utils.LanguageUtils
import com.tpov.common.presentation.utils.LanguageUtils.Companion.toLanguageUtils
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

/**
 * Repository implementation for Question data management with Firebase Storage integration.
 *
 * NEW FLAT STORAGE STRUCTURE:
 * Uses flat paths in format: nameEvent/category>subCategory>subsubCategory>quiz_fileType_fileName
 *
 * Examples:
 * - "QUIZ_HOME/Математика>Алгебра>Уравнения>quiz1_question_123_image.jpg"
 * - "QUIZ_ARENA/Физика>Механика>Законы>quiz2_question_456_diagram.png"
 * - "QUIZ_USER/История>Древний мир>Египет>quiz3_question_789_map.jpg"
 *
 * Benefits:
 * - Single Storage operation per file
 * - Direct file access without hierarchy traversal
 * - Cost-effective (fewer Firebase operations)
 * - Simple URL structure for CDN optimization
 */

class RepositoryQuestionImpl @Inject constructor(
    private val questionDao: QuestionDao,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val context: Context
) : RepositoryQuestion {

    private val functions = Firebase.functions
    private val baseCollection = firestore.collection("questions")

    /**
     * Sanitizes path components to prevent Firestore path parsing issues.
     * Replaces forward slashes with underscores to avoid creating invalid collection references.
     */
    private fun sanitizePathComponent(component: String): String {
        return component.replace("/", "_")
    }

    /**
     * Creates a sanitized collection name from path structure components.
     */
    private fun createSanitizedCollectionName(pathStructure: PathStructure): String {
        val sanitizedCategory = sanitizePathComponent(pathStructure.nameCategory)
        val sanitizedSubCategory = sanitizePathComponent(pathStructure.nameSubCategory)
        val sanitizedSubsubCategory = sanitizePathComponent(pathStructure.nameSubsubCategory)
        return "${sanitizedCategory}|${sanitizedSubCategory}|${sanitizedSubsubCategory}"
    }

    /**
     * Creates a flat storage path in format: nameEvent/category>subCategory>subsubCategory>quiz
     * Example: "QUIZ_HOME/Математика>Алгебра>Уравнения>quiz1"
     */
    private fun createStoragePath(pathStructure: PathStructure, fileType: String = "", fileName: String = ""): String {
        val pathComponents = mutableListOf<String>()

        // Add non-empty path components with '>' separator
        if (pathStructure.nameCategory.isNotBlank()) pathComponents.add(pathStructure.nameCategory)
        if (pathStructure.nameSubCategory.isNotBlank()) pathComponents.add(pathStructure.nameSubCategory)
        if (pathStructure.nameSubsubCategory.isNotBlank()) pathComponents.add(pathStructure.nameSubsubCategory)
        if (pathStructure.nameQuiz.isNotBlank()) pathComponents.add(pathStructure.nameQuiz)

        val hierarchyPath = pathComponents.joinToString(">")
        val basePath = "${pathStructure.nameEvent}/$hierarchyPath"

        return when {
            fileName.isNotBlank() && fileType.isNotBlank() -> "${basePath}_${fileType}_$fileName"
            fileType.isNotBlank() -> "${basePath}_$fileType"
            fileName.isNotBlank() -> "${basePath}_$fileName"
            else -> basePath
        }
    }

    /**
     * Creates a storage path for question images
     */
    private fun createQuestionImagePath(pathStructure: PathStructure, questionId: String, imageFileName: String): String {
        return createStoragePath(pathStructure, "question_${questionId}", imageFileName)
    }

    /**
     * Creates structured question document ID in format: difficulty_language_question_XXX
     * Examples: "hard_ru_question_001", "easy_en_question_042"
     */
    private fun createStructuredQuestionId(
        difficulty: String,
        language: String,
        questionNumber: Int
    ): String {
        return "${difficulty}_${language}_question_${questionNumber.toString().padStart(3, '0')}"
    }

    /**
     * Parses structured question ID to extract filters
     */
    private fun parseStructuredQuestionId(questionId: String): QuestionIdInfo? {
        return try {
            val parts = questionId.split("_")
            if (parts.size >= 4 && parts[2] == "question") {
                QuestionIdInfo(
                    difficulty = parts[0],
                    language = parts[1],
                    questionNumber = parts[3].toIntOrNull() ?: 0
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Data class for parsed question ID information
     */
    private data class QuestionIdInfo(
        val difficulty: String,
        val language: String,
        val questionNumber: Int
    )

    /**
     * OPTIMIZED: Creates storage paths with smart filtering support
     * Uses single file with metadata instead of separate categories per filter
     */
    private fun createOptimizedStoragePath(
        pathStructure: PathStructure,
        fileType: String,
        language: String? = null,
        difficulty: String? = null
    ): String {
        val basePath = createStoragePath(pathStructure)

        return when (fileType) {
            "questions_bundle" -> {
                // Single bundle file with all questions + metadata for filtering
                // Example: "QUIZ_HOME/Математика>Алгебра>quiz1_questions_bundle.json"
                "${basePath}_questions_bundle.json"
            }
            "questions_index" -> {
                // Lightweight index file for quick filtering
                // Example: "QUIZ_HOME/Математика>Алгебра>quiz1_index.json"
                "${basePath}_index.json"
            }
            "questions_by_lang" -> {
                // Language-specific files (only if really needed)
                // Example: "QUIZ_HOME/Математика>Алгебра>quiz1_questions_ru.json"
                "${basePath}_questions_${language ?: "all"}.json"
            }
            else -> createStoragePath(pathStructure, fileType)
        }
    }

    /**
     * COST-EFFECTIVE: Download strategy with smart filtering
     * 1. Download lightweight index first (1 operation)
     * 2. Filter locally to determine what to download
     * 3. Download only needed content (minimal operations)
     */
    private suspend fun downloadQuestionsWithFilters(
        pathStructure: PathStructure,
        requiredLanguage: String? = null,
        requiredDifficulty: String? = null
    ): List<QuestionLocal> {
        try {
            // Step 1: Download index file (1 Firebase operation)
            val indexPath = createOptimizedStoragePath(pathStructure, "questions_index")
            val indexContent = downloadTextFile(indexPath)

            if (indexContent != null) {
                val indexData = parseQuestionsIndex(indexContent)

                // Step 2: Check what we need based on filters
                val neededLanguages = requiredLanguage?.let { listOf(it) }
                    ?: indexData.availableLanguages
                val neededFiles = determineRequiredFiles(indexData, neededLanguages, requiredDifficulty)

                // Step 3: Download only required files
                return downloadSpecificQuestionFiles(pathStructure, neededFiles)
            } else {
                // Fallback: download bundle file (1 operation) and filter locally
                val bundlePath = createOptimizedStoragePath(pathStructure, "questions_bundle")
                val bundleContent = downloadTextFile(bundlePath)
                return bundleContent?.let { parseAndFilterQuestions(it, requiredLanguage, requiredDifficulty) } ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e("RepositoryQuestionImpl", "Error downloading filtered questions", e)
            return emptyList()
        }
    }

    override suspend fun getAllMustTrnslLangsPaidQuestions(): Set<LanguageUtils> {
        return try {
            val snapshot = firestore
                .collection("variable")
                .document("translateConfig")
                .get()
                .await()

            val languages = snapshot.get("languagesGoogleTranslate") as? List<String>
            languages?.map { it.toLanguageUtils() }?.toSet() ?: emptySet()

        } catch (e: Exception) {
            Log.e("Firestore", "Error getting paid translation languages", e)
            emptySet()
        }
    }

    override suspend fun getAllMustTrnslLangsFreeQuestions(): Set<LanguageUtils> {
        return try {
            val snapshot = firestore
                .collection("variable")
                .document("translateConfig")
                .get()
                .await()

            val languages = snapshot.get("languagesFreeTranslate") as? List<String>
            languages?.map { it.toLanguageUtils() }?.toSet() ?: emptySet()
        } catch (e: Exception) {
            Log.e("Firestore", "Error getting free translation languages", e)
            emptySet()
        }
    }

    override suspend fun fetchQuestion(pathStructure: PathStructure, language: List<LanguageUtils>): List<QuestionLocal> {

        val collectionName = createSanitizedCollectionName(pathStructure)
        val baseCollectionReference = baseCollection
            .document("${EventQuiz.fromInput(pathStructure.nameEvent)}")
            .collection(collectionName)

        val questionLocal = mutableListOf<QuestionLocal>()

        try {
            val task = baseCollectionReference.get().await()

            val questionDocuments = task.documents

            for (questionDocument in questionDocuments) {
                val questionRemote = questionDocument.toObject(QuestionRemote::class.java)
                questionRemote?.let { questionLocal.add(it.toQuestionEntity(pathStructure).toQuestionLocal()) }
                questionRemote?.pathPictureQuestion?.let { downloadPhotoToLocalPath(it) }
            }
        } catch (e: Exception) {
            Log.w("Firestore", "Error fetching questions", e)
        }

        return questionLocal
    }

    /**
     * OPTIMIZED: Fetch questions using structured IDs with filters
     * Much faster than scanning all documents with whereEqualTo
     */
    suspend fun fetchQuestionsWithStructuredIds(
        pathStructure: PathStructure,
        difficulty: String? = null,
        language: String? = null,
        limit: Int = 20
    ): List<QuestionLocal> {

        val collectionName = createSanitizedCollectionName(pathStructure)
        val baseCollectionReference = baseCollection
            .document("question${pathStructure.nameEvent}")
            .collection(collectionName)

        val questionLocal = mutableListOf<QuestionLocal>()

        try {
            val query = when {
                // Both filters specified
                difficulty != null && language != null -> {
                    val prefix = "${difficulty}_${language}_question_"
                    baseCollectionReference
                        .orderBy(com.google.firebase.firestore.FieldPath.documentId())
                        .startAt(prefix)
                        .endAt("$prefix\uf8ff")
                        .limit(limit.toLong())
                }
                // Only difficulty specified
                difficulty != null -> {
                    baseCollectionReference
                        .orderBy(com.google.firebase.firestore.FieldPath.documentId())
                        .startAt("${difficulty}_")
                        .endAt("${difficulty}_\uf8ff")
                        .limit(limit.toLong())
                }
                // Only language specified
                language != null -> {
                    // Need to check both easy_ and hard_ prefixes
                    baseCollectionReference
                        .orderBy(com.google.firebase.firestore.FieldPath.documentId())
                        .limit((limit * 2).toLong()) // Get more to filter locally
                }
                // No filters
                else -> {
                    baseCollectionReference.limit(limit.toLong())
                }
            }

            val documents = query.get().await().documents

            for (document in documents) {
                // If only language filter, need to check document ID
                if (difficulty == null && language != null) {
                    val questionId = document.id
                    val idInfo = parseStructuredQuestionId(questionId)
                    if (idInfo?.language != language) continue
                }

                val questionRemote = document.toObject(QuestionRemote::class.java)
                questionRemote?.let {
                    questionLocal.add(it.toQuestionEntity(pathStructure).toQuestionLocal())
                    // Download image if exists
                    it.pathPictureQuestion?.let { imagePath ->
                        downloadPhotoToLocalPath(imagePath)
                    }
                }

                if (questionLocal.size >= limit) break
            }

            Log.d("RepositoryQuestionImpl",
                "Fetched ${questionLocal.size} questions with filters: difficulty=$difficulty, language=$language")

        } catch (e: Exception) {
            Log.w("Firestore", "Error fetching questions with structured IDs", e)
        }

        return questionLocal
    }

    override suspend fun getQuestionsByPath(path: PathStructure) = questionDao.getQuestionsByPath(
        path.nameEvent,
        path.nameCategory,
        path.nameSubCategory,
        path.nameSubsubCategory,
        path.nameQuiz
    ).map { it.toQuestionLocal() }


    override suspend fun saveQuestion(questionLocal: QuestionLocal) {
        questionDao.insertQuestion(questionLocal.toQuestionEntity())
    }

        override suspend fun pushQuestion(questionLocal: QuestionLocal, isUpdate: Boolean) {
        val questionEntity = questionLocal.toQuestionEntity()
        val pathStructure = PathStructure(
            questionEntity.eventName,
            questionEntity.categoryName,
            questionEntity.subCategoryName,
            questionEntity.subsubCategoryName,
            questionEntity.quizName
        )

        val collectionName = createSanitizedCollectionName(pathStructure)

        // Создаем структурированный ID для документа
        val structuredId = createStructuredQuestionId(
            difficulty = if (questionEntity.hardQuestion) "hard" else "easy",
            language = questionEntity.language,
            questionNumber = questionEntity.numQuestion
        )

        val docRef = baseCollection
            .document("question${pathStructure.nameEvent}")
            .collection(collectionName)
            .document(structuredId)  // Используем структурированный ID вместо случайного

        // Обновляем путь к картинке в Firebase Storage с новой плоской структурой
        val updatedQuestionEntity = if (questionEntity.pathPictureQuestion?.isNotBlank() == true) {
            val localPhotoPath = questionEntity.pathPictureQuestion!!

            // Загружаем файл в новую структуру Storage
            uploadPhotoToServer(localPhotoPath, pathStructure, structuredId)

            // Обновляем путь в entity на новый Storage путь
            val fileName = File(localPhotoPath).name
            val newStoragePath = createQuestionImagePath(pathStructure, structuredId, fileName)
            questionEntity.copy(pathPictureQuestion = newStoragePath)
        } else {
            questionEntity
        }

        Log.d("Translation", "docRef: ${docRef.path}")
        Log.d("Translation", "Structured ID: $structuredId")
        Log.d("Storage", "Updated storage path: ${updatedQuestionEntity.pathPictureQuestion}")

        try {
            docRef.set(updatedQuestionEntity).await()
        } catch (e: Exception) {
            Log.w("Firestore", "Error pushing question", e)
        }
    }

    override suspend fun pushQuestionForTranslate(
        questionLocal: QuestionLocal,
        usePaidTranslation: Boolean,
        toLang: LanguageUtils
    ) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val request = TranslateRequest(questionLocal.toQuestionEntity(), usePaidTranslation, toLang.code)

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

    override suspend fun remoteLangsQuestions(questionLocal: QuestionLocal) =
        suspendCoroutine<List<LanguageUtils>> { continuation ->
            val questionEntity = questionLocal.toQuestionEntity()
            val pathStructure = PathStructure(
                questionEntity.eventName,
                questionEntity.categoryName,
                questionEntity.subCategoryName,
                questionEntity.subsubCategoryName,
                questionEntity.quizName
            )
            val collectionName = createSanitizedCollectionName(pathStructure)
            val languages = mutableListOf<LanguageUtils>()

            baseCollection
                .document("question${questionEntity.eventName}")
                .collection(collectionName)
                .whereEqualTo("hardQuestion", questionEntity.hardQuestion)
                .whereEqualTo("numQuestion", questionEntity.numQuestion)
                .get()
                .addOnSuccessListener { documents ->
                    for (document in documents) {
                        document.getString("language")?.let {
                            languages.add(it.toLanguageUtils())
                        }
                    }
                    continuation.resume(languages)
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }

    override suspend fun updateQuestion(questionLocal: QuestionLocal) {
        questionDao.updateQuestion(questionLocal.toQuestionEntity())
    }

    override suspend fun deleteQuestionByPath(path: PathStructure) {
        questionDao.deleteQuestion(
            path.nameEvent,
            path.nameCategory,
            path.nameSubCategory,
            path.nameSubsubCategory,
            path.nameQuiz
        )
    }

    override suspend fun deleteRemoteQuestionByIdQuiz(idQuiz: Int, event: Int) {
        Log.d("RepositoryQuestionImpl", "deleteRemoteQuestionByIdQuiz")
        val baseCollectionReference = baseCollection
            .document("question$event")
            .collection(idQuiz.toString())

        try {
            val task = baseCollectionReference.get().await()

            val questionDocuments = task.documents

            for (document in questionDocuments) {
                val questionEntity = document.toObject(QuestionEntity::class.java)

                // Если есть путь к изображению, удаляем его из Storage
                questionEntity?.pathPictureQuestion?.let { storagePath ->
                    deletePhotoFromServer(storagePath)
                }

                // Удаление документа
                document.reference.delete().await()
            }
        } catch (e: Exception) {
            Log.w("Firestore", "Error deleting remote questions", e)
        }
    }

    private fun uploadPhotoToServer(localPhotoPath: String, pathStructure: PathStructure, questionId: String) {
        if (localPhotoPath.isNotBlank()) {
            // Проверка на авторизацию
            val currentUser: FirebaseUser? = FirebaseAuth.getInstance().currentUser

            if (currentUser == null) {
                // Если пользователь не авторизован, авторизуем анонимно
                FirebaseAuth.getInstance().signInAnonymously()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("FirebaseAuth", "Anonymous user created")
                            // Продолжаем загрузку файла после успешной аутентификации
                            uploadFile(localPhotoPath, pathStructure, questionId)
                        } else {
                            Log.e("FirebaseAuth", "Anonymous authentication error", task.exception)
                        }
                    }
            } else {
                // Если пользователь уже авторизован, продолжаем загрузку
                uploadFile(localPhotoPath, pathStructure, questionId)
            }
        }
    }

    private fun uploadFile(localPhotoPath: String, pathStructure: PathStructure, questionId: String) {
        Log.d("FirebaseStorage", "Starting file upload for question $questionId")

        val localFile = File(localPhotoPath)
        if (!localFile.exists()) {
            Log.e("FirebaseStorage", "Local file not found: $localPhotoPath")
            return
        }

        val fileName = localFile.name
        val storagePath = createQuestionImagePath(pathStructure, questionId, fileName)

        Log.d("FirebaseStorage", "Uploading to storage path: $storagePath")

        val storageRef = storage.reference.child(storagePath)
        storageRef.putFile(Uri.fromFile(localFile))
            .addOnSuccessListener {
                Log.d("FirebaseStorage", "Photo uploaded successfully to: $storagePath")
            }
            .addOnFailureListener { exception ->
                Log.e("FirebaseStorage", "Error uploading photo to: $storagePath", exception)
            }
    }

    private suspend fun downloadPhotoToLocalPath(storagePath: String): String? {
        return try {
            val storageRef = storage.reference.child(storagePath)

            // Extract filename from storage path (everything after last '_' or '/')
            val fileName = when {
                storagePath.contains('_') -> storagePath.substringAfterLast('_')
                storagePath.contains('/') -> storagePath.substringAfterLast('/')
                else -> storagePath
            }

            val localFile = File(context.filesDir, fileName)

            // Skip download if file already exists
            if (localFile.exists()) {
                Log.d("RepositoryQuestionImpl", "Photo already exists locally: ${localFile.absolutePath}")
                return localFile.absolutePath
            }

            localFile.parentFile?.mkdirs() // Создаем родительские директории при необходимости

            val downloadTask = storageRef.getFile(localFile)
            downloadTask.await()

            Log.d("RepositoryQuestionImpl", "Photo downloaded successfully from: $storagePath to: ${localFile.absolutePath}")
            localFile.absolutePath
        } catch (e: Exception) {
            Log.e("RepositoryQuestionImpl", "Error downloading photo from: $storagePath", e)
            null
        }
    }

    private suspend fun deletePhotoFromServer(storagePath: String) {
        if (storagePath.isNotBlank()) {
            val photoRef = storage.reference.child(storagePath)
            try {
                photoRef.delete().await()
                Log.d("RepositoryQuestionImpl", "Photo deleted successfully from server: $storagePath")
            } catch (e: Exception) {
                Log.e("RepositoryQuestionImpl", "Error deleting photo from server: $storagePath", e)
            }
        }
    }



    /**
     * Demo function to show the new flat storage path structure
     * Examples of generated paths:
     */
    private fun demonstrateNewPaths() {
        val pathStructure = PathStructure(
            nameEvent = "QUIZ_HOME",
            nameCategory = "Математика",
            nameSubCategory = "Алгебра",
            nameSubsubCategory = "Уравнения",
            nameQuiz = "quiz1"
        )

        val questionId = "123"
        val imageFileName = "equation_diagram.jpg"

        val storagePath = createQuestionImagePath(pathStructure, questionId, imageFileName)
        // Result: "QUIZ_HOME/Математика>Алгебра>Уравнения>quiz1_question_123_equation_diagram.jpg"

        Log.d("StorageDemo", "Generated storage path: $storagePath")

        // Other examples:
        val basePath = createStoragePath(pathStructure)
        // Result: "QUIZ_HOME/Математика>Алгебра>Уравнения>quiz1"

        val dataPath = createStoragePath(pathStructure, "data", "questions.json")
        // Result: "QUIZ_HOME/Математика>Алгебра>Уравнения>quiz1_data_questions.json"

        Log.d("StorageDemo", "Base path: $basePath")
        Log.d("StorageDemo", "Data path: $dataPath")
    }

    // Вспомогательные методы для оптимизированной загрузки

    private suspend fun downloadTextFile(storagePath: String): String? {
        return try {
            val storageRef = storage.reference.child(storagePath)
            val bytes = storageRef.getBytes(1024 * 1024).await() // 1MB limit
            String(bytes)
        } catch (e: Exception) {
            Log.e("RepositoryQuestionImpl", "Error downloading text file: $storagePath", e)
            null
        }
    }

    private fun parseQuestionsIndex(indexContent: String): QuestionsIndex {
        // Parse JSON index content
        // Returns metadata about available languages, difficulties, file sizes etc.
        return try {
            val gson = Gson()
            gson.fromJson(indexContent, QuestionsIndex::class.java)
        } catch (e: Exception) {
            Log.e("RepositoryQuestionImpl", "Error parsing questions index", e)
            QuestionsIndex(emptyList(), emptyList(), emptyMap())
        }
    }

    private fun determineRequiredFiles(
        indexData: QuestionsIndex,
        languages: List<String>,
        difficulty: String?
    ): List<String> {
        // Smart logic to determine minimal set of files needed
        return when {
            // If user needs specific language and it has dedicated file
            languages.size == 1 && indexData.languageFiles.containsKey(languages.first()) -> {
                listOf(indexData.languageFiles[languages.first()]!!)
            }
            // If multiple languages or no specific files - use bundle
            else -> listOf("questions_bundle.json")
        }
    }

    private suspend fun downloadSpecificQuestionFiles(
        pathStructure: PathStructure,
        fileNames: List<String>
    ): List<QuestionLocal> {
        val questions = mutableListOf<QuestionLocal>()

        for (fileName in fileNames) {
            val fullPath = "${createStoragePath(pathStructure)}_$fileName"
            val content = downloadTextFile(fullPath)
            content?.let {
                questions.addAll(parseQuestions(it))
            }
        }

        return questions
    }

    private fun parseAndFilterQuestions(
        bundleContent: String,
        requiredLanguage: String?,
        requiredDifficulty: String?
    ): List<QuestionLocal> {
        // Parse all questions and filter locally
        val allQuestions = parseQuestions(bundleContent)

        return allQuestions.filter { question ->
            val languageMatch = requiredLanguage?.let { question.language.code == it } ?: true
            val difficultyMatch = requiredDifficulty?.let {
                when(requiredDifficulty) {
                    "hard" -> question.hardQuestion == true
                    "simple" -> question.hardQuestion == false
                    else -> true
                }
            } ?: true
            languageMatch && difficultyMatch
        }
    }

    private fun parseQuestions(content: String): List<QuestionLocal> {
        // Implementation to parse JSON content to QuestionLocal list
        return try {
            val gson = Gson()
            val questionEntities = gson.fromJson(content, Array<QuestionEntity>::class.java)
            questionEntities.map { it.toQuestionLocal() }
        } catch (e: Exception) {
            Log.e("RepositoryQuestionImpl", "Error parsing questions", e)
            emptyList()
        }
    }

    // Data class for questions index
    private data class QuestionsIndex(
        val availableLanguages: List<String>,
        val availableDifficulties: List<String>,
        val languageFiles: Map<String, String> // language -> filename mapping
    )

    /**
     * PAGINATION STRATEGY: Creates paginated storage paths for efficient loading
     * Instead of 1 file with 400 questions, creates multiple files with 20 questions each
     *
     * Examples:
     * - "QUIZ_HOME/Математика>Алгебра>quiz1_questions_page_0.json" (questions 1-20)
     * - "QUIZ_HOME/Математика>Алгебра>quiz1_questions_page_1.json" (questions 21-40)
     * - "QUIZ_HOME/Математика>Алгебра>quiz1_questions_page_2.json" (questions 41-60)
     */
    private fun createPaginatedStoragePath(
        pathStructure: PathStructure,
        pageNumber: Int,
        pageSize: Int = 20
    ): String {
        val basePath = createStoragePath(pathStructure)
        return "${basePath}_questions_page_${pageNumber}.json"
    }

    /**
     * Creates metadata file path that contains info about total pages, question counts, etc.
     */
    private fun createQuestionsMetadataPath(pathStructure: PathStructure): String {
        val basePath = createStoragePath(pathStructure)
        return "${basePath}_questions_metadata.json"
    }

    /**
     * COST-EFFECTIVE: Download only specific page of questions
     * Downloads ~100KB instead of 2MB (20x savings!)
     *
     * Note: This is a placeholder implementation.
     * Full pagination support will be added when needed.
     */
    suspend fun fetchQuestionsPaginated(
        pathStructure: PathStructure,
        pageNumber: Int = 0,
        pageSize: Int = 20,
        language: String? = null,
        difficulty: String? = null
    ): List<QuestionLocal> {
        // For now, return regular fetch with local filtering
        // TODO: Implement full pagination when storage structure is ready
        return try {
            val allQuestions = fetchQuestionsWithStructuredIds(
                pathStructure = pathStructure,
                difficulty = difficulty,
                language = language,
                limit = 100 // Fetch more for local pagination
            )

            // Apply pagination locally
            val startIndex = pageNumber * pageSize
            val endIndex = minOf(startIndex + pageSize, allQuestions.size)

            if (startIndex < allQuestions.size) {
                allQuestions.subList(startIndex, endIndex)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("RepositoryQuestionImpl", "Error fetching paginated questions", e)
            emptyList()
        }
    }

}

