package com.tpov.schoolquiz.presentation.custom

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import androidx.recyclerview.widget.RecyclerView

object ApplyCustomAnimation {
    fun applyCustomExitAnimation(recyclerView: RecyclerView, onAnimationEnd: () -> Unit) {
        val itemCount = recyclerView.adapter?.itemCount ?: 0
        val delay = 100L // задержка между анимациями элементов, вы можете изменить это значение

        var animationsEnded = 0
        val onAnimationEndListener = object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                animationsEnded++
                if (animationsEnded == itemCount) {
                    onAnimationEnd()
                }
            }
        }

        for (i in 0 until itemCount) {
            val child = recyclerView.getChildAt(i)

            // Анимация увеличения первого элемента по высоте
            if (i == 0) {
                val scaleAnimator = ObjectAnimator.ofFloat(child, "scaleY", 1.5f)
                scaleAnimator.duration = 300 // продолжительность анимации, вы можете изменить это значение
                scaleAnimator.addListener(onAnimationEndListener)
                scaleAnimator.start()
            } else {
                // Анимация смещения остальных элементов вниз
                val translationAnimator = ObjectAnimator.ofFloat(child, "translationY", child.height.toFloat())
                translationAnimator.startDelay = i * delay
                translationAnimator.duration = 300 // продолжительность анимации, вы можете изменить это значение
                translationAnimator.addListener(onAnimationEndListener)
                translationAnimator.start()
            }
        }
    }

}