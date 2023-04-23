package com.tpov.schoolquiz.presentation.custom

import android.graphics.Camera
import android.view.animation.Animation
import android.view.animation.Transformation

class YRotateAnimation(
    private val fromDegrees: Float,
    private val toDegrees: Float,
    private val centerX: Float,
    private val centerY: Float
) : Animation() {

    private var camera: Camera? = null

    init {
        duration = 500
        fillAfter = true
    }

    override fun initialize(
        width: Int, height: Int,
        parentWidth: Int, parentHeight: Int
    ) {
        super.initialize(width, height, parentWidth, parentHeight)
        camera = Camera()
    }

    override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
        val degrees = fromDegrees + (toDegrees - fromDegrees) * interpolatedTime
        val matrix = t.matrix
        camera?.let {
            it.save()
            it.rotateY(degrees)
            it.getMatrix(matrix)
            it.restore()
        }

        // Устанавливаем ось вращения по вертикальной оси Y
        matrix.preTranslate(-centerX, -centerY)
        matrix.postTranslate(centerX, centerY)
    }
}
