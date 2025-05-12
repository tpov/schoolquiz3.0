package com.tpov.common.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.tpov.common.Core.tpovId
import com.tpov.common.data.database.StructureDataDao
import com.tpov.common.data.database.StructureEditDataDao
import com.tpov.common.data.manager.FirebaseRequestInterceptor.executeWithChecksSingleTask
import com.tpov.common.data.model.local.StructureDataEntity
import com.tpov.common.data.model.local.StructureInfoEntity
import com.tpov.common.data.model.remote.StructureDataRemote
import com.tpov.common.data.model.remote.StructureEditData
import com.tpov.common.data.model.remote.StructureInfoRemote
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.model.StructureDataLocal
import com.tpov.common.domain.repository.RepositoryStructure
import com.tpov.common.presentation.model.PathStructure
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
    private val context: Context
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
            executeWithChecksSingleTask(pushTask).await()
        } catch (e: Exception) {
        }
    }

    override suspend fun updateStructureData(
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
            val path = "quizzes/$tpovId/$category/structureData"
            firestore.document(path).set(structureDataEntity).await()
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun fetchStructureCategoryDataList(event: String): List<StructureDataLocal>? {
        if (event == EventQuiz.QUIZ_BY_USER.name) {
            val basePath = "quizzes/$tpovId"

            return try {
                val categories = firestore.collection(basePath)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { it.id }

                categories.flatMap { categoryId ->
                    firestore.collection("$basePath/category$categoryId/structureData")
                        .get()
                        .await()
                        .documents
                        .mapNotNull { document ->
                            document.toObject(StructureDataRemote::class.java)
                                ?.toStructureDataLocal()
                        }
                }
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            val basePath = "structures/structureData/quiz$event"

            return try {
                val categories = firestore.collection(basePath)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { it.id }

                categories.flatMap { categoryId ->
                    firestore.collection("category$categoryId")
                        .get()
                        .await()
                        .documents
                        .mapNotNull { document ->
                            document.toObject(StructureDataRemote::class.java)
                                ?.toStructureDataLocal()
                        }
                }
            } catch (e: Exception) {
                emptyList()
            }
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
                .whereEqualTo("tpovIdUser", tpovId)
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
            executeWithChecksSingleTask(fetchTask).await()
        } catch (e: Exception) {
            Log.e("Firestore", "Failed to fetch data from Firestore", e)
            null
        }
    }

    override suspend fun saveStructureData(
        structureDataCategoryList: List<StructureDataLocal>,
        event: String
    ) {
        structureDataDao.insertStructureData(
            StructureDataLocal(
                nameItem = event,
                children = structureDataCategoryList.toMutableList()
            ).toStructureDataEntity()!!
        )
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
        executeWithChecksSingleTask(pushTask)
    }

    override suspend fun getEditStructure(): List<StructureEditData> {
        TODO("Not yet implemented")
    }

    override fun deleteLocalPictureStructure(namePicture: String) {
        TODO("Not yet implemented")
    }

    override suspend fun fetchStructureInfo(path: PathStructure): StructureInfoEntity? {
        val db = FirebaseFirestore.getInstance()

        val pathSegments = mutableListOf<String>()
        pathSegments.add("structures")
        pathSegments.add("structureInfo")
        pathSegments.add("quiz${path.nameEvent}")
        if (path.nameCategory != "") pathSegments.add("category/${path.nameCategory}")
        if (path.nameSubCategory != "") pathSegments.add("subCategory/${path.nameSubCategory}")
        if (path.nameSubsubCategory != "") pathSegments.add("subsubCategory/${path.nameSubsubCategory}")
        if (path.nameQuiz != "") pathSegments.add("quizzes/${path.nameQuiz}")
        pathSegments.add("infoList/tpovIdList/$tpovId")

        val fullPath = pathSegments.joinToString("/")
return null!!
//        return try {
//            val document = db.document(fullPath).get().await()
//            if (document.exists()) {
//                document.toObject(StructureInfoRemote::class.java)
//            } else null
//        } catch (e: Exception) {
//            Log.e("Firestore", "Error fetching document: ${e.message}")
//            null
//        }
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

        executeWithChecksSingleTask {
            storageRef.putFile(Uri.fromFile(localFile))
                .addOnSuccessListener {
                    println("Фотография загружена успешно: $namePicture")
                }
                .addOnFailureListener { exception ->
                    println("Ошибка при загрузке фотографии: $namePicture")
                    exception.printStackTrace()
                }
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

        executeWithChecksSingleTask {
            storageRef.getFile(localFile)
                .addOnSuccessListener {
                    println("Фотография загружена успешно: $namePicture")
                }
                .addOnFailureListener { exception ->
                    println("Ошибка при загрузке фотографии: $namePicture")
                    exception.printStackTrace()
                }
        }
    }

    override fun fetchPictureStructure(path: PathStructure) {
        TODO("Not yet implemented")
    }


    override fun clearStructureEdit() {
        TODO("Not yet implemented")
    }

    override suspend fun getStructureEventData(
        eevent: String,
        vararg path: String
    ): List<StructureDataLocal>? {

        Log.d("initStructureData", "getStructureData")
        return structureDataDao.getStructureDataByPath(eevent, path.toList())
    }
}

