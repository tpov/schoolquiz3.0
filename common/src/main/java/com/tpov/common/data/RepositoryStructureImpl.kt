package com.tpov.common.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.tpov.common.data.database.StructureDataDao
import com.tpov.common.data.database.StructureEditDataDao
import com.tpov.common.data.model.entity.StructureDataEntity
import com.tpov.common.data.model.entity.StructureInfoEntity
import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.common.data.model.remote.StructureDataRemote
import com.tpov.common.data.model.remote.StructureEditData
import com.tpov.common.data.model.remote.StructureInfoRemote
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.repository.RepositoryStructure
import com.tpov.common.domain.usecase.SettingConfigObject.settingsConfig
import com.tpov.common.presentation.model.PathStructure
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

open class RepositoryStructureImpl @Inject constructor(
    private val structureDataDao: StructureDataDao,
    private val structureEditDataDao: StructureEditDataDao,
    private val firestore: FirebaseFirestore,
    private val context: Context,
    private val externalScope: CoroutineScope
) : RepositoryStructure {

    private val gson = Gson()
    private val fileName = "structure_data.json"
    private val ratingFileName = "structure_rating.json"
    private val storageFolder = "quizPhoto"


    override suspend fun pushStructureInfoData(
        ratingData: StructureInfoRemote,
        path: PathStructure
    ) {
        val pathSegments = mutableListOf<String>()

        if (path.nameEvent != "") pathSegments.add("idEvent/${path.nameEvent}")
        if (path.nameCategory != "") pathSegments.add("idCategory/${path.nameCategory}")
        if (path.nameSubCategory != "") pathSegments.add("idSubCategory/${path.nameSubCategory}")
        if (path.nameSubsubCategory != "") pathSegments.add("idSubsubCategory/${path.nameSubsubCategory}")
        if (path.nameQuiz != "") pathSegments.add("idQuiz/${path.nameQuiz}")

        val fullPath = pathSegments.joinToString("/") + "/listData"

        val pushTask = {
            val taskCompletionSource = TaskCompletionSource<Void>()

            firestore.collection(fullPath)
                .add(ratingData)
                .addOnSuccessListener { documentReference ->
                    Log.d("Firestore", "DocumentSnapshot added with ID: ${documentReference.id}")
                    taskCompletionSource.setResult(null)
                }
                .addOnFailureListener { exception ->
                    Log.e("Firestore", "Error adding document", exception)
                    taskCompletionSource.setException(exception)
                }

            taskCompletionSource.task
        }

        try {
            pushTask().await()
        } catch (e: Exception) {
        }
    }

    override suspend fun insertStructureData(
        structureDataEntity: StructureDataEntity,
        event : String
    ) {
        structureDataDao.insertStructureData(structureDataEntity)
    }

    override suspend fun pushStructureData(
        structureDataEntity: StructureDataLocal,
        category: String
    ) {
        try {
            val path = "quizzes/${settingsConfig.tpovId}/$category/structureData"
            firestore.document(path).set(structureDataEntity).await()
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun fetchStructureCategoryDataList(eventQuiz: EventQuiz): List<StructureDataLocal>? {
        android.util.Log.d("StructureRepo", "🔍 fetchStructureCategoryDataList started for: ${eventQuiz.name} (id: ${eventQuiz.id})")
        
        return try {
            if (eventQuiz == EventQuiz.QUIZ_BY_USER) {
                android.util.Log.d("StructureRepo", "📝 Fetching user-created quizzes from: quizzes/${settingsConfig.tpovId}")
                // Для пользовательских викторин - получаем данные из структуры пользователя
                val basePath = "quizzes/${settingsConfig.tpovId}"
                
                val result = firestore.collection(basePath)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { it.toObject(StructureDataRemote::class.java)?.toStructureDataLocal() }
                
                if (result.isNotEmpty()) {
                    android.util.Log.d("StructureRepo", "✅ Found ${result.size} user quizzes")
                    result
                } else {
                    android.util.Log.w("StructureRepo", "⚠️ No user quizzes found")
                    emptyList()
                }
            } else {
                android.util.Log.d("StructureRepo", "🏠 Fetching ${eventQuiz.name} categories from structures/structureData/${eventQuiz.name}/")
                
                // Читаем из правильного пути: structures/structureData/QUIZ_HOME/
                val result = firestore.collection("structures")
                    .document("structureData")
                    .collection(eventQuiz.name)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { document ->
                        try {
                            val structureRemote = document.toObject(StructureDataRemote::class.java)
                            val structureLocal = structureRemote?.toStructureDataLocal()
                            
                            if (structureLocal != null) {
                                android.util.Log.d("StructureRepo", "  📄 Found category: ${structureLocal.nameItem} with ${structureLocal.children?.size ?: 0} children")
                                structureLocal
                            } else {
                                android.util.Log.w("StructureRepo", "  ⚠️ Failed to convert document ${document.id} to StructureDataLocal")
                                null
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("StructureRepo", "  ⚠️ Failed to parse document ${document.id}: ${e.message}")
                            null
                        }
                    }
                
                android.util.Log.d("StructureRepo", "✅ ${eventQuiz.name} categories processed successfully")
                android.util.Log.d("StructureRepo", "📊 Total found ${result.size} categories")
                
                // Выводим всю структуру
                if (result.isNotEmpty()) {
                    for (category in result) {
                        category.printFullStructure("${eventQuiz.name} - ${category.nameItem}")
                    }
                    result
                } else {
                    android.util.Log.w("StructureRepo", "⚠️ No data found for ${eventQuiz.name}")
                    emptyList() // Возвращаем пустой список вместо null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("StructureRepo", "❌ Error in fetchStructureCategoryDataList for ${eventQuiz.name}: ${e.message}", e)
            emptyList() // Возвращаем пустой список вместо null
        }
    }

    override suspend fun fetchStructureInfoData(
        path: PathStructure
    ): StructureInfoRemote? {
        val pathSegments = mutableListOf<String>()

        if (path.nameEvent != "") pathSegments.add("idEvent/${path.nameEvent}")
        if (path.nameCategory != "") pathSegments.add("idCategory/${path.nameCategory}")
        if (path.nameSubCategory != "") pathSegments.add("idSubCategory/${path.nameSubCategory}")
        if (path.nameSubsubCategory != "") pathSegments.add("idSubsubCategory/${path.nameSubsubCategory}")
        if (path.nameQuiz != "") pathSegments.add("idQuiz/${path.nameQuiz}")

        val fullPath = pathSegments.joinToString("/") + "/listData"

        val fetchTask = {
            val taskCompletionSource = TaskCompletionSource<StructureInfoRemote?>()

            firestore.collection(fullPath)
                .whereEqualTo("tpovIdUser", settingsConfig.tpovId)
                .limit(1)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    val document = querySnapshot.documents.firstOrNull()
                    val result = document?.toObject(StructureInfoRemote::class.java)
                    taskCompletionSource.setResult(result)
                }
                .addOnFailureListener { exception ->
                    Log.e("Firestore", "Error fetching document", exception)
                    taskCompletionSource.setException(exception)
                }

            taskCompletionSource.task
        }

        return try {
            fetchTask().await()
        } catch (e: Exception) {
            Log.e("Firestore", "Failed to fetch data from Firestore", e)
            null
        }
    }

    override suspend fun saveStructureData(
        structureDataCategoryList: List<StructureDataLocal>,
        event: String
    ) {
        android.util.Log.d("StructureRepo", "💾 saveStructureData called for event: $event")
        android.util.Log.d("StructureRepo", "📊 Categories to save: ${structureDataCategoryList.size}")
        
        for (category in structureDataCategoryList) {
            android.util.Log.d("StructureRepo", "  📂 Category: ${category.nameItem} with ${category.children?.size ?: 0} children")
        }
        
        val structureDataEntity = StructureDataLocal(
            nameItem = event,
            children = structureDataCategoryList.toMutableList()
        ).toStructureDataEntity()
        
        if (structureDataEntity != null) {
            android.util.Log.d("StructureRepo", "✅ Saving to local DB: $event")
            structureDataDao.insertStructureData(structureDataEntity)
            android.util.Log.d("StructureRepo", "✅ Successfully saved to local DB")
        } else {
            android.util.Log.e("StructureRepo", "❌ Failed to convert to StructureDataEntity")
        }
    }

    override suspend fun insertEditStructure(structureEditData: StructureEditData) {
        structureEditDataDao.insertStructureEditData(structureEditData)
    }

    override suspend fun pushEditStructure(structureEditData: StructureEditData) {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val url = "https://create-quiz-function-762375057396.us-west3.run.app"
        val data = JSONObject()
        data.put("structureEditData", structureEditData)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = data.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val pushTask = {
            val taskCompletionSource = TaskCompletionSource<StructureEditData>()
            val call = client.newCall(request)

            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    taskCompletionSource.setException(e)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    if (response.isSuccessful) {

                    }
                }
            })

            taskCompletionSource.task
        }
        pushTask()
    }

    override suspend fun getEditStructure(): List<StructureEditData> {
        return structureEditDataDao.getAllStructureEditData()
    }

    override fun deleteLocalPictureStructure(namePicture: String) {
        TODO("Not yet implemented")
    }

    /**
     * Sanitizes path components to prevent Firestore path parsing issues.
     * Replaces forward slashes with underscores to avoid creating invalid document references.
     */
    private fun sanitizePathComponent(component: String): String {
        return component.replace("/", "_")
    }

    override suspend fun fetchStructureInfo(path: PathStructure): StructureInfoEntity? {
        val db = FirebaseFirestore.getInstance()

        val pathSegments = mutableListOf<String>()
        pathSegments.add("structures")
        pathSegments.add("structureInfo")
        pathSegments.add("quiz${path.nameEvent}")
        if (path.nameCategory != "") pathSegments.add("category/${sanitizePathComponent(path.nameCategory)}")
        if (path.nameSubCategory != "") pathSegments.add("subCategory/${sanitizePathComponent(path.nameSubCategory)}")
        if (path.nameSubsubCategory != "") pathSegments.add("subsubCategory/${sanitizePathComponent(path.nameSubsubCategory)}")
        if (path.nameQuiz != "") pathSegments.add("quizzes/${sanitizePathComponent(path.nameQuiz)}")
        pathSegments.add("infoList/tpovIdList/${settingsConfig.tpovId}")

        val fullPath = pathSegments.joinToString("/")
        
        return try {
            val document = db.document(fullPath).get().await()
            if (document.exists()) {
                val structureInfoRemote = document.toObject(StructureInfoRemote::class.java)
                structureInfoRemote?.toStructureInfoEntity(path)
            } else {
                Log.d("Firestore", "Document not found at path: $fullPath")
                null
            }
        } catch (e: Exception) {
            Log.e("Firestore", "Error fetching document: ${e.message}")
            null
        }
    }



    fun pushPictureStructure(namePicture: String) {
        val storageRef = FirebaseStorage.getInstance().reference.child("$storageFolder/$namePicture")
        val localFile = File(namePicture)

        localFile.parentFile?.let {
            if (!it.exists()) {
                it.mkdirs()
            }
        }

        if (!localFile.exists()) {
            println("Файл не найден: $namePicture")
            return
        }

        storageRef.putFile(Uri.fromFile(localFile))
            .addOnSuccessListener {
                println("Фотография загружена успешно: $namePicture")
            }
            .addOnFailureListener { exception ->
                println("Ошибка при загрузке фотографии: $namePicture")
                exception.printStackTrace()
            }
    }

    override fun fetchPictureStructure(namePicture: String) {
        val storageRef = FirebaseStorage.getInstance().reference.child("$storageFolder/$namePicture")
        val localFile = File(context.filesDir, namePicture)

        localFile.parentFile?.let {
            if (!it.exists()) {
                it.mkdirs()
            }
        }

        storageRef.getFile(localFile)
            .addOnSuccessListener {
                println("Фотография загружена успешно: $namePicture")
            }
            .addOnFailureListener { exception ->
                println("Ошибка при загрузке фотографии: $namePicture")
                exception.printStackTrace()
            }
    }

    override fun fetchPictureStructure(path: PathStructure) {
        TODO("Not yet implemented")
    }


    override fun clearStructureEdit() {
        // Clear StructureEditData from local database
        externalScope.launch(Dispatchers.IO) {
            structureEditDataDao.clearAllStructureEditData()
        }
    }

    override suspend fun getStructureEventData(
        eevent: String,
        vararg path: String
    ): List<StructureDataLocal>? {
        android.util.Log.d("StructureRepo", "🔍 getStructureEventData called for event: $eevent")
        android.util.Log.d("StructureRepo", "📂 Path: ${path.toList()}")
        
        val result = structureDataDao.getStructureDataByPath(eevent, path.toList())
        
        if (result != null) {
            android.util.Log.d("StructureRepo", "✅ Found ${result.size} categories in local DB")
            for (category in result) {
                android.util.Log.d("StructureRepo", "  📂 Local category: ${category.nameItem} with ${category.children?.size ?: 0} children")
            }
        } else {
            android.util.Log.w("StructureRepo", "⚠️ No data found in local DB for event: $eevent")
        }
        
        return result
    }
}

