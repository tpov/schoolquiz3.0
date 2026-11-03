package com.tpov.schoolquiz.presentation.main.profile_state

import android.content.Context
import com.tpov.common.domain.usecase.SettingConfigObject.settingsConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


class PremiumController(
    private val context: Context
) {

    private val _premiumState = MutableStateFlow("0")
    val premiumState = _premiumState.asStateFlow()

    fun setPremium(datePremium: String) {
        val currentTimeSeconds = System.currentTimeMillis() / 1000
        val premiumTimeSeconds = datePremium.toLongOrNull()

        if (premiumTimeSeconds == null) {
            _premiumState.value = "0"
            settingsConfig.premium = false
            return
        }

        val diffSeconds = premiumTimeSeconds - currentTimeSeconds
        val daysLeft = if (diffSeconds > 0) (diffSeconds / (60 * 60 * 24)) else 0

        _premiumState.value = daysLeft.toString()
        settingsConfig.premium = diffSeconds > 0
    }
}
