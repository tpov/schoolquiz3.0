package com.tpov.setting.presentation

import com.tpov.common.data.model.SettingConfigModel

interface ProfileSyncInterface {
    fun syncProfileWithSettings(settings: SettingConfigModel)
} 