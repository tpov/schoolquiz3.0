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

class SettingsFragment : Fragment() {

    private lateinit var preferencesManager: PreferencesManager
    private var profileSyncCallback: ProfileSyncInterface? = null
    
    // Text views for displaying current values
    private lateinit var tvLoginValue: TextView
    private lateinit var tvPasswordValue: TextView
    private lateinit var tvNameValue: TextView
    private lateinit var tvNicknameValue: TextView
    private lateinit var tvBirthdayValue: TextView
    private lateinit var tvCityValue: TextView
    private lateinit var tvLanguagesValue: TextView
    private lateinit var tvLifeValue: TextView
    private lateinit var tvGoldLifeValue: TextView
    private lateinit var tvProfileSyncValue: TextView
    private lateinit var tvQuestsSyncValue: TextView
    private lateinit var tvEventNotificationsFrequencyValue: TextView
    private lateinit var tvScheduleTime: TextView
    private lateinit var tvLessonsDaysValue: TextView
    
    // UI components
    private lateinit var switchNotifications: Switch
    private lateinit var switchPremium: Switch

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
        tvLanguagesValue = view.findViewById(R.id.tv_languages_value)
        
        // Game stats text views
        tvLifeValue = view.findViewById(R.id.tv_life_value)
        tvGoldLifeValue = view.findViewById(R.id.tv_gold_life_value)
        
        // Sync text views
        tvProfileSyncValue = view.findViewById(R.id.tv_profile_sync_value)
        tvQuestsSyncValue = view.findViewById(R.id.tv_quests_sync_value)
        
        // Notifications text views
        tvEventNotificationsFrequencyValue = view.findViewById(R.id.tv_event_notifications_frequency_value)
        tvScheduleTime = view.findViewById(R.id.tv_schedule_time)
        tvLessonsDaysValue = view.findViewById(R.id.tv_lessons_days_value)
        
        // Switches
        switchNotifications = view.findViewById(R.id.switch_notifications)
        switchPremium = view.findViewById(R.id.switch_premium)
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
        
        view.findViewById<View>(R.id.setting_languages).setOnClickListener {
            showLanguagesDialog()
        }
        
        // Game stats settings
        view.findViewById<View>(R.id.setting_life).setOnClickListener {
            showEditDialog("Жизни", tvLifeValue.text.toString()) { newValue ->
                saveLife(newValue.toIntOrNull() ?: 0)
            }
        }
        
        view.findViewById<View>(R.id.setting_gold_life).setOnClickListener {
            showEditDialog("Золотые жизни", tvGoldLifeValue.text.toString()) { newValue ->
                saveGoldLife(newValue.toIntOrNull() ?: 0)
            }
        }
        
        // Sync settings 
        view.findViewById<View>(R.id.setting_profile_sync).setOnClickListener {
            showFrequencyDialog("Синхронизация профиля", 
                getCurrentFrequencyText(preferencesManager.getSettings().profileSyncFrequency)) { frequency ->
                saveProfileSyncFrequency(frequency)
            }
        }
        
        view.findViewById<View>(R.id.setting_quests_sync).setOnClickListener {
            showFrequencyDialog("Синхронизация квестов",
                getCurrentFrequencyText(preferencesManager.getSettings().questsSyncFrequency)) { frequency ->
                saveQuestsSyncFrequency(frequency)
            }
        }
        
        // Event notifications frequency
        view.findViewById<View>(R.id.setting_event_notifications_frequency).setOnClickListener {
            showFrequencyDialog("Частота уведомлений о событиях",
                getCurrentFrequencyText(preferencesManager.getSettings().eventNotificationsFrequency)) { frequency ->
                saveEventNotificationsFrequency(frequency)
            }
        }
        
        // Schedule time
        view.findViewById<View>(R.id.setting_lessons_time).setOnClickListener {
            showTimePickerDialog()
        }
        
        // Schedule days
        view.findViewById<View>(R.id.setting_lessons_days).setOnClickListener {
            showDaysPickerDialog()
        }
        
