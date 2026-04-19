package com.tpov.schoolquiz.presentation.main.profile_state

import android.content.Context
import com.tpov.common.MAX_COUNT_DAY_BOX
import com.tpov.schoolquiz.data.fierbase.Box
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


class DaysInGameController(private val context: Context) {

    private val _daysInGameState = MutableStateFlow(Box())
    val daysInGameState = _daysInGameState.asStateFlow()

    fun setDaysInGame(profileBox: Box) {
        val currentTime = System.currentTimeMillis()
        val twentyFourHoursInMillis = 24 * 60 * 60 * 1000L
        val lastLoginTime = profileBox.timeLastOpenBox.toLongOrNull()

        _daysInGameState.value = when {
            lastLoginTime == null -> {
                Box(
                    countBox = 0,
                    timeLastOpenBox = (currentTime + twentyFourHoursInMillis).toString(),
                    countDayBox = 0
                )
            }

            lastLoginTime > currentTime + twentyFourHoursInMillis -> profileBox
            lastLoginTime > currentTime -> profileBox

            else -> {
                val newConsecutiveDays = profileBox.countDayBox + 1

                val newCountBox = if (newConsecutiveDays >= MAX_COUNT_DAY_BOX) profileBox.countBox + 1
                else profileBox.countBox

                Box(
                    countBox = newCountBox,
                    timeLastOpenBox = (currentTime + twentyFourHoursInMillis).toString(),
                    countDayBox = newConsecutiveDays
                )
            }
        }
    }
}
