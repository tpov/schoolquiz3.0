package com.tpov.userguide.presentation

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
import com.tpov.userguide.R

internal class DotView {

    @OptIn(UnstableApi::class)
    fun showDot(
        views: List<View?>,
        context: Context,
        text: String,
        titleText: String? = null,
        icon: Drawable? = null,
        video: String? = null,
        theme: Drawable? = null,
        options: Options,
        uniqueId: Int,
        buttonClick: (Int) -> Unit
    ) {
        if (views.isEmpty()) {
            MainView().showDialog(context, text, titleText, icon, video, theme, uniqueId,null, buttonClick)
        } else {
            views.forEach { view ->
                if (options.showDot) {
                    val dotDrawable = if (options.dotText != null) {
                        NotificationNumberDrawable(options.dotText ?: "e")
                    } else {
                        DotDrawable(view?.foreground ?: ColorDrawable(Color.TRANSPARENT))
                    }
                    view?.foreground = dotDrawable
                }

                if (view?.getTag(R.id.original_click_listener) == null) {
                    view?.setTag(R.id.original_click_listener, getOnClickListener(view))
                }

                view?.setOnClickListener { v ->
                    if (v == views.first()) {
                        MainView().showDialog(context, text, titleText, icon, video, theme, uniqueId, v, buttonClick)
                    }
                }
            }
        }
    }

    private fun getOnClickListener(view: View?): View.OnClickListener? {
        if (view == null) return null
        try {
            val listenerInfoField = View::class.java.getDeclaredField("mListenerInfo")
            listenerInfoField.isAccessible = true
            val listenerInfo = listenerInfoField.get(view) ?: return null
            val onClickListenerField = listenerInfo.javaClass.getDeclaredField("mOnClickListener")
            onClickListenerField.isAccessible = true
            return onClickListenerField.get(listenerInfo) as? View.OnClickListener
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
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
            val textHeight = textPaint.descent() - textPaint.ascent()
            val textOffset = (textHeight / 2) - textPaint.descent()
            canvas.drawText(count, cx, cy + textOffset, textPaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
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

        override fun setAlpha(alpha: Int) {
            base.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            base.colorFilter = colorFilter
        }

        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
