package com.tpov.common.data.model

data class SettingConfigModel(
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
        internal fun default() = SettingConfigModel("", "", "", "", "", "", -1, "", "1", "1", false, "1", "00:00", emptySet())
    }
}