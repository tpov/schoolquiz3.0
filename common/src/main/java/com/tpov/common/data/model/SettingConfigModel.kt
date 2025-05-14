package com.tpov.common.data.model

data class SettingConfigModel(
    var tpovId: Int = 0,
    var login: String = "",
    var password: String = "",
    var name: String = "",
    val nicknameColor: Int = 1,
    var nickname: String = "",
    var birthday: String = "",
    var city: String = "",
    var logo: Int = 0,
    var premium: Boolean = false,
    var languages: String = "",
    var profileSyncFrequency: Int,
    var questsSyncFrequency: Int,
    var notificationsEnabled: Boolean = true,
    var eventNotificationsFrequency: Int,
    var lessonsAlarmTime: String = "00:00",
    var lessonsAlarmDays: Set<String> = emptySet()
) {
    companion object {
        fun defaultLight() = SettingConfigModel(
            profileSyncFrequency = 1,
            questsSyncFrequency = 1,
            eventNotificationsFrequency = 1,
        )

        fun defaultMiddle() = SettingConfigModel(
            profileSyncFrequency = 4,
            questsSyncFrequency = 4,
            eventNotificationsFrequency = 4,
        )

        fun defaultHard() = SettingConfigModel(
            profileSyncFrequency = 30,
            questsSyncFrequency = 30,
            eventNotificationsFrequency = 30,
        )

        fun fromMap(map: Map<String, Any>): SettingConfigModel {
            return SettingConfigModel(
                tpovId = map["tpovId"] as? Int ?: 0,
                login = map["login"] as? String ?: "",
                password = map["password"] as? String ?: "",
                name = map["name"] as? String ?: "",
                nicknameColor = map["nicknameColor"] as? Int ?: 1,
                nickname = map["nickname"] as? String ?: "",
                birthday = map["birthday"] as? String ?: "",
                city = map["city"] as? String ?: "",
                premium = map["premium"] as? Boolean ?: false,
                logo = (map["logo"] as? Long)?.toInt() ?: 0,
                languages = map["languages"] as? String ?: "",
                profileSyncFrequency = map["profileSyncFrequency"] as? Int ?: 1,
                questsSyncFrequency = map["questsSyncFrequency"] as? Int ?: 1,
                notificationsEnabled = map["notificationsEnabled"] as? Boolean ?: true,
                eventNotificationsFrequency = map["eventNotificationsFrequency"] as? Int ?: 1,
                lessonsAlarmTime = map["lessonsAlarmTime"] as? String ?: "00:00",
                lessonsAlarmDays = (map["lessonsAlarmDays"] as? List<String>)?.toSet() ?: emptySet()
            )
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "tpovId" to tpovId,
            "login" to login,
            "password" to password,
            "name" to name,
            "nickname" to nickname,
            "nicknameColor" to nicknameColor,
            "birthday" to birthday,
            "city" to city,
            "premium" to premium,
            "logo" to logo,
            "languages" to languages,
            "profileSyncFrequency" to profileSyncFrequency,
            "questsSyncFrequency" to questsSyncFrequency,
            "notificationsEnabled" to notificationsEnabled,
            "eventNotificationsFrequency" to eventNotificationsFrequency,
            "lessonsAlarmTime" to lessonsAlarmTime,
            "lessonsAlarmDays" to lessonsAlarmDays.toList()
        )
    }
}
