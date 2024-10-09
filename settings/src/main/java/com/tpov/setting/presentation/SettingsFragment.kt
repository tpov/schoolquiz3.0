package com.tpov.setting.presentation

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.tpov.common.data.core.Core.tpovId
import com.tpov.common.data.model.SettingConfigModel
import com.tpov.setting.R
import com.tpov.setting.data.PreferencesManager
import com.tpov.setting.domain.SettingsDomain

internal class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var settingsDomain: SettingsDomain

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        setHasOptionsMenu(true)

        preferencesManager = PreferencesManager(requireContext())
        settingsDomain = SettingsDomain(preferencesManager)

        val settings = settingsDomain.getSettings()

        val saveButton = findPreference<Preference>(getString(R.string.key_save))
        saveButton?.setOnPreferenceClickListener {
            savePreferences()
            true
        }

        findPreference<EditTextPreference>(getString(R.string.key_login))?.text = settings.login
        findPreference<EditTextPreference>(getString(R.string.key_password))?.text = settings.password
        findPreference<EditTextPreference>(getString(R.string.key_name))?.text = settings.name
        findPreference<EditTextPreference>(getString(R.string.key_nickname))?.text = settings.nickname
        findPreference<EditTextPreference>(getString(R.string.key_birthday))?.text = settings.birthday
        findPreference<EditTextPreference>(getString(R.string.key_city))?.text = settings.city
        findPreference<EditTextPreference>(getString(R.string.key_logo))?.text = settings.logo.toString()
        findPreference<EditTextPreference>(getString(R.string.key_languages))?.text = settings.languages
        findPreference<ListPreference>(getString(R.string.key_profile_sync_frequency))?.value = settings.profileSyncFrequency
        findPreference<ListPreference>(getString(R.string.key_quests_sync_frequency))?.value = settings.questsSyncFrequency
        findPreference<SwitchPreferenceCompat>(getString(R.string.key_notifications))?.isChecked = settings.notificationsEnabled
        findPreference<ListPreference>(getString(R.string.key_event_notifications_frequency))?.value =settings.eventNotificationsFrequency
        findPreference<TimePickerPreference>(getString(R.string.key_lessons_frequency_time))?.time = settings.lessonsFrequencyTime
        findPreference<MultiSelectListPreference>(getString(R.string.key_lessons_frequency_days))?.values = settings.lessonsFrequencyDays
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is TimePickerPreference) {
            val dialogFragment =
                TimePickerPreferenceDialogFragmentCompat.newInstance(preference.key)
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(parentFragmentManager, "TimePickerDialog")
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.settings_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                savePreferences()
                true
            }

            R.id.action_cancel -> {
                activity?.onBackPressed()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun savePreferences() {
        val defaultConfig = SettingConfigModel.default()

        val login =
            findPreference<EditTextPreference>(getString(R.string.key_login))?.text ?: defaultConfig.login
        val password = findPreference<EditTextPreference>(getString(R.string.key_password))?.text
            ?: defaultConfig.password
        val name =
            findPreference<EditTextPreference>(getString(R.string.key_name))?.text ?: defaultConfig.name
        val nickname = findPreference<EditTextPreference>(getString(R.string.key_nickname))?.text
            ?: defaultConfig.nickname
        val nicknameColor = findPreference<EditTextPreference>(getString(R.string.key_nickname_color))?.text?.toIntOrNull()
            ?: defaultConfig.nicknameColor
        val birthday = findPreference<EditTextPreference>(getString(R.string.key_birthday))?.text
            ?: defaultConfig.birthday
        val city =
            findPreference<EditTextPreference>(getString(R.string.key_city))?.text ?: defaultConfig.city
        val logo = findPreference<EditTextPreference>(getString(R.string.key_logo))?.text?.toIntOrNull()
            ?: defaultConfig.logo
        val languages = findPreference<EditTextPreference>(getString(R.string.key_languages))?.text
            ?: defaultConfig.languages
        val profileSyncFrequency =
            findPreference<ListPreference>(getString(R.string.key_profile_sync_frequency))?.value
                ?: defaultConfig.profileSyncFrequency
        val questsSyncFrequency =
            findPreference<ListPreference>(getString(R.string.key_quests_sync_frequency))?.value
                ?: defaultConfig.questsSyncFrequency
        val notificationsEnabled =
            findPreference<SwitchPreferenceCompat>(getString(R.string.key_notifications))?.isChecked
                ?: defaultConfig.notificationsEnabled
        val eventNotificationsFrequency =
            findPreference<ListPreference>(getString(R.string.key_event_notifications_frequency))?.value
                ?: defaultConfig.eventNotificationsFrequency
        val lessonsFrequencyTime =
            findPreference<TimePickerPreference>(getString(R.string.key_lessons_frequency_time))?.time
                ?: defaultConfig.lessonsFrequencyTime
        val lessonsFrequencyDays =
            findPreference<MultiSelectListPreference>(getString(R.string.key_lessons_frequency_days))?.values
                ?: defaultConfig.lessonsFrequencyDays

        val settings = SettingConfigModel(
            tpovId,
            login,
            password,
            name,
            nicknameColor,
            nickname,
            birthday,
            city,
            logo,
            languages,
            profileSyncFrequency,
            questsSyncFrequency,
            notificationsEnabled,
            eventNotificationsFrequency,
            lessonsFrequencyTime,
            lessonsFrequencyDays
        )

        settingsDomain.saveSettings(settings)
    }
}