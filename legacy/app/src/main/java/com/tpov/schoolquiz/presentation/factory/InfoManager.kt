package com.tpov.schoolquiz.presentation.factory

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.tpov.schoolquiz.R

class InfoManager : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.info_preference, rootKey)
        init()
    }

    private fun init() {

    }
}
