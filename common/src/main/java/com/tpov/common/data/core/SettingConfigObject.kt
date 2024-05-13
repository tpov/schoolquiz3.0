package com.tpov.common.data.core

import com.tpov.common.data.model.SettingConfig

object Settings {

    private var _settings: SettingConfig = SettingConfig()

    var settingsConfig: SettingConfig
        get() = _settings
        set(value) { _settings = value }

    fun setSettings(newSettings: SettingConfig) {
        settingsConfig = newSettings
    }
}
