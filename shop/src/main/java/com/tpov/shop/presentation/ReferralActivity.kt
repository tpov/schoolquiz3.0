package com.tpov.shop.presentation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.tpov.shop.R

class ReferralActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_referral)
        
        // Make layout fullscreen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Set status bar color
        window.statusBarColor = ContextCompat.getColor(this, R.color.referral_background_dark)
        
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.referral_container, ReferralFragment.newInstance())
                .commit()
        }
    }
    
    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, ReferralActivity::class.java)
        }
    }
} 