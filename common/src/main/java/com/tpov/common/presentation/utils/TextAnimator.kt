package com.tpov.common.presentation.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    private fun showTextWithDelay(
        textView: TextView,
        text: String,
        delayInMillis: Long,
        firstColorId: Int,
        secondColorId: Int
    ) {
        val existingText = textView.text.toString()
        if (existingText != text) {
            val commonPrefixLength = existingText.commonPrefixWith(text).length

            CoroutineScope(Dispatchers.Main).launch {
                val spannableText = SpannableStringBuilder()
                spannableText.append(text.substring(0, commonPrefixLength))
                for (i in commonPrefixLength until text.length) {
                    val char = text[i]
                    val start = spannableText.length
                    spannableText.append(char.toString())
                    spannableText.setSpan(
                        ForegroundColorSpan(firstColorId),
                        start,
                        start + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    textView.text = spannableText
                    delay(delayInMillis)

                    spannableText.setSpan(
                        ForegroundColorSpan(secondColorId),
                        start,
                        start + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    textView.text = spannableText
                }
            }
        }
    }

    fun startAnimationWithRepeat(
        imageView: ImageView,
        duration: Int,
        initialDelay: Long,
        repeatDelay: Long
    ) {
        val animator = ObjectAnimator.ofFloat(imageView, "rotationY", 0f, 360f).apply {
            this.duration = duration.toLong()
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                animation.removeListener(this)
                imageView.postDelayed({
                    animation.addListener(this)
                    animation.start()
                }, repeatDelay)
            }
        })

        imageView.postDelayed({
            animator.start()
        }, initialDelay)
    }

}