        // Notifications switch
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationSettings(isChecked)
        }
        
        // Premium switch
        switchPremium.setOnCheckedChangeListener { _, isChecked ->
            savePremiumStatus(isChecked)
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
            tvLanguagesValue.text = if (settings.languages.isNotEmpty()) {
                settings.languages.joinToString(", ") { it.name }
            } else {
                "English"
            }
            
            // Load game stats
            tvLifeValue.text = settings.life.toString()
            tvGoldLifeValue.text = settings.goldLife.toString()
            switchPremium.isChecked = settings.premium
            
            // Load sync settings
            tvProfileSyncValue.text = getCurrentFrequencyText(settings.profileSyncFrequency)
            tvQuestsSyncValue.text = getCurrentFrequencyText(settings.questsSyncFrequency)
            
            // Load notification settings
            switchNotifications.isChecked = settings.notificationsEnabled
            tvEventNotificationsFrequencyValue.text = getCurrentFrequencyText(settings.eventNotificationsFrequency)
            
            // Load schedule settings
            tvScheduleTime.text = if (settings.lessonsAlarmTime.isNotEmpty()) {
                settings.lessonsAlarmTime
            } else {
                "00:00"
            }
            
            tvLessonsDaysValue.text = if (settings.lessonsAlarmDays.isNotEmpty()) {
                settings.lessonsAlarmDays.joinToString(", ")
            } else {
                "Не выбраны"
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

    fun setProfileSyncCallback(callback: ProfileSyncInterface) {
        this.profileSyncCallback = callback
    }

    private fun saveLogin(login: String) {
        try {
            val currentSettings = preferencesManager.getSettings()
            val updatedSettings = currentSettings.copy(login = login)
            preferencesManager.saveSettings(updatedSettings)
            tvLoginValue.text = login
            // Синхронизируем с основным профилем
            profileSyncCallback?.syncProfileWithSettings(updatedSettings)
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
            // Синхронизируем с основным профилем
            profileSyncCallback?.syncProfileWithSettings(updatedSettings)
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
            // Синхронизируем с основным профилем
            profileSyncCallback?.syncProfileWithSettings(updatedSettings)
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
            // Синхронизируем с основным профилем
            profileSyncCallback?.syncProfileWithSettings(updatedSettings)
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
            // Синхронизируем с основным профилем
            profileSyncCallback?.syncProfileWithSettings(updatedSettings)
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
            // Синхронизируем с основным профилем
            profileSyncCallback?.syncProfileWithSettings(updatedSettings)
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
            // Синхронизируем с основным профилем
            profileSyncCallback?.syncProfileWithSettings(updatedSettings)
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
            // Синхронизируем с основным профилем
            profileSyncCallback?.syncProfileWithSettings(updatedSettings)
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



    private fun saveLife(life: Int) {
        try {
            val currentSettings = preferencesManager.getSettings()
            val updatedSettings = currentSettings.copy(life = life)
            preferencesManager.saveSettings(updatedSettings)
            tvLifeValue.text = life.toString()
            // Синхронизируем с основным профилем
            profileSyncCallback?.syncProfileWithSettings(updatedSettings)
            showToast("Жизни сохранены")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Ошибка сохранения жизней")
        }
    }

    private fun saveGoldLife(goldLife: Int) {
        try {
            val currentSettings = preferencesManager.getSettings()
            val updatedSettings = currentSettings.copy(goldLife = goldLife)
            preferencesManager.saveSettings(updatedSettings)
            tvGoldLifeValue.text = goldLife.toString()
            // Синхронизируем с основным профилем
            profileSyncCallback?.syncProfileWithSettings(updatedSettings)
            showToast("Золотые жизни сохранены")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Ошибка сохранения золотых жизней")
        }
    }

    private fun savePremiumStatus(premium: Boolean) {
        try {
            val currentSettings = preferencesManager.getSettings()
            val updatedSettings = currentSettings.copy(premium = premium)
            preferencesManager.saveSettings(updatedSettings)
            // Синхронизируем с основным профилем
            profileSyncCallback?.syncProfileWithSettings(updatedSettings)
            showToast(if (premium) "Премиум статус активирован" else "Премиум статус деактивирован")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Ошибка сохранения премиум статуса")
        }
    }

    private fun saveProfileSyncFrequency(frequency: Int) {
        try {
            val currentSettings = preferencesManager.getSettings()
            val updatedSettings = currentSettings.copy(profileSyncFrequency = frequency)
            preferencesManager.saveSettings(updatedSettings)
            tvProfileSyncValue.text = getCurrentFrequencyText(frequency)
            // Синхронизируем с основным профилем
            profileSyncCallback?.syncProfileWithSettings(updatedSettings)
            showToast("Частота синхронизации профиля сохранена")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Ошибка сохранения частоты синхронизации")
        }
    }

    private fun saveQuestsSyncFrequency(frequency: Int) {
        try {
            val currentSettings = preferencesManager.getSettings()
            val updatedSettings = currentSettings.copy(questsSyncFrequency = frequency)
            preferencesManager.saveSettings(updatedSettings)
            tvQuestsSyncValue.text = getCurrentFrequencyText(frequency)
            // Синхронизируем с основным профилем
            profileSyncCallback?.syncProfileWithSettings(updatedSettings)
            showToast("Частота синхронизации квестов сохранена")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Ошибка сохранения частоты синхронизации")
        }
    }

    private fun saveEventNotificationsFrequency(frequency: Int) {
        try {
            val currentSettings = preferencesManager.getSettings()
            val updatedSettings = currentSettings.copy(eventNotificationsFrequency = frequency)
            preferencesManager.saveSettings(updatedSettings)
            tvEventNotificationsFrequencyValue.text = getCurrentFrequencyText(frequency)
            // Синхронизируем с основным профилем
            profileSyncCallback?.syncProfileWithSettings(updatedSettings)
            showToast("Частота уведомлений о событиях сохранена")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Ошибка сохранения частоты уведомлений")
        }
    }

    private fun saveLessonsDays(days: Set<String>) {
        try {
            val currentSettings = preferencesManager.getSettings()
            val updatedSettings = currentSettings.copy(lessonsAlarmDays = days)
            preferencesManager.saveSettings(updatedSettings)
            tvLessonsDaysValue.text = if (days.isNotEmpty()) {
                days.joinToString(", ")
            } else {
                "Не выбраны"
            }
            // Синхронизируем с основным профилем
            profileSyncCallback?.syncProfileWithSettings(updatedSettings)
            showToast("Дни занятий сохранены")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Ошибка сохранения дней занятий")
        }
    }

    private fun getCurrentFrequencyText(frequency: Int): String {
        return when (frequency) {
            1 -> "Каждый день"
            2 -> "Каждые 2 дня"
            3 -> "Каждые 3 дня"
            4 -> "Каждые 4 дня"
            7 -> "Раз в неделю"
            14 -> "Раз в 2 недели"
            30 -> "Раз в месяц"
            else -> "Каждый день"
        }
    }

    private fun showFrequencyDialog(title: String, currentValue: String, onSave: (Int) -> Unit) {
        val frequencies = arrayOf("Каждый день", "Каждые 2 дня", "Каждые 3 дня", "Каждые 4 дня", 
                                  "Раз в неделю", "Раз в 2 недели", "Раз в месяц")
        val frequencyValues = arrayOf(1, 2, 3, 4, 7, 14, 30)
        
        val currentIndex = frequencies.indexOf(currentValue)
        
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(frequencies, currentIndex) { dialog, which ->
                onSave(frequencyValues[which])
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showLanguagesDialog() {
        showToast("Настройка языков - в разработке")
    }

    private fun showDaysPickerDialog() {
        val days = arrayOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
        val dayValues = arrayOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        val currentDays = preferencesManager.getSettings().lessonsAlarmDays
        val checkedItems = dayValues.map { currentDays.contains(it) }.toBooleanArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle("Выберите дни занятий")
            .setMultiChoiceItems(days, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Сохранить") { _, _ ->
                val selectedDays = mutableSetOf<String>()
                checkedItems.forEachIndexed { index, isChecked ->
                    if (isChecked) {
                        selectedDays.add(dayValues[index])
                    }
                }
                saveLessonsDays(selectedDays)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun newInstance(): SettingsFragment {
            return SettingsFragment()
        }
        
        fun newInstance(profileSyncCallback: ProfileSyncInterface): SettingsFragment {
            return SettingsFragment().apply {
                setProfileSyncCallback(profileSyncCallback)
            }
        }
    }
} 