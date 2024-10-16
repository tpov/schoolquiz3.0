package com.tpov.common.data.core

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

object FirebaseRequestInterceptor {
    private var isOpenServer: Boolean? = null
    private val requestQueue: ConcurrentLinkedQueue<() -> Task<*>> = ConcurrentLinkedQueue()
    private var isProcessingQueue: Boolean = false

    fun getIsOpenServer(taskCompletionSource: TaskCompletionSource<Boolean>) {
        fetchIsOpenServer()
            .addOnSuccessListener { isOpenServerResult ->
                isOpenServer = isOpenServerResult
                taskCompletionSource.setResult(isOpenServer!!)
            }
            .addOnFailureListener { exception ->
                taskCompletionSource.setException(exception)
            }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun <T> executeWithChecksSingleTask(request: () -> Task<T>): Task<T> {
        val taskCompletionSource = TaskCompletionSource<T>()

        requestQueue.add {
            request().addOnSuccessListener { result ->
                taskCompletionSource.setResult(result)
            }.addOnFailureListener { exception ->
                taskCompletionSource.setException(exception)
            }
        }
        processQueue()

        return taskCompletionSource.task
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun processQueue() {
        if (isProcessingQueue) return

        GlobalScope.launch {
            isProcessingQueue = true
            while (requestQueue.isNotEmpty()) {
                val request = requestQueue.peek()

                if (Core.token.isEmpty()) {
                    Log.d("FirebaseRequestInterceptor", "Token is empty, retrying after delay")
                    delay(5000L)
                    continue
                }

                try {
                    val isOpenServerTask = TaskCompletionSource<Boolean>()
                    getIsOpenServer(isOpenServerTask)
                    val isOpenServerResult = Tasks.await(isOpenServerTask.task)

                    if (isOpenServerResult) {
                        Log.d("FirebaseRequestInterceptor", "Server is open, executing request")
                        request()
                        requestQueue.poll() // Удаляем успешно выполненный запрос из очереди
                    } else {
                        Log.d("FirebaseRequestInterceptor", "Server is closed, retrying after delay")
                        delay(5000L)
                    }
                } catch (e: Exception) {
                    Log.e("FirebaseRequestInterceptor", "Exception occurred: ${e.message}, retrying request after delay")
                    delay(5000L)
                }
            }
            isProcessingQueue = false
        }
    }

    private fun fetchIsOpenServer(): Task<Boolean> {
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("variable").document("serverConfig")
        val taskCompletionSource = TaskCompletionSource<Boolean>()

        docRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val isOpenServer = document.getBoolean("isOpenServer") ?: false
                    Log.d("FirebaseRequestInterceptor", "isOpenServer fetched: $isOpenServer")
                    taskCompletionSource.setResult(isOpenServer)
                } else {
                    Log.e("FirebaseRequestInterceptor", "Document not found")
                    taskCompletionSource.setException(Exception("Документ не найден"))
                }
            }
            .addOnFailureListener { exception ->
                Log.e("FirebaseRequestInterceptor", "Failed to fetch document: ${exception.message}")
                taskCompletionSource.setException(exception)
            }

        return taskCompletionSource.task
    }
}
