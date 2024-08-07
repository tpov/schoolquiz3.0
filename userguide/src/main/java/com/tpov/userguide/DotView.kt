package com.tpov.userguide

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

internal class DotView {
    fun showDot(
        item: View?,
        context: Context,
        text: String,
        titleText: String? = null,
        icon: Drawable? = null,
        video: String? = null,
        theme: Drawable?,
        options: Options,
        buttonClick: (Int) -> Unit
    ) {
        if (options.showDot) {
            if (options.numberDot != null) item?.foreground = NotificationNumberDrawable(options.numberDot ?: "1")
            else item?.foreground = DotDrawable(item?.foreground ?: ColorDrawable(Color.TRANSPARENT))
        }

        item?.setOnClickListener {
            showDialog(context, text, titleText, icon, video, theme, item, buttonClick)
        }
    }

    class NotificationNumberDrawable(var count: String) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            textAlign = Paint.Align.CENTER
        }
        private val radius = 16f

        override fun draw(canvas: Canvas) {
            val bounds = bounds
            val cx = bounds.right - radius * 2
            val cy = bounds.top + radius * 2
            canvas.drawCircle(cx, cy, radius, paint)
                val text = count
                val textHeight = textPaint.descent() - textPaint.ascent()
                val textOffset = (textHeight / 2) - textPaint.descent()
                canvas.drawText(text, cx, cy + textOffset, textPaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        fun setSize(width: Int, height: Int) {
            setBounds(0, 0, width, height)
        }
    }

    private class DotDrawable(private val base: Drawable) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
        }
        private val radius = 6f

        override fun draw(canvas: Canvas) {
            base.draw(canvas)
            val cx = bounds.width() - radius * 3
            val cy = bounds.height() - radius * 3
            canvas.drawCircle(cx, cy, radius, paint)
        }

        override fun setAlpha(alpha: Int) { base.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { base.colorFilter = colorFilter }
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    @OptIn(UnstableApi::class)
    private fun showDialog(
        context: Context,
        text: String,
        titleText: String?,
        icon: Drawable?,
        video: String?,
        theme: Drawable?,
        item: View,
        buttonClick: (Int) -> Unit
    ) {
        MainView().apply { showDialog(context, text, titleText, icon, video, theme, item, buttonClick) }
    }
}
