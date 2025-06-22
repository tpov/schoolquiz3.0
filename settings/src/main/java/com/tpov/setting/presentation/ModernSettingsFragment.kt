package com.tpov.setting.presentation

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.tpov.setting.R
import com.tpov.setting.data.PreferencesManager
import java.util.*

class ModernSettingsFragment : Fragment() {

    private lateinit var preferencesManager: PreferencesManager
    
    // Text views for displaying current values
    private lateinit var tvLoginValue: TextView
    private lateinit var tvPasswordValue: TextView
    private lateinit var tvNameValue: TextView
    private lateinit var tvNicknameValue: TextView
    private lateinit var tvBirthdayValue: TextView
    private lateinit var tvCityValue: TextView
    private lateinit var tvProfileSyncValue: TextView
    private lateinit var tvQuestsSyncValue: TextView
    private lateinit var tvScheduleTime: TextView
    
    // UI components
    private lateinit var switchNotifications: Switch

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_modern_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        preferencesManager = PreferencesManager(requireContext())
        
        initViews(view)
        setupClickListeners(view)
        loadCurrentSettings()
    }

    private fun initViews(view: View) {
        // Profile information text views
        tvLoginValue = view.findViewById(R.id.tv_login_value)
        tvPasswordValue = view.findViewById(R.id.tv_password_value)
        tvNameValue = view.findViewById(R.id.tv_name_value)
        tvNicknameValue = view.findViewById(R.id.tv_nickname_value)
        tvBirthdayValue = view.findViewById(R.id.tv_birthday_value)
        tvCityValue = view.findViewById(R.id.tv_city_value)
        
        // Sync text views
        tvProfileSyncValue = view.findViewById(R.id.tv_profile_sync_value)
        tvQuestsSyncValue = view.findViewById(R.id.tv_quests_sync_value)
        
        // Notifications
        switchNotifications = view.findViewById(R.id.switch_notifications)
        tvScheduleTime = view.findViewById(R.id.tv_schedule_time)
    }

    private fun setupClickListeners(view: View) {
        // Profile settings click listeners
        view.findViewById<View>(R.id.setting_login).setOnClickListener {
            showEditDialog("Логин", tvLoginValue.text.toString()) { newValue ->
                saveLogin(newValue)
            }
        }
        
        view.findViewById<View>(R.id.setting_password).setOnClickListener {
            showEditDialog("Пароль", "", true) { newValue ->
                savePassword(newValue)
            }
        }
        
        view.findViewById<View>(R.id.setting_name).setOnClickListener {
            showEditDialog("Имя", tvNameValue.text.toString()) { newValue ->
                saveName(newValue)
            }
        }
        
        view.findViewById<View>(R.id.setting_nickname).setOnClickListener {
            showEditDialog("Никнейм", tvNicknameValue.text.toString()) { newValue ->
                saveNickname(newValue)
            }
        }
        
        view.findViewById<View>(R.id.setting_birthday).setOnClickListener {
            showEditDialog("День рождения", tvBirthdayValue.text.toString()) { newValue ->
                saveBirthday(newValue)
            }
        }
        
        view.findViewById<View>(R.id.setting_city).setOnClickListener {
            showEditDialog("Город", tvCityValue.text.toString()) { newValue ->
                saveCity(newValue)
            }
        }
        
        // Sync settings (placeholder for now)
        view.findViewById<View>(R.id.setting_profile_sync).setOnClickListener {
            showToast("Настройки синхронизации профиля - в разработке")
        }
        
        view.findViewById<View>(R.id.setting_quests_sync).setOnClickListener {
            showToast("Настройки синхронизации квестов - в разработке")
        }
        
        // Schedule time
        view.findViewById<View>(R.id.setting_lessons_time).setOnClickListener {
            showTimePickerDialog()
        }
        
        // Notifications switch
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationSettings(isChecked)
        }
    }

    private fun loadCurrentSettings() {
        try {
            val settings = preferencesManager.getSettings()
            
            // Load profile information
            tvLoginValue.text = if (settings.login.isNotEmpty()) settings.login else "Не установлен"
            tvPasswordValue.text = if (settings.password.isNotEmpty()) "••••••••" else "Не установлен"
            tvNameValue.text = if (settings.name.isNotEmpty()) settings.name else "Не установлено"
            tvNicknameValue.text = if (settings.nickname.isNotEmpty()) settings.nickname else "Не установлен"
            tvBirthdayValue.text = if (settings.birthday.isNotEmpty()) settings.birthday else "Не установлен"
            tvCityValue.text = if (settings.city.isNotEmpty()) settings.city else "Не установлен"
            
            // Load notification settings
            switchNotifications.isChecked = settings.notificationsEnabled
            
            // Load schedule time
            tvScheduleTime.text = if (settings.lessonsAlarmTime.isNotEmpty()) {
                settings.lessonsAlarmTime
            } else {
                "00:00"
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Ошибка загрузки настроек")
        }
    }

    private fun showEditDialog(title: String, currentValue: String, isPassword: Boolean = false, onSave: (String) -> Unit) {
        val editText = EditText(requireContext()).apply {
            setText(if (isPassword) "" else currentValue)
            if (isPassword) {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(editText)
            .setPositiveButton("Сохранить") { _, _ ->
                val newValue = editText.text.toString()
                if (newValue.isNotEmpty()) {
                    onSave(newValue)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun saveLogin(login: String) {
        try {
            val currentSettings = preferencesManager.getSettings()
            val updatedSettings = currentSettings.copy(login = login)
            preferencesManager.saveSettings(updatedSettings)
            tvLoginValue.text = login
            showToast("Логин сохранен")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Ошибка сохранения логина")
        }
    }

    private fun savePassword(password: String) {
        try {
            val currentSettings = preferencesManager.getSettings()
            val updatedSettings = currentSettings.copy(password = password)
            preferencesManager.saveSettings(updatedSettings)
            tvPasswordValue.text = "••••••••"
            showToast("Пароль сохранен")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Ошибка сохранения пароля")
        }
    }

    private fun saveName(name: String) {
        try {
            val currentSettings = preferencesManager.getSettings()
            val updatedSettings = currentSettings.copy(name = name)
            preferencesManager.saveSettings(updatedSettings)
            tvNameValue.text = name
            showToast("Имя сохранено")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Ошибка сохранения имени")
        }
    }

    private fun saveNickname(nickname: String) {
        try {
            val currentSettings = preferencesManager.getSettings()
            val updatedSettings = currentSettings.copy(nickname = nickname)
            preferencesManager.saveSettings(updatedSettings)
            tvNicknameValue.text = nickname
            showToast("Никнейм сохранен")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Ошибка сохранения никнейма")
        }
    }

    private fun saveBirthday(birthday: String) {
        try {
            val currentSettings = preferencesManager.getSettings()
            val updatedSettings = currentSettings.copy(birthday = birthday)
            preferencesManager.saveSettings(updatedSettings)
            tvBirthdayValue.text = birthday
            showToast("День рождения сохранен")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Ошибка сохранения дня рождения")
        }
    }

    private fun saveCity(city: String) {
        try {
            val currentSettings = preferencesManager.getSettings()
            val updatedSettings = currentSettings.copy(city = city)
            preferencesManager.saveSettings(updatedSettings)
            tvCityValue.text = city
            showToast("Город сохранен")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Ошибка сохранения города")
        }
    }

    private fun saveNotificationSettings(enabled: Boolean) {
        try {
            val currentSettings = preferencesManager.getSettings()
            val updatedSettings = currentSettings.copy(notificationsEnabled = enabled)
            preferencesManager.saveSettings(updatedSettings)
            showToast(if (enabled) "Уведомления включены" else "Уведомления выключены")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Ошибка сохранения настроек уведомлений")
        }
    }

    private fun saveScheduleTime(timeString: String) {
        try {
            val currentSettings = preferencesManager.getSettings()
            val updatedSettings = currentSettings.copy(lessonsAlarmTime = timeString)
            preferencesManager.saveSettings(updatedSettings)
            tvScheduleTime.text = timeString
            showToast("Время занятий сохранено: $timeString")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Ошибка сохранения времени")
        }
    }

    private fun showTimePickerDialog() {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val timeString = String.format("%02d:%02d", hourOfDay, minute)
                saveScheduleTime(timeString)
            },
            currentHour,
            currentMinute,
            true
        )
        
        timePickerDialog.show()
    }



    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun newInstance(): ModernSettingsFragment {
            return ModernSettingsFragment()
        }
    }
} 