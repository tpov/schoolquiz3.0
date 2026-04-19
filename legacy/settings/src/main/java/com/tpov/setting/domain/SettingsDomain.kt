package com.tpov.setting.domain

import com.tpov.common.data.model.SettingConfigModel
import com.tpov.setting.data.PreferencesManager

class SettingsDomain(private val preferencesManager: PreferencesManager) {

    fun getSettings(): SettingConfigModel {
        return preferencesManager.getSettings()
    }

    fun saveSettings(settings: SettingConfigModel) {
        preferencesManager.saveSettings(settings)
    }

    fun getSettingsForRemote(): Map<String, Any> {
        val settings = getSettings()
        return settings.toMap()
    }

    fun setSettingsForRemote(map: Map<String, Any>) {
        val settings = SettingConfigModel.fromMap(map)
        saveSettings(settings)
    }
}