package com.tpov.common

import android.graphics.Bitmap
import android.widget.Toast
import com.bumptech.glide.Priority
import com.tpov.common.presentation.utils.Values
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object Core {

    var token: String = ""
    var tpovId = 0
        set(value) {
            field = value
            _tpovIdFlow.value = value
        }


    private val _tpovIdFlow = MutableStateFlow(tpovId)
    val tpovIdFlow: StateFlow<Int> = _tpovIdFlow


    fun savePicture(fileName: String, bitmap: Bitmap) {
        val file = File(Values.application.filesDir, fileName)
        var fileOutputStream: FileOutputStream? = null
        try {
            fileOutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream)
            Toast.makeText(Values.application, "Image saved to $fileName in Pictures", Toast.LENGTH_SHORT)
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            fileOutputStream?.close()
        }
    }
}
