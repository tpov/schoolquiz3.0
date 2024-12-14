package com.tpov.common.presentation.custom

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
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
import com.tpov.common.EVENT_QUIZ_ARENA
import com.tpov.common.EVENT_QUIZ_FOR_ADMIN
import com.tpov.common.EVENT_QUIZ_FOR_MODERATOR
import com.tpov.common.EVENT_QUIZ_FOR_TESTER
import com.tpov.common.EVENT_QUIZ_FOR_USER
import com.tpov.common.EVENT_QUIZ_HOME
import com.tpov.common.EVENT_QUIZ_TOURNIRE
import com.tpov.common.EVENT_QUIZ_TOURNIRE_LEADER

class CustomProgressBar(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint1 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint2 = Paint(Paint.ANTI_ALIAS_FLAG)

    private val progressRect = RectF()
    private val leftMarkerRect = RectF()
    private val rightMarkerRect = RectF()

    var progress = 0.5f
    var leftMarkerPosition = 0.25f
    var rightMarkerPosition = 0.75f

    init {
        progressPaint.color = Color.YELLOW
        markerPaint1.color = Color.RED
        markerPaint2.color = Color.GREEN
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        progressRect.set(width * progress, height / 2 - 2f, width, height / 2 + 2f)
        canvas.drawRect(progressRect, Paint().apply { color = Color.GRAY })

        progressRect.set(0f, height / 2 - 2f, width * progress, height / 2 + 2f)
        canvas.drawRect(progressRect, progressPaint)

        val leftMarkerX = width * leftMarkerPosition
        leftMarkerRect.set(leftMarkerX - 9f, height, leftMarkerX + 9f, 0f)
        canvas.drawOval(leftMarkerRect, markerPaint1)

        val rightMarkerX = width * rightMarkerPosition
        rightMarkerRect.set(rightMarkerX - 9f, height, rightMarkerX + 9f, 0f)
        canvas.drawOval(rightMarkerRect, markerPaint2)
    }

    @SuppressLint("SetTextI18n")
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
            override fun onAnimationStart(animation: Animator) {}

            override fun onAnimationEnd(animation: Animator) {
                when (event) {
                    EVENT_QUIZ_FOR_USER -> noShowRating(rbEvaluation,bOk,bHelpTranslate,tvEvaluation)

                    EVENT_QUIZ_FOR_TESTER,
                    EVENT_QUIZ_FOR_MODERATOR,
                    EVENT_QUIZ_FOR_ADMIN -> showRating(rbEvaluation, bOk, bHelpTranslate,tvEvaluation)

                    EVENT_QUIZ_ARENA,
                    EVENT_QUIZ_TOURNIRE,
                    EVENT_QUIZ_TOURNIRE_LEADER,
                    EVENT_QUIZ_HOME -> {
                        if (showStars) showRating(rbEvaluation, bOk, bHelpTranslate, tvEvaluation)
                        else noShowRating(rbEvaluation, bOk, bHelpTranslate, tvEvaluation)
                    }
                }

                rbEvaluation.setOnRatingBarChangeListener { _, _, _ ->
                    bOk.visibility = VISIBLE
                    bHelpTranslate.visibility = GONE
                }

                progressBar.visibility = GONE
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
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