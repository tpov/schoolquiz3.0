package com.tpov.setting.utils

import com.tpov.common.data.model.SettingConfigModel

object SettingsToProfileConverter {
    
    data class ProfileUpdateData(
        val name: String?,
        val nickname: String?,
        val birthday: String?,
        val city: String?,
        val logo: Int?,
        val login: String?,
        val languages: String?,
        val life: Int?,
        val goldLife: Int?,
        val premium: Boolean?
    )
    
    fun convertSettingsToProfileUpdate(settings: SettingConfigModel): ProfileUpdateData {
        return ProfileUpdateData(
            name = settings.name.takeIf { it.isNotEmpty() },
            nickname = settings.nickname.takeIf { it.isNotEmpty() },
            birthday = settings.birthday.takeIf { it.isNotEmpty() },
            city = settings.city.takeIf { it.isNotEmpty() },
            logo = settings.logo.takeIf { it > 0 },
            login = settings.login.takeIf { it.isNotEmpty() },
            languages = settings.languages.joinToString(",") { it.name }.takeIf { it.isNotEmpty() },
            life = settings.life.takeIf { it > 0 },
            goldLife = settings.goldLife.takeIf { it > 0 },
            premium = settings.premium
        )
    }
} 