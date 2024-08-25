package com.tpov.schoolquiz.presentation.splashscreen

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tpov.schoolquiz.MainApp
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.ActivitySplashScreenBinding
import com.tpov.schoolquiz.presentation.AppWorkerFactory
import com.tpov.schoolquiz.presentation.SyncWorker
import com.tpov.schoolquiz.presentation.main.MainActivity
import kotlinx.coroutines.InternalCoroutinesApi
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@SuppressLint("CustomSplashScreen")
@InternalCoroutinesApi
class SplashScreen : AppCompatActivity() {

    @Inject
    lateinit var daggerWorkerFactory: AppWorkerFactory
    private lateinit var binding: ActivitySplashScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        binding = ActivitySplashScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        (application as MainApp).applicationComponent.inject(this)
        setupPeriodicSync()
        createAnimation()
    }

    private fun View.setVisible(visible: Boolean) {
        this.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun visibleTPOV(visible: Boolean) = with(binding) {
        tvT.setVisible(visible)
        tvP.setVisible(visible)
        tvO.setVisible(visible)
        tvV.setVisible(visible)
    }

    private fun createAnimation() = with(binding) {
        visibleTPOV(true)

        val animations = listOf(
            tvT to R.anim.anim_splash_t,
            tvP to R.anim.anim_splash_p,
            tvO to R.anim.anim_splash_o,
            tvV to R.anim.anim_splash_v
        )

        animations.forEach { (view, animRes) ->
            val animation = AnimationUtils.loadAnimation(this@SplashScreen, animRes)
            if (view == tvV) {
                animation.setAnimationListener(object : AnimationListener {
                    override fun onAnimationStart(p0: Animation?) {
                        visibleTPOV(true)
                    }

                    override fun onAnimationEnd(p0: Animation?) {
                        visibleTPOV(false)
                        startMainActivity()
                    }

                    override fun onAnimationRepeat(p0: Animation?) {}
                })
            }
            view.startAnimation(animation)
        }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setupPeriodicSync() {

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SyncWork",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}