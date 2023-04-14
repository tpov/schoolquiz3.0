package com.tpov.schoolquiz.presentation.dowload

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tpov.schoolquiz.data.database.QuizDatabase
import com.tpov.schoolquiz.databinding.DownloadFragmentBinding
import com.tpov.schoolquiz.presentation.fragment.BaseFragment
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import java.io.File

class DownloadFragment : BaseFragment() {

    private lateinit var binding: DownloadFragmentBinding
    private lateinit var downloadedResourcesAdapter: DownloadedResourcesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DownloadFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        downloadedResourcesAdapter = DownloadedResourcesAdapter { downloadedResource ->
            // Здесь добавьте код для удаления ресурса, например, удаление файла и обновление списка
            // ...
        }

        binding.recyclerViewDownloadedResources.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewDownloadedResources.adapter = downloadedResourcesAdapter

        val downloadedResources = getDownloadedResources(requireContext())
        downloadedResourcesAdapter.submitList(downloadedResources)
        loadDownloadedResources()
        loadDatabaseInfo()
    }


    private fun getDownloadedResources(context: Context): List<DownloadedResource> {
        val downloadedResources = mutableListOf<DownloadedResource>()

        val cacheDir = context.cacheDir
        val imageDir = File(cacheDir, "imageCache") // Замените на название вашей директории для изображений
        val musicDir = File(cacheDir, "musicCache") // Замените на название вашей директории для музыки

        if (imageDir.exists()) {
            for (file in imageDir.listFiles() ?: emptyArray()) {
                val fileSizeInKb = file.length() / 1024
                downloadedResources.add(DownloadedResource(file.name, fileSizeInKb, file.absolutePath))
            }
        }

        if (musicDir.exists()) {
            for (file in musicDir.listFiles() ?: emptyArray()) {
                val fileSizeInKb = file.length() / 1024
                downloadedResources.add(DownloadedResource(file.name, fileSizeInKb, file.absolutePath))
            }
        }

        return downloadedResources
    }

    private fun loadDownloadedResources() {
        lifecycleScope.launch {
            val downloadedResources = getDownloadedResources(requireContext())
            downloadedResourcesAdapter.submitList(downloadedResources)
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun loadDatabaseInfo() {
        lifecycleScope.launch {
            val quizDatabase = QuizDatabase.getDatabase(requireContext())
            val quizDao = quizDatabase.getQuizDao()

            val questionDetailCount = quizDao.getQuestionDetailCount()
            val questionCount = quizDao.getQuestionCount()
            val quizCount = quizDao.getQuizCount()
            val apiQuestionCount = quizDao.getApiQuestionCount()
            val profileCount = quizDao.getProfileCount()
            val chatCount = quizDao.getChatCount()

            val databaseInfo = """
            QuestionDetailEntity count: $questionDetailCount
            QuestionEntity count: $questionCount
            QuizEntity count: $quizCount
            ApiQuestion count: $apiQuestionCount
            ProfileEntity count: $profileCount
            ChatEntity count: $chatCount
        """.trimIndent()

            binding.tvDatabaseInfo.text = databaseInfo
        }
    }
}

data class DownloadedResource(
    val fileName: String,
    val fileSize: Long,
    val filePath: String
)