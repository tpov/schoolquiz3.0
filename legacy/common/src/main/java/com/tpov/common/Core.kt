package com.tpov.common

import android.graphics.Bitmap
import android.widget.Toast
import com.tpov.common.presentation.utils.Values
import java.io.File
import java.io.FileOutputStream

object Core {

    var token: String = ""

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
