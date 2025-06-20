package com.tpov.common.presentation.utils

import com.tpov.common.domain.usecase.SettingConfigObject.settingsConfig

class NamesUtils {
    fun getPathPicture(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val randomString = (1..5)
            .map { chars.random() }
            .joinToString("")
        return "${settingsConfig.tpovId}_$randomString.jpg"
    }
}
