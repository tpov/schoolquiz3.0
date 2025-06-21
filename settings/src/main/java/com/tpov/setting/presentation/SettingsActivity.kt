package com.tpov.setting.presentation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tpov.setting.R

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        supportActionBar?.apply {
            title = getString(R.string.settings_title)
            setDisplayHomeAsUpEnabled(true)
        }

        // Use modern settings fragment instead of preference fragment
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, ModernSettingsFragment.newInstance())
                .commit()
        }
    }
}
