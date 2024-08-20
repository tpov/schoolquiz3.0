package com.tpov.common.data.core

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.TextView

class CustomProgressBar(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint1 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint2 = Paint(Paint.ANTI_ALIAS_FLAG)

    private val progressRect = RectF()
    private val leftMarkerRect = RectF()
    private val rightMarkerRect = RectF()

    var progress = 0.5f // Начальное значение прогресса
    var leftMarkerPosition = 0.25f // Положение левого маркера (от 0 до 1)
    var rightMarkerPosition = 0.75f // Положение правого маркера (от 0 до 1)

    init {
        progressPaint.color = Color.YELLOW // Цвет полосы прогресса
        markerPaint1.color = Color.RED // Цвет маркеров
        markerPaint2.color = Color.GREEN // Цвет маркеров
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        // Рисуем незанятую часть прогресс бара
        progressRect.set(width * progress, height / 2 - 2f, width, height / 2 + 2f)
        canvas.drawRect(progressRect, Paint().apply { color = Color.GRAY })

        // Рисуем занятую часть прогресс бара
        progressRect.set(0f, height / 2 - 2f, width * progress, height / 2 + 2f)
        canvas.drawRect(progressRect, progressPaint)

        // Рисуем левый маркер
        val leftMarkerX = width * leftMarkerPosition
        leftMarkerRect.set(leftMarkerX - 9f, height, leftMarkerX + 9f, 0f)
        canvas.drawOval(leftMarkerRect, markerPaint1)

        // Рисуем правый маркер
        val rightMarkerX = width * rightMarkerPosition
        rightMarkerRect.set(rightMarkerX - 9f, height, rightMarkerX + 9f, 0f)
        canvas.drawOval(rightMarkerRect, markerPaint2)
    }

    fun setProgressWithAnimation(
        progress: Float,
        duration: Long,
        tvResult: TextView,
        progressBar: ProgressBar,
        tvEvaluation: TextView,
        rbEvaluation: RatingBar,
        bOk: Button,
        bHelpTranslate: Button,
        showStars: Boolean,
        event: Int
    ) {
        val animator = ValueAnimator.ofFloat(this.progress, progress)
        animator.duration = duration
        animator.addUpdateListener { animation ->
            this.progress = animation.animatedValue as Float

            tvResult.text = "${((animation.animatedValue as Float) * 100).toInt()}%"
            invalidate()
        }
        animator.start()
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                // Анимация началась
            }

            override fun onAnimationEnd(animation: Animator) {
                when (event) {
                    1 -> noShowRating(rbEvaluation, bOk, bHelpTranslate, tvEvaluation)
                    2, 3, 4 -> showRating(rbEvaluation, bOk, bHelpTranslate, tvEvaluation)
                    5, 6, 7, 8 -> {
                        if (showStars) showRating(rbEvaluation, bOk, bHelpTranslate, tvEvaluation)
                        else noShowRating(rbEvaluation, bOk, bHelpTranslate, tvEvaluation)
                    }
                }

                rbEvaluation.setOnRatingBarChangeListener { ratingBar, rating, fromUser ->
                    bOk.visibility = VISIBLE
                    bHelpTranslate.visibility = GONE
                }
                // Анимация закончилась
                progressBar.visibility = GONE

            }

            override fun onAnimationCancel(animation: Animator) {
                // Анимация была отменена
            }

            override fun onAnimationRepeat(animation: Animator) {
                // Анимация повторилась
            }
        })
    }

    private fun showRating(
        rbEvaluation: RatingBar,
        bOk: Button,
        bHelpTranslate: Button,
        tvEvaluation: TextView
    ) {
        rbEvaluation.visibility = VISIBLE
        bOk.visibility = GONE
        bHelpTranslate.visibility = GONE
        tvEvaluation.visibility = VISIBLE
    }

    private fun noShowRating(
        rbEvaluation: RatingBar,
        bOk: Button,
        bHelpTranslate: Button,
        tvEvaluation: TextView
    ) {
        rbEvaluation.visibility = GONE
        bOk.visibility = VISIBLE
        bHelpTranslate.visibility = GONE
        tvEvaluation.visibility = GONE
    }
}