package com.tpov.setting.presentation

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.tpov.setting.R
import com.tpov.setting.data.PreferencesManager

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var preferencesManager: PreferencesManager
    
    // Settings Cards
    private lateinit var profileCard: CardView
    private lateinit var securityCard: CardView 
    private lateinit var notificationsCard: CardView
    private lateinit var scheduleCard: CardView
    private lateinit var aboutCard: CardView
    private lateinit var exportCard: CardView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        
        preferencesManager = PreferencesManager(this)
        
        setupToolbar()
        initViews()
        setupClickListeners()
        updateCardStates()
    }
    
    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            title = getString(R.string.settings_title)
            setDisplayHomeAsUpEnabled(true)
            elevation = 0f
        }

        // Set status bar color to match modern theme
        window.statusBarColor = ContextCompat.getColor(this, R.color.app_background_dark)
    }
    
    private fun initViews() {
        profileCard = findViewById(R.id.card_profile_settings)
        securityCard = findViewById(R.id.card_security_settings)
        notificationsCard = findViewById(R.id.card_notifications_settings)
        scheduleCard = findViewById(R.id.card_schedule_settings)
        aboutCard = findViewById(R.id.card_about_settings)
        exportCard = findViewById(R.id.card_export_settings)
    }
    
    private fun setupClickListeners() {
        profileCard.setOnClickListener {
            openProfileSettings()
        }
        
        securityCard.setOnClickListener {
            openSecuritySettings()
        }
        
        notificationsCard.setOnClickListener {
            openNotificationSettings()
        }
        
        scheduleCard.setOnClickListener {
            openScheduleSettings()
        }
        
        aboutCard.setOnClickListener {
            openAboutSettings()
        }
        
        exportCard.setOnClickListener {
            openExportSettings()
        }
        
        // Note: No back/save buttons in main card view - navigation handled by ActionBar
    }
    
    private fun updateCardStates() {
        // Update cards based on current settings
        val settings = preferencesManager.getSettings()
        
        // Update profile card status
        val hasProfile = settings.login.isNotEmpty() && settings.name.isNotEmpty()
        updateCardState(profileCard, hasProfile)
        
        // Update security card status  
        val hasPassword = settings.password.isNotEmpty()
        updateCardState(securityCard, hasPassword)
        
        // Update notifications card status
        updateCardState(notificationsCard, settings.notificationsEnabled)
        
        // Update schedule card status
        val hasSchedule = settings.lessonsAlarmTime.isNotEmpty()
        updateCardState(scheduleCard, hasSchedule)
    }
    
    private fun updateCardState(card: CardView, isConfigured: Boolean) {
        if (isConfigured) {
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.shop_card_configured))
            card.elevation = 6f
        } else {
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.shop_card_default))
            card.elevation = 4f
        }
    }
    
    private fun openProfileSettings() {
                    // Launch SettingsFragment with profile focus
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment.newInstance())
            .addToBackStack("profile")
            .commit()
        
        showToast("Профиль")
    }
    
    private fun openSecuritySettings() {
        showToast("Безопасность")
    }
    
    private fun openNotificationSettings() {
        showToast("Уведомления")
    }
    
    private fun openScheduleSettings() {
        showToast("Расписание")
    }
    
    private fun openAboutSettings() {
        showToast("О приложении")
    }
    
    private fun openExportSettings() {
        showToast("Экспорт данных")
    }
    
    private fun saveAllSettings() {
        showToast("Настройки сохранены")
        finish()
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
