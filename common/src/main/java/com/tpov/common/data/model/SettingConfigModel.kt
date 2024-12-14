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
    var languages: String = "",
    var profileSyncFrequency: Int, //count in month
    var questsSyncFrequency: Int,
    var notificationsEnabled: Boolean = true,
    var eventNotificationsFrequency: Int,
    var lessonsAlarmTime: String = "00:00",
    var lessonsAlarmDays: Set<String> = emptySet()
) {

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
                tpovId = map["tpovId"] as Int,
                login = map["login"] as? String ?: this.login,
                password = map["password"] as? String ?: this.password,
                name = map["name"] as? String ?: this.name,
                nickname = map["nickname"] as? String ?: this.nickname,
                nicknameColor = map["nicknameColor"] as? Int ?: this.nicknameColor,
                birthday = map["birthday"] as? String ?: this.birthday,
                city = map["city"] as? String ?: this.city,
                logo = (map["logo"] as? Long)?.toInt() ?: this.logo,
                languages = map["languages"] as? String ?: "",
                profileSyncFrequency = map["profileSyncFrequency"] as? Int ?: defaultMiddle().profileSyncFrequency,
                questsSyncFrequency = map["questsSyncFrequency"] as? Int ?: defaultMiddle().questsSyncFrequency,
                notificationsEnabled = map["notificationsEnabled"] as? Boolean ?: this.notificationsEnabled,
                eventNotificationsFrequency = map["eventNotificationsFrequency"] as? Int ?: defaultMiddle().eventNotificationsFrequency,
                lessonsAlarmTime = map["lessonsAlarmTime"] as? String ?: "00:00",
                lessonsAlarmDays = (map["lessonsAlarmDays"] as? List<String>)?.toSet() ?: emptySet()
            )
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "tpovId" to this.tpovId,
            "login" to this.login,
            "password" to this.password,
            "name" to this.name,
            "nickname" to this.nickname,
            "nicknameColor" to this.nicknameColor,
            "birthday" to this.birthday,
            "city" to this.city,
            "logo" to this.logo,
            "languages" to this.languages,
            "profileSyncFrequency" to this.profileSyncFrequency,
            "questsSyncFrequency" to this.questsSyncFrequency,
            "notificationsEnabled" to this.notificationsEnabled,
            "eventNotificationsFrequency" to this.eventNotificationsFrequency,
            "lessonsFrequencyTime" to this.lessonsFrequencyTime,
            "lessonsFrequencyDays" to this.lessonsFrequencyDays.toList()
        )
}