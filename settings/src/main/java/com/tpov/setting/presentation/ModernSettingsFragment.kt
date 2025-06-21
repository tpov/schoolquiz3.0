package com.tpov.setting.presentation

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.tpov.common.data.model.SettingConfigModel
import com.tpov.setting.R
import com.tpov.setting.data.PreferencesManager
import com.tpov.setting.domain.SettingsDomain

class ModernSettingsFragment : Fragment() {
    
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var settingsDomain: SettingsDomain
    
    // UI Components
    private lateinit var etLogin: EditText
    private lateinit var etPassword: EditText
    private lateinit var etName: EditText
    private lateinit var etNickname: EditText
    private lateinit var etBirthday: EditText
    private lateinit var etCity: EditText
    private lateinit var switchNotifications: Switch
    private lateinit var tvLessonsTime: TextView
    private lateinit var btnSelectTime: Button
    private lateinit var btnSaveSettings: Button
    private lateinit var btnBack: ImageButton
    
    private var selectedHour = 0
    private var selectedMinute = 0
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_modern_settings, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeComponents()
        setupClickListeners()
        loadSettings()
    }
    
    private fun initializeComponents() {
        preferencesManager = PreferencesManager(requireContext())
        settingsDomain = SettingsDomain(preferencesManager)
        
        // Initialize UI components
        etLogin = requireView().findViewById(R.id.et_login)
        etPassword = requireView().findViewById(R.id.et_password)
        etName = requireView().findViewById(R.id.et_name)
        etNickname = requireView().findViewById(R.id.et_nickname)
        etBirthday = requireView().findViewById(R.id.et_birthday)
        etCity = requireView().findViewById(R.id.et_city)
        switchNotifications = requireView().findViewById(R.id.switch_notifications)
        tvLessonsTime = requireView().findViewById(R.id.tv_lessons_time)
        btnSelectTime = requireView().findViewById(R.id.btn_select_time)
        btnSaveSettings = requireView().findViewById(R.id.btn_save_settings)
        btnBack = requireView().findViewById(R.id.btn_back)
    }
    
    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            activity?.onBackPressed()
        }
        
        btnSaveSettings.setOnClickListener {
            saveSettings()
        }
        
        btnSelectTime.setOnClickListener {
            showTimePickerDialog()
        }
    }
    
    private fun loadSettings() {
        val settings = settingsDomain.getSettings()
        
        etLogin.setText(settings.login)
        etPassword.setText(settings.password)
        etName.setText(settings.name)
        etNickname.setText(settings.nickname)
        etBirthday.setText(settings.birthday)
        etCity.setText(settings.city)
        switchNotifications.isChecked = settings.notificationsEnabled
        
        // Parse time from settings
        val timeparts = settings.lessonsAlarmTime.split(":")
        if (timeparts.size == 2) {
            selectedHour = timeparts[0].toIntOrNull() ?: 0
            selectedMinute = timeparts[1].toIntOrNull() ?: 0
            updateTimeDisplay()
        }
    }
    
    private fun showTimePickerDialog() {
        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                selectedHour = hourOfDay
                selectedMinute = minute
                updateTimeDisplay()
            },
            selectedHour,
            selectedMinute,
            true
        )
        timePickerDialog.show()
    }
    
    private fun updateTimeDisplay() {
        val timeString = String.format("%02d:%02d", selectedHour, selectedMinute)
        tvLessonsTime.text = timeString
    }
    
    private fun saveSettings() {
        val defaultConfig = SettingConfigModel.defaultMiddle()
        
        val settings = SettingConfigModel(
            tpovId = defaultConfig.tpovId,
            login = etLogin.text.toString().takeIf { it.isNotEmpty() } ?: defaultConfig.login,
            password = etPassword.text.toString().takeIf { it.isNotEmpty() } ?: defaultConfig.password,
            name = etName.text.toString().takeIf { it.isNotEmpty() } ?: defaultConfig.name,
            nicknameColor = defaultConfig.nicknameColor,
            nickname = etNickname.text.toString().takeIf { it.isNotEmpty() } ?: defaultConfig.nickname,
            birthday = etBirthday.text.toString().takeIf { it.isNotEmpty() } ?: defaultConfig.birthday,
            city = etCity.text.toString().takeIf { it.isNotEmpty() } ?: defaultConfig.city,
            logo = defaultConfig.logo,
            life = defaultConfig.life,
            goldLife = defaultConfig.goldLife,
            premium = defaultConfig.premium,
            languages = defaultConfig.languages,
            profileSyncFrequency = defaultConfig.profileSyncFrequency,
            questsSyncFrequency = defaultConfig.questsSyncFrequency,
            notificationsEnabled = switchNotifications.isChecked,
            eventNotificationsFrequency = defaultConfig.eventNotificationsFrequency,
            lessonsAlarmTime = String.format("%02d:%02d", selectedHour, selectedMinute),
            lessonsAlarmDays = defaultConfig.lessonsAlarmDays
        )
        
        settingsDomain.saveSettings(settings)
        
        Toast.makeText(requireContext(), "Настройки сохранены", Toast.LENGTH_SHORT).show()
        activity?.onBackPressed()
    }
    
    companion object {
        fun newInstance(): ModernSettingsFragment {
            return ModernSettingsFragment()
        }
    }
} 