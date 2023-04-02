package com.tpov.schoolquiz.presentation.mainactivity.info

import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.presentation.factory.InfoManager

/**
 * The InfoActivity displays information about the application for the user.
 */
class InfoActivity: AppCompatActivity() {
    private lateinit var defPref: SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        defPref = PreferenceManager.getDefaultSharedPreferences(this)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.placeHolder, InfoManager())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}
