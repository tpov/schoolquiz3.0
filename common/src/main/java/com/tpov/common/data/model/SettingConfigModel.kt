package com.tpov.common.data.model
data class SettingConfigModel(
    var tpovId: Int,
    var login: String,
    var password: String,
    var name: String,
    var nickname: String,
    var birthday: String,
    var city: String,
    var logo: Int,
    var languages: String,
    var profileSyncFrequency: String,
    var questsSyncFrequency: String,
    var notificationsEnabled: Boolean,
    var eventNotificationsFrequency: String,
    var lessonsFrequencyTime: String,
    var lessonsFrequencyDays: Set<String>
) {
    companion object {
        fun default() = SettingConfigModel(0,"", "", "", "", "", "", -1, "", "1", "1", false, "1", "00:00", emptySet())

        fun fromMap(map: Map<String, Any>): SettingConfigModel {
            return SettingConfigModel(
                tpovId = map["tpovId"] as Int,
                login = map["login"] as? String ?: "",
                password = map["password"] as? String ?: "",
                name = map["name"] as? String ?: "",
                nickname = map["nickname"] as? String ?: "",
                birthday = map["birthday"] as? String ?: "",
                city = map["city"] as? String ?: "",
                logo = (map["logo"] as? Long)?.toInt() ?: -1, // Firebase может возвращать Int как Long
                languages = map["languages"] as? String ?: "",
                profileSyncFrequency = map["profileSyncFrequency"] as? String ?: "1",
                questsSyncFrequency = map["questsSyncFrequency"] as? String ?: "1",
                notificationsEnabled = map["notificationsEnabled"] as? Boolean ?: false,
                eventNotificationsFrequency = map["eventNotificationsFrequency"] as? String ?: "1",
                lessonsFrequencyTime = map["lessonsFrequencyTime"] as? String ?: "00:00",
                lessonsFrequencyDays = (map["lessonsFrequencyDays"] as? List<String>)?.toSet() ?: emptySet()
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
            "birthday" to birthday,
            "city" to city,
            "logo" to logo,
            "languages" to languages,
            "profileSyncFrequency" to profileSyncFrequency,
            "questsSyncFrequency" to questsSyncFrequency,
            "notificationsEnabled" to notificationsEnabled,
            "eventNotificationsFrequency" to eventNotificationsFrequency,
            "lessonsFrequencyTime" to lessonsFrequencyTime,
            "lessonsFrequencyDays" to lessonsFrequencyDays.toList()
        )
    }
}