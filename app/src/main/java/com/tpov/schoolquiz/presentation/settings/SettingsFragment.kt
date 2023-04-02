package com.tpov.schoolquiz.presentation.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.tpov.schoolquiz.R

class SettingsFragment: PreferenceFragmentCompat() {
    private lateinit var removeAdsPref: Preference
    private lateinit var bManager: BillingManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_preference, rootKey)
        init()
    }

    private fun init() {
        bManager = BillingManager(activity as AppCompatActivity)
        removeAdsPref = findPreference("remove_ads_key")!!
        removeAdsPref.setOnPreferenceClickListener {
            bManager.startConnection()
            true
        }
    }

    override fun onDestroy() {
        bManager.closeConnection()
        super.onDestroy()
    }
}
