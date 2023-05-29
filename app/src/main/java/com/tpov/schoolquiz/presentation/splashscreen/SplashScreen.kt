package com.tpov.schoolquiz.presentation.splashscreen

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.ActivitySplashScreenBinding
import com.tpov.schoolquiz.presentation.main.MainActivity
import kotlinx.coroutines.InternalCoroutinesApi

@SuppressLint("CustomSplashScreen")
@InternalCoroutinesApi
class SplashScreen : AppCompatActivity() {

    private lateinit var binding: ActivitySplashScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState)
        binding = ActivitySplashScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        visibleTPOV(false)

        createAnimation()
    }

    private fun visibleTPOV(visible: Boolean) = with(binding) {
        Log.d("WorkManager", "Видимость ТПОВ.")
        if (visible) {
            tvT.visibility = View.VISIBLE
            tvP.visibility = View.VISIBLE
            tvO.visibility = View.VISIBLE
            tvV.visibility = View.VISIBLE
        } else {
            tvT.visibility = View.GONE
            tvP.visibility = View.GONE
            tvO.visibility = View.GONE
            tvV.visibility = View.GONE
        }
    }


    private fun createAnimation() = with(binding) {
        visibleTPOV(true)

        tvT.startAnimation(AnimationUtils.loadAnimation(this@SplashScreen, R.anim.anim_splash_t))
        tvP.startAnimation(AnimationUtils.loadAnimation(this@SplashScreen, R.anim.anim_splash_p))
        tvO.startAnimation(AnimationUtils.loadAnimation(this@SplashScreen, R.anim.anim_splash_o))

        var anim3 = AnimationUtils.loadAnimation(this@SplashScreen, R.anim.anim_splash_v)
        animationListener(anim3)
        tvV.startAnimation(anim3)
    }

    private fun animationListener(anim: Animation) {

        anim.setAnimationListener(object : AnimationListener {
            override fun onAnimationStart(p0: Animation?) {
                visibleTPOV(true)
            }

            override fun onAnimationEnd(p0: Animation?) {
                visibleTPOV(false)
                startActivity()
            }

            override fun onAnimationRepeat(p0: Animation?) {

            }
        })
    }

    private fun startActivity() {
        var intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    companion object {

    }
}