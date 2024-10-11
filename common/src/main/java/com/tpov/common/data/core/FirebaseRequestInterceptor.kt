package com.tpov.common.data.core

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

object FirebaseRequestInterceptor {
    private var isOpenServer: Boolean? = null
    private val requestQueue: ConcurrentLinkedQueue<QueuedRequest<*>> = ConcurrentLinkedQueue()
    private var isProcessingQueue: Boolean = false

    data class QueuedRequest<T>(
        val request: () -> Task<T>,
        val taskCompletionSource: TaskCompletionSource<T>
    )

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

    fun <T> executeWithChecksSingleTask(request: () -> Task<T>): Task<T> {
        val taskCompletionSource = TaskCompletionSource<T>()

        val queuedRequest = QueuedRequest(request, taskCompletionSource)
        requestQueue.add(queuedRequest)
        processQueue()

        return taskCompletionSource.task
    }

    private fun processQueue() {
        if (isProcessingQueue) return

        coroutineScope.launch {
            isProcessingQueue = true
            while (requestQueue.isNotEmpty()) {
                val queuedRequest = requestQueue.poll() as QueuedRequest<Any>

                if (Core.token.isEmpty()) {
                    Log.d("FirebaseRequestInterceptor", "Token is empty, cannot execute request")
                    queuedRequest.taskCompletionSource.setException(Exception("Token is empty"))
                    continue
                }

                try {
                    val isOpenServerTask = TaskCompletionSource<Boolean>()
                    getIsOpenServer(isOpenServerTask)
                    val isOpenServerResult = Tasks.await(isOpenServerTask.task)

                    if (isOpenServerResult) {
                        Log.d("FirebaseRequestInterceptor", "Server is open, executing request")
                        queuedRequest.request()
                            .addOnSuccessListener { result ->
                                queuedRequest.taskCompletionSource.setResult(result)
                            }
                            .addOnFailureListener { exception ->
                                queuedRequest.taskCompletionSource.setException(exception)
                            }
                    } else {
                        Log.d("FirebaseRequestInterceptor", "Server is closed, completing task with error")
                        queuedRequest.taskCompletionSource.setException(Exception("Server is closed"))
                    }
                } catch (e: Exception) {
                    Log.e("FirebaseRequestInterceptor", "Exception occurred: ${e.message}")
                    queuedRequest.taskCompletionSource.setException(e)
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
