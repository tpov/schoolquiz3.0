package com.tpov.common.presentation.utils

import android.view.View

class TextAnimator {
    private fun hideWithDelay(view: View, duration: Long, delay: Long) {
        view.translationX = -view.width.toFloat()
        view.apply {
            alpha = 0f

            animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(duration)
                .setStartDelay(delay)
                .withStartAction { view.visibility = View.VISIBLE }
                .start()
        }
    }

    private fun showWithDelay(view: View, duration: Long, delay: Long) {
        view.animate()
            .translationX(view.width.toFloat())
            .alpha(0f)
            .setDuration(duration)
            .setStartDelay(delay)
            .withEndAction { view.visibility = View.GONE }
            .start()
    }
}