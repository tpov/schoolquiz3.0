package com.tpov.schoolquiz.presentation.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout

class CustomRoundedImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val radius = 25.0f // радиус закругления
    private val path = Path()
    private lateinit var rect: RectF

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.clipPath(path)
        super.dispatchDraw(canvas)
    }
}
