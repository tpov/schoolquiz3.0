package com.tpov.common.presentation.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

class ResizeAndCrop(val outWidth: Int, val outHeight: Int) : BitmapTransformation() {
    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update("ResizeAndCrop".toByteArray(CHARSET))
    }

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val width = toTransform.width
        val height = toTransform.height
        val aspectRatio = width.toFloat() / height.toFloat()
        val targetHeight = outHeight
        val targetWidth = (targetHeight * aspectRatio).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(toTransform, targetWidth, targetHeight, true)

        val xOffset = (scaledBitmap.width - outWidth) / 2

        val targetBitmap = pool.get(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(targetBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        val srcRect = Rect(xOffset, 0, xOffset + outWidth, outHeight)
        val dstRect = Rect(0, 0, outWidth, outHeight)

        canvas.drawBitmap(scaledBitmap, srcRect, dstRect, paint)

        return targetBitmap
    }
}
