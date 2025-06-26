package com.tpov.schoolquiz.presentation.create

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImagePickerHelper(
    private val fragment: Fragment,
    private val onImageSelected: (String) -> Unit
) {

    private var imagePickerLauncher: ActivityResultLauncher<String>? = null

    init {
        setupImagePicker()
    }

    private fun setupImagePicker() {
        imagePickerLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { selectedImageUri ->
                saveImageLocally(selectedImageUri)
            }
        }
    }

    fun pickImage() {
        imagePickerLauncher?.launch("image/*")
    }

    private fun saveImageLocally(uri: Uri) {
        try {
            val context = fragment.requireContext()
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)

            inputStream?.let { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                stream.close()

                // Создаем уникальное имя файла
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "IMG_$timeStamp.jpg"

                // Создаем директорию если её нет
                val photoDir = File(context.filesDir, "questionPhoto")
                if (!photoDir.exists()) {
                    photoDir.mkdirs()
                }

                // Сохраняем файл
                val imageFile = File(photoDir, fileName)
                val outputStream = FileOutputStream(imageFile)

                // Сжимаем изображение для экономии места
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                outputStream.flush()
                outputStream.close()

                // Возвращаем имя файла
                onImageSelected(fileName)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            // TODO: Показать ошибку пользователю
        }
    }

    suspend fun resizeImage(imagePath: String, maxWidth: Int = 800, maxHeight: Int = 600): String = withContext(Dispatchers.IO) {
        try {
            val context = fragment.requireContext()
            val originalFile = File(context.filesDir, "questionPhoto/$imagePath")

            if (!originalFile.exists()) return@withContext imagePath

            val bitmap = BitmapFactory.decodeFile(originalFile.absolutePath)

            // Вычисляем новые размеры, сохраняя пропорции
            val ratio = minOf(
                maxWidth.toFloat() / bitmap.width,
                maxHeight.toFloat() / bitmap.height
            )

            val newWidth = (bitmap.width * ratio).toInt()
            val newHeight = (bitmap.height * ratio).toInt()

            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

            // Сохраняем сжатое изображение
            val outputStream = FileOutputStream(originalFile)
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.flush()
            outputStream.close()

            imagePath
        } catch (e: Exception) {
            e.printStackTrace()
            imagePath
        }
    }

    fun deleteImage(imageName: String) {
        try {
            val context = fragment.requireContext()
            val imageFile = File(context.filesDir, "questionPhoto/$imageName")
            if (imageFile.exists()) {
                imageFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getImageFile(imageName: String): File? {
        return try {
            val context = fragment.requireContext()
            val imageFile = File(context.filesDir, "questionPhoto/$imageName")
            if (imageFile.exists()) imageFile else null
        } catch (e: Exception) {
            null
        }
    }
}
