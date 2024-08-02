package com.tpov.common.data.core

import com.tpov.common.data.model.SettingConfigModel

object SettingConfigObject {
    private var _settings: SettingConfigModel = SettingConfigModel.default()
    var settingsConfig: SettingConfigModel
        get() = _settings
        private set(value) { _settings = value }

    fun setSettings(newSettings: SettingConfigModel) {
        settingsConfig = newSettings
    }
}