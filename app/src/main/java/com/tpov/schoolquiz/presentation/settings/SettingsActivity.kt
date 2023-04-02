package com.tpov.schoolquiz.presentation.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.tpov.schoolquiz.R

class SettingsActivity: AppCompatActivity() {
    private lateinit var defPref: SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        defPref = PreferenceManager.getDefaultSharedPreferences(this)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.placeHolder, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }

//    private fun getSelectedTheme(): Int {
//        return when {
//            defPref.getString("theme key", "classic") == "classic" {
//
//            }
//            defPref.getString("theme key", "night") == "night" {
//
//            }
//            defPref.getString("theme key", "light") == "light" {
//
//            }
//            else -> {
//
//            }
//        }
//    }
}