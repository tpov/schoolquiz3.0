package com.tpov.setting.presentation

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.tpov.setting.R

class ModernContainerActivity : AppCompatActivity() {
    
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var btnPrimaryAction: Button
    private lateinit var actionButtonsContainer: LinearLayout
    private lateinit var headerContainer: LinearLayout
    private lateinit var bottomNavigation: LinearLayout
    
    companion object {
        const val EXTRA_FRAGMENT_TYPE = "fragment_type"
        const val EXTRA_TITLE = "title"
        const val FRAGMENT_SETTINGS = "settings"
        const val FRAGMENT_SHOP = "shop"
        const val FRAGMENT_REFERRAL = "referral"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_modern_container)
        
        initializeViews()
        setupUI()
        loadFragment(savedInstanceState)
    }
    
    private fun initializeViews() {
        tvTitle = findViewById(R.id.tv_title)
        btnBack = findViewById(R.id.btn_back)
        btnPrimaryAction = findViewById(R.id.btn_primary_action)
        actionButtonsContainer = findViewById(R.id.action_buttons_container)
        headerContainer = findViewById(R.id.header_container)
        bottomNavigation = findViewById(R.id.bottom_navigation)
    }
    
    private fun setupUI() {
        btnBack.setOnClickListener {
            onBackPressed()
        }
        
        // Get title from intent
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Приложение"
        tvTitle.text = title
        
        // Setup navigation based on fragment type
        val fragmentType = intent.getStringExtra(EXTRA_FRAGMENT_TYPE)
        when (fragmentType) {
            FRAGMENT_SETTINGS -> setupSettingsUI()
            FRAGMENT_SHOP -> setupShopUI()
            FRAGMENT_REFERRAL -> setupReferralUI()
            else -> setupDefaultUI()
        }
    }
    
    private fun setupSettingsUI() {
        tvTitle.text = "Настройки"
        btnPrimaryAction.visibility = View.VISIBLE
        btnPrimaryAction.text = "Сохранить"
        
        btnPrimaryAction.setOnClickListener {
            // Handle save action
            val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (fragment is ModernSettingsFragment) {
                // Settings fragment will handle saving internally
            }
        }
    }
    
    private fun setupShopUI() {
        tvTitle.text = "Магазин"
        // Shop might have different UI requirements
        bottomNavigation.visibility = View.VISIBLE
    }
    
    private fun setupReferralUI() {
        tvTitle.text = "Реферальная программа"
        actionButtonsContainer.visibility = View.VISIBLE
        // Could add share/copy buttons here
    }
    
    private fun setupDefaultUI() {
        // Default setup - minimal UI
    }
    
    private fun loadFragment(savedInstanceState: Bundle?) {
        val fragmentType = intent.getStringExtra(EXTRA_FRAGMENT_TYPE)
        val fragment = when (fragmentType) {
            FRAGMENT_SETTINGS -> ModernSettingsFragment.newInstance()
            // Add other fragments as needed
            // FRAGMENT_SHOP -> ShopFragment.newInstance()
            // FRAGMENT_REFERRAL -> ReferralFragment.newInstance()
            else -> ModernSettingsFragment.newInstance() // Default to settings
        }
        
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
        }
    }
    
    fun setTitle(title: String) {
        tvTitle.text = title
    }
    
    fun showPrimaryAction(text: String, action: () -> Unit) {
        btnPrimaryAction.visibility = View.VISIBLE
        btnPrimaryAction.text = text
        btnPrimaryAction.setOnClickListener { action() }
    }
    
    fun hidePrimaryAction() {
        btnPrimaryAction.visibility = View.GONE
    }
    
    fun showBottomNavigation() {
        bottomNavigation.visibility = View.VISIBLE
    }
    
    fun hideBottomNavigation() {
        bottomNavigation.visibility = View.GONE
    }
} 