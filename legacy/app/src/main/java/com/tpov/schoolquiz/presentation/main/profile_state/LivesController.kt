package com.tpov.schoolquiz.presentation.main.profile_state

import android.content.Context
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import androidx.core.content.ContextCompat
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.presentation.model.LivesState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Timer
import java.util.TimerTask

/**
 * Класс для управления жизнями пользователя
 */

class LivesController(private val context: Context) {

    companion object {
        private const val LIFE_POINTS_PER_LIFE = 100
        private const val RECOVERY_RATE = 1
    }

    private var timer: Timer? = null
    private var timerTask: TimerTask? = null

    private var standardLife = 0
    private var standardHearts = 0
    private var goldLife = 0
    private var goldHearts = 0

    private val _livesState = MutableStateFlow(LivesState())
    val livesState = _livesState.asStateFlow()

    fun startTimer(
        standardLife: Int,
        standardHearts: Int,
        goldLife: Int,
        goldHearts: Int,
        lastUpdateTime: Long
    ) {
        this.standardLife = standardLife
        this.standardHearts = standardHearts
        this.goldLife = goldLife
        this.goldHearts = goldHearts

        calculateAndUpdateLives(lastUpdateTime)

        timer = Timer()
        timerTask = object : TimerTask() {
            override fun run() {
                val currentTime = System.currentTimeMillis() / 1000
                val tenSecondsAgo = currentTime - 10

                Handler(Looper.getMainLooper()).post {
                    calculateAndUpdateLives(tenSecondsAgo)
                }
            }
        }

        timer?.schedule(timerTask, 10000, 10000)
    }

    fun stopTimer() {
        timerTask?.cancel()
        timer?.cancel()
        timer = null
        timerTask = null
    }

    private fun calculateAndUpdateLives(fromTime: Long) {
        val currentTime = System.currentTimeMillis() / 1000
        val elapsedSeconds = (currentTime - fromTime).coerceAtLeast(0)

        val maxStandardLife = standardHearts * LIFE_POINTS_PER_LIFE
        val maxGoldLife = goldHearts * LIFE_POINTS_PER_LIFE

        val newStandardLife = (standardLife + (elapsedSeconds * RECOVERY_RATE))
            .coerceAtMost(maxStandardLife.toLong())
            .toInt()

        val newGoldLife = (goldLife + (elapsedSeconds * RECOVERY_RATE))
            .coerceAtMost(maxGoldLife.toLong())
            .toInt()

        if (newStandardLife != standardLife || newGoldLife != goldLife) {
            standardLife = newStandardLife
            goldLife = newGoldLife

            _livesState.value = LivesState(
                standardLife = standardLife,
                standardHearts = standardHearts,
                goldLife = goldLife,
                goldHearts = goldHearts,
                updateTime = currentTime
            )
        }
    }

    fun createHeartDrawable(lifePoints: Int, heartIndex: Int, isGold: Boolean): LayerDrawable {
        val emptyResId = R.drawable.baseline_favorite_24_empty
        val filledResId = if (isGold) {
            R.drawable.baseline_favorite_24_gold
        } else {
            R.drawable.baseline_favorite_24
        }

        val fullHeartPoints = LIFE_POINTS_PER_LIFE
        val maxPointsForHeart = (heartIndex + 1) * fullHeartPoints
        val minPointsForHeart = heartIndex * fullHeartPoints

        val heartPoints = when {
            lifePoints >= maxPointsForHeart -> fullHeartPoints
            lifePoints > minPointsForHeart -> lifePoints - minPointsForHeart
            else -> 0
        }

        val emptyDrawable = ContextCompat.getDrawable(context, emptyResId)
        val filledDrawable = ClipDrawable(
            ContextCompat.getDrawable(context, filledResId),
            Gravity.LEFT,
            ClipDrawable.HORIZONTAL
        )

        return LayerDrawable(arrayOf(emptyDrawable, filledDrawable)).apply {
            setDrawableByLayerId(0, emptyDrawable)
            setDrawableByLayerId(1, filledDrawable)
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)

            val fillLevel = ((heartPoints * 10000) / fullHeartPoints).toInt()
            level = fillLevel
        }
    }
}
