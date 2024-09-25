package com.tpov.common.domain

import com.tpov.common.data.model.SettingConfigModel

object SettingConfigObject {
    var settingsConfig = SettingConfigModel.default()
        private set

    fun updateSettings(newSettings: SettingConfigModel) {
        settingsConfig = newSettings
    }
}