package com.tpov.schoolquiz.presentation.splashscreen

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.tpov.common.domain.usecase.SettingConfigObject
import com.tpov.schoolquiz.MainApp
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.ActivitySplashScreenBinding
import com.tpov.schoolquiz.presentation.AppWorkerFactory
import com.tpov.schoolquiz.presentation.SyncWorker
import com.tpov.schoolquiz.presentation.main.MainActivity
import com.tpov.setting.data.PreferencesManager
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Inject

@SuppressLint("CustomSplashScreen")
@InternalCoroutinesApi
class SplashScreen : AppCompatActivity() {

    @Inject
    lateinit var daggerWorkerFactory: AppWorkerFactory
    private lateinit var binding: ActivitySplashScreenBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        binding = ActivitySplashScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hide top bar and make fullscreen
        hideTopBar()

        (application as MainApp).applicationComponent.inject(this)

        // Установка начальных состояний
        setupInitialStates()

        // Запуск анимации с задержкой 500ms
        handler.postDelayed({
            startQvestifyAnimation()
        }, 500)

        startInitialSetupAndSyncAndObserve()
        syncSettings()
    }

    private fun syncSettings() {
        SettingConfigObject.updateSettings( PreferencesManager(this).getSettings() )
    }

    private fun hideTopBar() {
        // Hide action bar
        supportActionBar?.hide()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11 (API 30) and above
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // For older versions
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }

        // Keep screen on during splash
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupInitialStates() {
        binding.logoQ.alpha = 0f
        binding.logoQ.scaleX = 0f
        binding.logoQ.scaleY = 0f
        binding.logoQ.rotation = -180f

        binding.logoDot.alpha = 0f
        binding.logoDot.scaleX = 0f
        binding.logoDot.scaleY = 0f
        binding.logoDot.translationY = -200f

        binding.appName.alpha = 0f
        binding.appName.translationY = 30f

        binding.tagline.alpha = 0f

        binding.glowView.alpha = 0f
        binding.glowView.scaleX = 0.5f
        binding.glowView.scaleY = 0.5f

        binding.pulseRing.alpha = 0f
        binding.pulseRing.scaleX = 0.5f
        binding.pulseRing.scaleY = 0.5f

        binding.loadingContainer.alpha = 0f

        // Создаем частицы после отрисовки макета
        binding.particlesContainer.post {
            createParticles()
        }
    }

    private fun createParticles() {
        val particleCount = 9
        val particleSize = resources.getDimensionPixelSize(R.dimen.particle_size)
        val containerWidth = binding.particlesContainer.width
        val containerHeight = binding.particlesContainer.height

        repeat(particleCount) { index ->
            val particle = View(this).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(particleSize, particleSize)
                setBackgroundResource(R.drawable.white_circle)
                alpha = 0.3f
                x = (containerWidth * (0.1f + index * 0.1f))
                y = containerHeight * 0.8f
            }

            binding.particlesContainer.addView(particle)

            // Анимация плавания частиц (как в HTML)
            val duration = listOf(5000L, 6000L, 4000L, 7000L, 5000L, 6000L, 4000L, 5000L, 6000L)[index]
            val delay = index * 500L

            startParticleAnimation(particle, duration, delay)
        }
    }

    private fun startParticleAnimation(particle: View, duration: Long, delay: Long) {
        val floatAnimator = ObjectAnimator.ofFloat(particle, "translationY", 0f, -100f).apply {
            this.duration = duration
            startDelay = delay
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val rotateAnimator = ObjectAnimator.ofFloat(particle, "rotation", 0f, 180f).apply {
            this.duration = duration
            startDelay = delay
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
        }

        val alphaAnimator = ObjectAnimator.ofFloat(particle, "alpha", 0.3f, 0.8f).apply {
            this.duration = duration / 2
            startDelay = delay
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        AnimatorSet().apply {
            playTogether(floatAnimator, rotateAnimator, alphaAnimator)
            start()
        }
    }

    private fun startQvestifyAnimation() {

        startGlowAnimation()
        startPulseRingAnimation()
        startLogoAnimation()
        startDotAnimation()
        startNameAnimation()
        startTaglineAnimation()
        startLoadingAnimation()

        handler.postDelayed({
            fadeOutAndStart()
        }, 8000)
    }

    private fun startGlowAnimation() {
        val glowSet = AnimatorSet().apply {
            val alphaIn = ObjectAnimator.ofFloat(binding.glowView, "alpha", 0f, 0.8f).apply {
                duration = 1500
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scaleInX = ObjectAnimator.ofFloat(binding.glowView, "scaleX", 0.5f, 1f).apply {
                duration = 1500
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scaleInY = ObjectAnimator.ofFloat(binding.glowView, "scaleY", 0.5f, 1f).apply {
                duration = 1500
                interpolator = AccelerateDecelerateInterpolator()
            }
            val alphaOut = ObjectAnimator.ofFloat(binding.glowView, "alpha", 0.8f, 0f).apply {
                duration = 1500
                startDelay = 1500
                interpolator = DecelerateInterpolator()
            }
            val scaleOutX = ObjectAnimator.ofFloat(binding.glowView, "scaleX", 1f, 1.5f).apply {
                duration = 1500
                startDelay = 1500
                interpolator = DecelerateInterpolator()
            }
            val scaleOutY = ObjectAnimator.ofFloat(binding.glowView, "scaleY", 1f, 1.5f).apply {
                duration = 1500
                startDelay = 1500
                interpolator = DecelerateInterpolator()
            }

            playTogether(alphaIn, scaleInX, scaleInY)
            play(alphaOut).after(alphaIn)
            play(scaleOutX).after(scaleInX)
            play(scaleOutY).after(scaleInY)
            startDelay = 1000
        }
        glowSet.start()
    }

    private fun startPulseRingAnimation() {
        val pulseSet = AnimatorSet().apply {
            val alphaIn = ObjectAnimator.ofFloat(binding.pulseRing, "alpha", 0f, 0.6f).apply {
                duration = 1000
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scaleInX = ObjectAnimator.ofFloat(binding.pulseRing, "scaleX", 0.5f, 1f).apply {
                duration = 1000
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scaleInY = ObjectAnimator.ofFloat(binding.pulseRing, "scaleY", 0.5f, 1f).apply {
                duration = 1000
                interpolator = AccelerateDecelerateInterpolator()
            }
            val alphaOut = ObjectAnimator.ofFloat(binding.pulseRing, "alpha", 0.6f, 0f).apply {
                duration = 1000
                startDelay = 1000
                interpolator = DecelerateInterpolator()
            }
            val scaleOutX = ObjectAnimator.ofFloat(binding.pulseRing, "scaleX", 1f, 2f).apply {
                duration = 1000
                startDelay = 1000
                interpolator = DecelerateInterpolator()
            }
            val scaleOutY = ObjectAnimator.ofFloat(binding.pulseRing, "scaleY", 1f, 2f).apply {
                duration = 1000
                startDelay = 1000
                interpolator = DecelerateInterpolator()
            }

            playTogether(alphaIn, scaleInX, scaleInY)
            play(alphaOut).after(alphaIn)
            play(scaleOutX).after(scaleInX)
            play(scaleOutY).after(scaleInY)
            startDelay = 1000
        }
        pulseSet.start()
    }

    private fun startLogoAnimation() {
        val logoSet = AnimatorSet().apply {
            val alpha = ObjectAnimator.ofFloat(binding.logoQ, "alpha", 0f, 1f).apply {
                duration = 3000
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scaleX = ObjectAnimator.ofFloat(binding.logoQ, "scaleX", 0f, 1.1f).apply {
                duration = 2100 // 70% of 3000ms
                interpolator = OvershootInterpolator()
            }
            val scaleY = ObjectAnimator.ofFloat(binding.logoQ, "scaleY", 0f, 1.1f).apply {
                duration = 2100 // 70% of 3000ms
                interpolator = OvershootInterpolator()
            }
            val scaleBackX = ObjectAnimator.ofFloat(binding.logoQ, "scaleX", 1.1f, 1f).apply {
                duration = 900 // remaining 30%
                startDelay = 2100
                interpolator = DecelerateInterpolator()
            }
            val scaleBackY = ObjectAnimator.ofFloat(binding.logoQ, "scaleY", 1.1f, 1f).apply {
                duration = 900 // remaining 30%
                startDelay = 2100
                interpolator = DecelerateInterpolator()
            }
            val rotation = ObjectAnimator.ofFloat(binding.logoQ, "rotation", -180f, 10f).apply {
                duration = 2100
                interpolator = AccelerateDecelerateInterpolator()
            }
            val rotationBack = ObjectAnimator.ofFloat(binding.logoQ, "rotation", 10f, 0f).apply {
                duration = 900
                startDelay = 2100
                interpolator = DecelerateInterpolator()
            }

            playTogether(alpha, scaleX, scaleY, rotation)
            play(scaleBackX).after(scaleX)
            play(scaleBackY).after(scaleY)
            play(rotationBack).after(rotation)
            startDelay = 500
        }
        logoSet.start()
    }

    private fun startDotAnimation() {
        val dotSet = AnimatorSet().apply {
            val alpha = ObjectAnimator.ofFloat(binding.logoDot, "alpha", 0f, 1f).apply {
                duration = 800
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scale1X = ObjectAnimator.ofFloat(binding.logoDot, "scaleX", 0f, 1.2f).apply {
                duration = 400 // 50% of 800ms
                interpolator = BounceInterpolator()
            }
            val scale1Y = ObjectAnimator.ofFloat(binding.logoDot, "scaleY", 0f, 1.2f).apply {
                duration = 400 // 50% of 800ms
                interpolator = BounceInterpolator()
            }
            val scale2X = ObjectAnimator.ofFloat(binding.logoDot, "scaleX", 1.2f, 1f).apply {
                duration = 400
                startDelay = 400
                interpolator = DecelerateInterpolator()
            }
            val scale2Y = ObjectAnimator.ofFloat(binding.logoDot, "scaleY", 1.2f, 1f).apply {
                duration = 400
                startDelay = 400
                interpolator = DecelerateInterpolator()
            }
            val translateY1 = ObjectAnimator.ofFloat(binding.logoDot, "translationY", -200f, -40f).apply {
                duration = 600
                interpolator = BounceInterpolator()
            }
            val translateY2 = ObjectAnimator.ofFloat(binding.logoDot, "translationY", -40f, 20f).apply {
                duration = 600
                startDelay = 600
                interpolator = DecelerateInterpolator()
            }

            playTogether(alpha, scale1X, scale1Y, translateY1)
            play(scale2X).after(scale1X)
            play(scale2Y).after(scale1Y)
            play(translateY2).after(translateY1)
            startDelay = 2000
        }
        dotSet.start()
    }

    private fun startNameAnimation() {
        val nameSet = AnimatorSet().apply {
            val alpha = ObjectAnimator.ofFloat(binding.appName, "alpha", 0f, 1f).apply {
                duration = 1000
                interpolator = AccelerateDecelerateInterpolator()
            }
            val translateY = ObjectAnimator.ofFloat(binding.appName, "translationY", 30f, 0f).apply {
                duration = 1000
                interpolator = DecelerateInterpolator()
            }

            playTogether(alpha, translateY)
            startDelay = 2500
        }
        nameSet.start()
    }

    private fun startTaglineAnimation() {
        val taglineAnimator = ObjectAnimator.ofFloat(binding.tagline, "alpha", 0f, 1f).apply {
            duration = 1000
            startDelay = 3500
            interpolator = DecelerateInterpolator()
        }
        taglineAnimator.start()
    }

    private fun startLoadingAnimation() {
        // Показываем контейнер загрузки
        val containerAnimator = ObjectAnimator.ofFloat(binding.loadingContainer, "alpha", 0f, 1f).apply {
            duration = 3000
            startDelay = 4000
            interpolator = DecelerateInterpolator()
        }
        containerAnimator.start()

        // Анимируем прогресс
        handler.postDelayed({
            animateLoadingProgress()
        }, 5000)
    }

    private fun animateLoadingProgress() {
        // Получаем ширину контейнера после отрисовки
        binding.loadingContainer.post {
            val containerWidth = binding.loadingContainer.width

            // Анимируем ширину прогресс-бара от 0 до 100%
            val progressAnimator = ValueAnimator.ofInt(0, containerWidth).apply {
                duration = 2500
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    val width = animator.animatedValue as Int
                    val layoutParams = binding.loadingProgress.layoutParams
                    layoutParams.width = width
                    binding.loadingProgress.layoutParams = layoutParams
                }
            }
            progressAnimator.start()
        }
    }

    private fun fadeOutAndStart() {
        // Создаем View поверх всего экрана для плавного перехода
        val fadeOverlay = View(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            alpha = 0f
        }

        // Добавляем overlay поверх всего
        val decorView = window.decorView as android.view.ViewGroup
        decorView.addView(fadeOverlay, android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Плавно затемняем экран
        val fadeOut = ObjectAnimator.ofFloat(fadeOverlay, "alpha", 0f, 1f).apply {
            duration = 1000
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Запускаем MainActivity после завершения затемнения
                    startMainActivity()
                }
            })
        }
        fadeOut.start()
    }

    private fun startInitialSetupAndSyncAndObserve() {
        // Implementation of startInitialSetupAndSyncAndObserve function
        val initialSyncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .build()

        val workManager = WorkManager.getInstance(this)
        workManager.enqueue(initialSyncRequest)

        workManager.getWorkInfoByIdLiveData(initialSyncRequest.id)
            .observe(this) { workInfo ->
                if (workInfo != null && workInfo.state.isFinished) {
                    // Sync completed
                }
            }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
