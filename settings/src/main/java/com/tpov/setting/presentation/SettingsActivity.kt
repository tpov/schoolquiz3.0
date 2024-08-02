package com.tpov.setting.presentation

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.tpov.common.data.model.SettingConfigModel
import com.tpov.setting.R
import com.tpov.setting.data.PreferencesManager

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        supportActionBar?.apply {
            title = getString(R.string.settings_title)
            setDisplayHomeAsUpEnabled(true)
        }

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private lateinit var preferencesManager: PreferencesManager

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            setHasOptionsMenu(true)

            preferencesManager = PreferencesManager(requireContext())

            val saveButton = findPreference<Preference>(SettingsKeys.SAVE_BUTTON)
            saveButton?.setOnPreferenceClickListener {
                savePreferences()
                true
            }

            // Initialize preferences with saved values
            val settings = preferencesManager.getSettings()
            findPreference<EditTextPreference>(SettingsKeys.LOGIN)?.text = settings.login
            findPreference<EditTextPreference>(SettingsKeys.PASSWORD)?.text = settings.password
            findPreference<EditTextPreference>(SettingsKeys.NAME)?.text = settings.name
            findPreference<EditTextPreference>(SettingsKeys.NICKNAME)?.text = settings.nickname
            findPreference<EditTextPreference>(SettingsKeys.BIRTHDAY)?.text = settings.birthday
            findPreference<EditTextPreference>(SettingsKeys.CITY)?.text = settings.city
            findPreference<EditTextPreference>(SettingsKeys.LOGO)?.text = settings.logo.toString()
            findPreference<EditTextPreference>(SettingsKeys.LANGUAGES)?.text = settings.languages
            findPreference<ListPreference>(SettingsKeys.PROFILE_SYNC_FREQUENCY)?.value = settings.profileSyncFrequency
            findPreference<ListPreference>(SettingsKeys.QUESTS_SYNC_FREQUENCY)?.value = settings.questsSyncFrequency
            findPreference<SwitchPreferenceCompat>(SettingsKeys.NOTIFICATIONS)?.isChecked = settings.notificationsEnabled
            findPreference<ListPreference>(SettingsKeys.EVENT_NOTIFICATIONS_FREQUENCY)?.value = settings.eventNotificationsFrequency
            findPreference<TimePickerPreference>(SettingsKeys.LESSONS_FREQUENCY_TIME)?.time = settings.lessonsFrequencyTime
            findPreference<MultiSelectListPreference>(SettingsKeys.LESSONS_FREQUENCY_DAYS)?.values = settings.lessonsFrequencyDays
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            if (preference is TimePickerPreference) {
                val dialogFragment = TimePickerPreferenceDialogFragmentCompat.newInstance(preference.key)
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
            val login = findPreference<EditTextPreference>(SettingsKeys.LOGIN)?.text ?: ""
            val password = findPreference<EditTextPreference>(SettingsKeys.PASSWORD)?.text ?: ""
            val name = findPreference<EditTextPreference>(SettingsKeys.NAME)?.text ?: ""
            val nickname = findPreference<EditTextPreference>(SettingsKeys.NICKNAME)?.text ?: ""
            val birthday = findPreference<EditTextPreference>(SettingsKeys.BIRTHDAY)?.text ?: ""
            val city = findPreference<EditTextPreference>(SettingsKeys.CITY)?.text ?: ""
            val logo = findPreference<EditTextPreference>(SettingsKeys.LOGO)?.text?.toIntOrNull() ?: -1
            val languages = findPreference<EditTextPreference>(SettingsKeys.LANGUAGES)?.text ?: ""
            val profileSyncFrequency = findPreference<ListPreference>(SettingsKeys.PROFILE_SYNC_FREQUENCY)?.value ?: "1"
            val questsSyncFrequency = findPreference<ListPreference>(SettingsKeys.QUESTS_SYNC_FREQUENCY)?.value ?: "1"
            val notificationsEnabled = findPreference<SwitchPreferenceCompat>(SettingsKeys.NOTIFICATIONS)?.isChecked ?: false
            val eventNotificationsFrequency = findPreference<ListPreference>(SettingsKeys.EVENT_NOTIFICATIONS_FREQUENCY)?.value ?: "1"
            val lessonsFrequencyTime = findPreference<TimePickerPreference>(SettingsKeys.LESSONS_FREQUENCY_TIME)?.time ?: "00:00"
            val lessonsFrequencyDays = findPreference<MultiSelectListPreference>(SettingsKeys.LESSONS_FREQUENCY_DAYS)?.values ?: emptySet()

            val settings = SettingConfigModel(
                login,
                password,
                name,
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
            preferencesManager.saveSettings(settings)
        }
    }
}
