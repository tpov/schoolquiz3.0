package com.tpov.common.domain.usecase

import com.tpov.common.data.model.SettingConfigModel

object SettingConfigObject {
    var settingsConfig = SettingConfigModel.defaultMiddle()
        private set

    fun updateSettings(newSettings: SettingConfigModel) {
        settingsConfig = newSettings
    }
}