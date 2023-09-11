package com.tpov.schoolquiz.presentation.main

import android.Manifest.permission.*
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.*
import android.view.animation.LinearInterpolator
import android.widget.*
import android.window.SplashScreen
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.firebase.auth.FirebaseAuth
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.data.model.Qualification
import com.tpov.schoolquiz.databinding.ActivityMainBinding
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.contact.Contacts
import com.tpov.schoolquiz.presentation.custom.CalcValues.getSkillByCountInChat
import com.tpov.schoolquiz.presentation.custom.CalcValues.getSkillByTimeInChat
import com.tpov.schoolquiz.presentation.custom.CalcValues.getSkillByTimeInGame
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.custom.NewValue.setNewSkill
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getCountMassageIdAndReset
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getCountStartApp
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.setCountStartApp
import com.tpov.schoolquiz.presentation.custom.Values.loadProgress
import com.tpov.schoolquiz.presentation.custom.Values.loadText
import com.tpov.schoolquiz.presentation.dowload.DownloadFragment
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import com.tpov.schoolquiz.presentation.fragment.FragmentManager
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_ARENA
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_CHAT
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_CONTACT
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_DOWNLOADS
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_EVENT
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_EXIT
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_FRIEND
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_HOME
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_LEADER
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_MASSAGE
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_MY_QUIZ
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_NEWS
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_PROFILE
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_REPORT
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_SETTING
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_USERS
import com.tpov.schoolquiz.presentation.main.info.InfoFragment
import com.tpov.schoolquiz.presentation.network.AutorisationFragment
import com.tpov.schoolquiz.presentation.network.chat.ChatFragment
import com.tpov.schoolquiz.presentation.network.event.EventFragment
import com.tpov.schoolquiz.presentation.network.profile.ProfileFragment
import com.tpov.schoolquiz.presentation.network.profile.UsersFragment
import com.tpov.schoolquiz.presentation.setting.SettingsFragment
import com.tpov.schoolquiz.presentation.shop.ShopFragment
import com.tpov.shoppinglist.utils.TimeManager
import com.tpov.userguide.Options
import com.tpov.userguide.UserGuide
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import java.text.NumberFormat
import java.util.*
import javax.inject.Inject
import kotlin.system.exitProcess

/**
 * This is the main screen of the application, it consists of a panel that shows how much spare is left.
 * questions of the day and a fragment that displays user and system questions
 */

@InternalCoroutinesApi
class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    private var iAd: InterstitialAd? = null
    private var numDayPrizeBox = 0
    private lateinit var viewModel: MainActivityViewModel
    private var fr1 = 1
    private var fr2 = 1

    private val sharedPreferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "tpovId") {
                val newTpovId = sharedPreferences.getInt(key, -1)
                viewModel.updateTpovId(newTpovId)
            }
        }

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private var recreateActivity: Boolean = false

    private val component by lazy {
        (application as MainApp).component
    }

    override fun onDestroy() {
        super.onDestroy()
        val sharedPref = this.getSharedPreferences("profile", Context.MODE_PRIVATE)
        sharedPref.unregisterOnSharedPreferenceChangeListener(sharedPreferenceListener)

        timer?.cancel()
        timer = null
    }

    private fun calculateQuizzResult() {

        lifecycleScope.launch {
            viewModel.setPercentResultAllQuiz()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        component.inject(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val swipeRefreshLayout = binding.swipeRefreshLayout

        var oldLoadText = ""
        loadText.observe(this) {
            log("ioioioio $it")
           // binding.tvPbLoad.visibility = View.VISIBLE
            if (oldLoadText != it) showTextWithDelay(binding.tvPbLoad, it, 25)
            oldLoadText = it
        }

        loadProgress.observe(this) {

        }

        swipeRefreshLayout.setOnRefreshListener {
            if (supportFragmentManager.findFragmentById(R.id.title_fragment) is FragmentMain) {
                calculateQuizzResult()
            }
            swipeRefreshLayout.isRefreshing = false
        }
        
        val pInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName: String = pInfo.versionName

        val userguide = UserGuide(this)

        userguide.addGuideNewVersion(
            "I'm considering the opportunity to join your team as an Android developer. What are your thoughts on this?",
            "Hello, Google!",
            icon = getDrawable(R.drawable.googleg_standard_color_18)
        )

        userguide.addGuide(
            findViewById(R.id.menu_network),
            "Тут находится связь с внешним миром, квесты других игроков, чат, турниры и многое другое",
            "Network",
            icon = getDrawable(R.drawable.ic_settings),
            callback = {
                startProfile()
            })

        binding.imbManu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        supportActionBar?.hide()
        viewModel = ViewModelProvider(this, viewModelFactory)[MainActivityViewModel::class.java]

        viewModel.init()

        try {
            setCountStartApp(getCountStartApp() + 1)
        } catch (e: Exception) {
            setCountStartApp(1)
        }

        val imageResGold = R.drawable.baseline_favorite_24_gold
        val imageRes = R.drawable.baseline_favorite_24

        val sharedPref = this.getSharedPreferences("profile", Context.MODE_PRIVATE)
        sharedPref.registerOnSharedPreferenceChangeListener(sharedPreferenceListener)

        val filledDrawable = ContextCompat.getDrawable(this, imageRes)
        val filledDrawableGold = ContextCompat.getDrawable(this, imageResGold)
        val emptyDrawable = ContextCompat.getDrawable(this, R.drawable.baseline_favorite_24_empty)

        val layers1 = arrayOf(
            emptyDrawable,
            ClipDrawable(filledDrawable, Gravity.LEFT, ClipDrawable.HORIZONTAL)
        )
        val layers2 = arrayOf(
            emptyDrawable,
            ClipDrawable(filledDrawable, Gravity.LEFT, ClipDrawable.HORIZONTAL)
        )
        val layers3 = arrayOf(
            emptyDrawable,
            ClipDrawable(filledDrawable, Gravity.LEFT, ClipDrawable.HORIZONTAL)
        )
        val layers4 = arrayOf(
            emptyDrawable,
            ClipDrawable(filledDrawable, Gravity.LEFT, ClipDrawable.HORIZONTAL)
        )
        val layers5 = arrayOf(
            emptyDrawable,
            ClipDrawable(filledDrawable, Gravity.LEFT, ClipDrawable.HORIZONTAL)
        )
        val layersGold = arrayOf(
            emptyDrawable,
            ClipDrawable(filledDrawableGold, Gravity.LEFT, ClipDrawable.HORIZONTAL)
        )
        val layerDrawable1 = LayerDrawable(layers1)
        val layerDrawable2 = LayerDrawable(layers2)
        val layerDrawable3 = LayerDrawable(layers3)
        val layerDrawable4 = LayerDrawable(layers4)
        val layerDrawable5 = LayerDrawable(layers5)
        val layerDrawableGold = LayerDrawable(layersGold)

        layerDrawable1.setDrawableByLayerId(0, emptyDrawable)
        layerDrawable2.setDrawableByLayerId(0, emptyDrawable)
        layerDrawable3.setDrawableByLayerId(0, emptyDrawable)
        layerDrawable4.setDrawableByLayerId(0, emptyDrawable)
        layerDrawable5.setDrawableByLayerId(0, emptyDrawable)
        layerDrawableGold.setDrawableByLayerId(0, emptyDrawable)

        layerDrawable1.setDrawableByLayerId(
            1,
            ClipDrawable(filledDrawable, Gravity.LEFT, ClipDrawable.HORIZONTAL)
        )
        layerDrawable2.setDrawableByLayerId(
            1,
            ClipDrawable(filledDrawable, Gravity.LEFT, ClipDrawable.HORIZONTAL)
        )
        layerDrawable3.setDrawableByLayerId(
            1,
            ClipDrawable(filledDrawable, Gravity.LEFT, ClipDrawable.HORIZONTAL)
        )
        layerDrawable4.setDrawableByLayerId(
            1,
            ClipDrawable(filledDrawable, Gravity.LEFT, ClipDrawable.HORIZONTAL)
        )
        layerDrawable5.setDrawableByLayerId(
            1,
            ClipDrawable(filledDrawable, Gravity.LEFT, ClipDrawable.HORIZONTAL)
        )
        layerDrawableGold.setDrawableByLayerId(
            1,
            ClipDrawable(filledDrawableGold, Gravity.LEFT, ClipDrawable.HORIZONTAL)
        )
        val imageViewGold = binding.pbLifeGold1
        val imageViewLife1 = binding.pbLife1
        val imageViewLife2 = binding.pbLife2
        val imageViewLife3 = binding.pbLife3
        val imageViewLife4 = binding.pbLife4
        val imageViewLife5 = binding.pbLife5

        layerDrawable1.setId(0, android.R.id.background)
        layerDrawable2.setId(0, android.R.id.background)
        layerDrawable3.setId(0, android.R.id.background)
        layerDrawable4.setId(0, android.R.id.background)
        layerDrawable5.setId(0, android.R.id.background)
        layerDrawableGold.setId(0, android.R.id.background)
        layerDrawable1.setId(1, android.R.id.progress)
        layerDrawable2.setId(1, android.R.id.progress)
        layerDrawable3.setId(1, android.R.id.progress)
        layerDrawable4.setId(1, android.R.id.progress)
        layerDrawable5.setId(1, android.R.id.progress)
        layerDrawableGold.setId(1, android.R.id.progress)

        imageViewGold.setImageDrawable(layerDrawableGold)
        imageViewLife1.setImageDrawable(layerDrawable1)
        imageViewLife2.setImageDrawable(layerDrawable2)
        imageViewLife3.setImageDrawable(layerDrawable3)
        imageViewLife4.setImageDrawable(layerDrawable4)
        imageViewLife5.setImageDrawable(layerDrawable5)

        if (ContextCompat.checkSelfPermission(
                this,
                READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Разрешения уже предоставлены, выполнить нужную функцию
            binding.tvName.text = ""

        } else {
            // Разрешения не предоставлены, запросить их
            requestStoragePermission()
        }

        numDayPrizeBox = viewModel.synthPrizeBoxDay(viewModel.getProfile()) ?: 0
        viewModel.getProfileFBLiveData.observe(this) {
            log("it: $it")
            showNotification(it?.pointsSkill)
            var count = (it?.count ?: 0) * 100
            layerDrawable1.findDrawableByLayerId(android.R.id.progress).level = count
            count -= 10000
            layerDrawable2.findDrawableByLayerId(android.R.id.progress).level = count
            count -= 10000
            layerDrawable3.findDrawableByLayerId(android.R.id.progress).level = count
            count -= 10000
            layerDrawable4.findDrawableByLayerId(android.R.id.progress).level = count
            count -= 10000
            layerDrawable5.findDrawableByLayerId(android.R.id.progress).level = count
            count -= 10000
            layerDrawableGold.findDrawableByLayerId(android.R.id.progress).level =
                (it?.countGold ?: 0) * 100

            if (it?.countGoldLife == 1) {
                imageViewGold.visibility = View.VISIBLE
            } else imageViewGold.visibility = View.GONE

            when (it?.countLife) {
                0, 1 -> {
                    imageViewLife1.visibility = View.VISIBLE
                    imageViewLife2.visibility = View.GONE
                    imageViewLife3.visibility = View.GONE
                    imageViewLife4.visibility = View.GONE
                    imageViewLife5.visibility = View.GONE
                }

                2 -> {
                    imageViewLife1.visibility = View.VISIBLE
                    imageViewLife2.visibility = View.VISIBLE
                    imageViewLife3.visibility = View.GONE
                    imageViewLife4.visibility = View.GONE
                    imageViewLife5.visibility = View.GONE
                }

                3 -> {
                    imageViewLife1.visibility = View.VISIBLE
                    imageViewLife2.visibility = View.VISIBLE
                    imageViewLife3.visibility = View.VISIBLE
                    imageViewLife4.visibility = View.GONE
                    imageViewLife5.visibility = View.GONE
                }

                4 -> {
                    imageViewLife1.visibility = View.VISIBLE
                    imageViewLife2.visibility = View.VISIBLE
                    imageViewLife3.visibility = View.VISIBLE
                    imageViewLife4.visibility = View.VISIBLE
                    imageViewLife5.visibility = View.GONE
                }

                5 -> {
                    imageViewLife1.visibility = View.VISIBLE
                    imageViewLife2.visibility = View.VISIBLE
                    imageViewLife3.visibility = View.VISIBLE
                    imageViewLife4.visibility = View.VISIBLE
                    imageViewLife5.visibility = View.VISIBLE
                }
            }
            log("SharedPreferencesManager.getNick(): ${SharedPreferencesManager.getNick()}")
            log("it?.nickname: ${it?.nickname}")
            if (SharedPreferencesManager.getNick() != it?.nickname) {

                showTextWithDelay(
                    binding.tvName, try {
                        "${it?.nickname} ${it?.trophy}"
                    } catch (e: Exception) {
                        ""
                    }, 50
                )

            } else binding.tvName.text =
                "${it.nickname} ${it.trophy}"

            val animationDuration = 3000L
            animateValue(
                binding.tvNolics,
                SharedPreferencesManager.getNolic(),
                it?.pointsNolics ?: 0,
                animationDuration,
                500
            )

            animateValue(
                binding.tvGold,
                SharedPreferencesManager.getGold(),
                it?.pointsGold ?: 0,
                animationDuration,
                500
            )

            animateValueFloat(
                binding.tvStars,
                (SharedPreferencesManager.getSkill().toFloat() / 100_000f),
                ((it?.pointsSkill?.toFloat())?.div(100_000f)) ?: 0f,
                animationDuration,
                500
            )

            animateValue(
                binding.tvCountPremiun,
                TimeManager.getDaysBetweenDates(
                    SharedPreferencesManager.getPremium(),
                    TimeManager.getCurrentTime()
                ).toInt() ?: 0,
                TimeManager.getDaysBetweenDates(
                    it?.datePremium ?: "",
                    TimeManager.getCurrentTime()
                )
                    .toInt(),
                animationDuration,
                500
            )
            SharedPreferencesManager.setProfile(
                it?.pointsSkill ?: 0,
                it?.pointsNolics ?: 0,
                it?.pointsGold ?: 0,
                it?.datePremium ?: "",
                it?.nickname ?: ""
            )

            setNewSkill(it?.pointsSkill)
        }

        pb_life1.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                // Rating bar clicked, handle the event here
                // You can call your method to show the translation popup/dialog
                showPopupInfo(event, MainActivityAdapter.POPUP_LIFE)
            }
            true
        }
        pb_life2.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                // Rating bar clicked, handle the event here
                // You can call your method to show the translation popup/dialog
                showPopupInfo(event, MainActivityAdapter.POPUP_LIFE)
            }
            true
        }
        pb_life3.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                // Rating bar clicked, handle the event here
                // You can call your method to show the translation popup/dialog
                showPopupInfo(event, MainActivityAdapter.POPUP_LIFE)
            }
            true
        }
        pb_life4.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                // Rating bar clicked, handle the event here
                // You can call your method to show the translation popup/dialog
                showPopupInfo(event, MainActivityAdapter.POPUP_LIFE)
            }
            true
        }
        pb_life5.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                // Rating bar clicked, handle the event here
                // You can call your method to show the translation popup/dialog
                showPopupInfo(event, MainActivityAdapter.POPUP_LIFE)
            }
            true
        }
        pb_life_gold1.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                // Rating bar clicked, handle the event here
                // You can call your method to show the translation popup/dialog
                showPopupInfo(event, MainActivityAdapter.POPUP_LIFE_GOLD)
            }
            true
        }
        setButtonNavListener()

        val profile = viewModel.getProfile()
        val qualification = try {
            Qualification(
                profile.tester ?: 0,
                profile.moderator ?: 0,
                profile.sponsor ?: 0,
                profile.translater ?: 0,
                profile.admin ?: 0,
                profile.developer ?: 0
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Произошла ошибка данных профиля, подождите (1)", Toast.LENGTH_LONG).show()
            Toast.makeText(this, "Произошла ошибка данных профиля, подождите (2)", Toast.LENGTH_LONG).show()
            Toast.makeText(this, "Произошла ошибка данных профиля, подождите (3)", Toast.LENGTH_LONG).show()

            val cacheDir = this.cacheDir

            val files = cacheDir.listFiles()
            if (files != null) {
                for (file in files) {
                    file.delete()
                }
            } else {
                Toast.makeText(this, "Кеш не удалось почистить ..(", Toast.LENGTH_LONG).show()
                0
            }

            val mStartActivity = Intent(this, SplashScreen::class.java)
            val mPendingIntentId = 123456
            val mPendingIntent = PendingIntent.getActivity(
                this,
                mPendingIntentId,
                mStartActivity,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val mgr = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 3000, mPendingIntent)
            exitProcess(0)

            Qualification(
                profile.tester ?: 0,
                profile.moderator ?: 0,
                profile.sponsor ?: 0,
                profile.translater ?: 0,
                profile.admin ?: 0,
                profile.developer ?: 0
            )
        }

        FragmentManager.setFragment(FragmentMain.newInstance(8), this)
        SetItemMenu.setHomeMenu(
            binding, MENU_HOME, this,
            profile.pointsSkill ?: 0,
            qualification
        )

        loadNumBoxDay()

        binding.drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                // Вычисляем на сколько нужно сдвинуть элемент LinearLayout
                val slideX = drawerView.width * slideOffset
                binding.cv.translationX = slideX
            }

            override fun onDrawerOpened(drawerView: View) {
                // Вызывается при открытии шторки
            }

            override fun onDrawerClosed(drawerView: View) {
                // Вызывается при закрытии шторки
            }

            override fun onDrawerStateChanged(newState: Int) {
                // Вызывается при изменении состояния шторки
            }
        })

        listenerDrawer()
        val imvNolics = binding.imvNolics
        val imvStars = binding.imvStars
        val imvGold = binding.imvGold
        val imvPremium = binding.imvPremiun

        SetItemMenu.setHomeMenu(
            binding, MENU_HOME, this,
            try {
                profile.pointsSkill ?: 0
            } catch (e: java.lang.Exception) {
                0
            },
            qualification
        )

        val yRotateAnimationDuration = 1000
        val repeatDelay = 60_000L // Задержка между повторениями (1 минута)
        var initialDelay = 1000L // Начальная задержка перед запуском анимации
        var addInitialDelay = 250L

        startAnimationWithRepeat(imvStars, yRotateAnimationDuration, initialDelay, repeatDelay)

        initialDelay += addInitialDelay

        startAnimationWithRepeat(imvNolics, yRotateAnimationDuration, initialDelay, repeatDelay)
        initialDelay += addInitialDelay

        startAnimationWithRepeat(
            imageViewLife1,
            yRotateAnimationDuration,
            initialDelay,
            repeatDelay
        )
        initialDelay += addInitialDelay

        startAnimationWithRepeat(
            imageViewLife2,
            yRotateAnimationDuration,
            initialDelay,
            repeatDelay
        )
        initialDelay += addInitialDelay

        startAnimationWithRepeat(
            imageViewLife3,
            yRotateAnimationDuration,
            initialDelay,
            repeatDelay
        )
        initialDelay += addInitialDelay

        startAnimationWithRepeat(
            imageViewLife4,
            yRotateAnimationDuration,
            initialDelay,
            repeatDelay
        )
        initialDelay += addInitialDelay

        startAnimationWithRepeat(
            imageViewLife5,
            yRotateAnimationDuration,
            initialDelay,
            repeatDelay
        )
        initialDelay += addInitialDelay

        startAnimationWithRepeat(imageViewGold, yRotateAnimationDuration, initialDelay, repeatDelay)
        initialDelay += addInitialDelay

        startAnimationWithRepeat(imvGold, yRotateAnimationDuration, initialDelay, repeatDelay)
        initialDelay += addInitialDelay

        startAnimationWithRepeat(imvPremium, yRotateAnimationDuration, initialDelay, repeatDelay)
        initialDelay += addInitialDelay


        viewModel.tpovIdLiveData.value = getTpovId()

        createTimer()
    }

    private fun showNotification(skill: Int?) {
        val userguide = UserGuide(this)
        userguide.setCounterValue(skill ?: 0)

        var id = 1
            // getPersonalMassage().forEach { it: ChatEntity ->
       //     userguide.addNotification(
       //         text = it.msg,
       //         titleText = "Mersonal sms by: ${it.user}",
       //         icon = getResources.getUserIcon(it.icon, this)
                //     )
       // }

        userguide.addNotification(
            id++,
            text = "Привет мой друг, ты прошел не легкий путь от Новичка до Легенды через всю дорогу препятствий и трудностей, я желаю что-бы тебе был безвозместный успех в жизни так же, как и в этой игре. \n  Признаюсь, мы так же шли по не легкой дороге, что-бы сделать все условия, что-бы наши замечатильные игроки, дошли до этой черты. Я этот текст пишу на этапе альфа версии квеста, но надеюсь, мы дойдем до момента когда наши игроки возьмут Легенду. Благодарим тебя за большой вклад в наше сообщество, каждый из вас действительно важен. Могу предложить податься к нам в работу, возможно именно у нас ты так же сможешь показать весь потенциал. Удачи! ",
            titleText = "Поздравление от разработчика игры.",
            options = Options(countKey = 1000_0000),
            icon = resources.getDrawable(R.drawable.star_full)
        )
        userguide.addNotification(
            id++,
            text = "Вы получили звание - Легенда",
            titleText = "Your skill = $skill",
            options = Options(countKey = 1000_0000),
            icon = resources.getDrawable(R.drawable.star_full)
        )
        userguide.addNotification(
            id++,
            text = "Вы получили звание - Єксперт",
            titleText = "Your skill = $skill",
            options = Options(countKey = 500_0000),
            icon = resources.getDrawable(R.drawable.star_full)
        )
        userguide.addNotification(
            id++,
            text = "Вы получили звание - Гроссместер",
            titleText = "Your skill = $skill",
            options = Options(countKey = 250_0000),
            icon = resources.getDrawable(R.drawable.star_full)
        )
        userguide.addNotification(
            id++,
            text = "Вы получили звание - Ветеран",
            titleText = "Your skill = $skill",
            options = Options(countKey = 130_0000),
            icon = resources.getDrawable(R.drawable.star_full)
        )
        userguide.addNotification(
            id++,
            text = "Вы можете просматривать профили других игроков по другим различным фильтрам",
            titleText = "Your skill = $skill",
            options = Options(countKey = 60_0000),
            icon = resources.getDrawable(R.drawable.nav_user)
        )
        userguide.addNotification(
            id++,
            text = "Вы получили звание - Любитель",
            titleText = "Your skill = $skill",
            options = Options(countKey = 60_0000),
            icon = resources.getDrawable(R.drawable.star_full)
        )
        userguide.addNotification(
            id++,
            text = "Вы можете смотреть список лучших игроков в этой игре по разным критериям",
            titleText = "Your skill = $skill",
            options = Options(countKey = 2_0000),
            icon = resources.getDrawable(R.drawable.nav_leader)
        )
        userguide.addNotification(
            id++,
            text = "Вы получили звание - Игрок",
            titleText = "Your skill = $skill",
            options = Options(countKey = 2_0000),
            icon = resources.getDrawable(R.drawable.star_full)
        )
        /////////////////////////////

        userguide.addNotification(
            id++,
            text = "Вы получили звание - Новичек",
            titleText = "Your skill = $skill",
            options = Options(countKey = 2000),
            icon = resources.getDrawable(R.drawable.star_full)
        )
        userguide.addNotification(
            id++,
            text = "Вы можете создавать собственные квесты.",
            titleText = "Your skill = $skill",
            options = Options(countKey = 2000),
            icon = resources.getDrawable(R.drawable.nav_my_quiz)
        )
        userguide.addNotification(
            id++,
            text = "Арена - здесь находятся квесты других игроков и их могут проходить все игроки.",
            titleText = "Your skill = $skill",
            options = Options(countKey = 2000),
            icon = resources.getDrawable(R.drawable.baseline_public_24)
        )
        userguide.addNotification(
            id++,
            text = "У тебя отлично получается! В этой игре еще есть много всего интересного, по мере набирания опыта - у тебя открывается различный функционал. Дерзай дальше!",
            titleText = "Your skill = $skill",
            options = Options(countKey = 200),
            icon = resources.getDrawable(R.drawable.star_full)
        )

        /////////////////////////////////////////
        if(skill != 0) userguide.addNotification(
            id++,
            "Это индикатор, который отображает перведен ли квест на ваш язык, на него можно нажать. \nЕсли квест не переведен, вы можете нажать на перевести\n - Синий - квест переведен хорошо \n - Желтый - квест переведен переводчиком\n - Серый - квест переведен",
            titleText = "Перевод квеста",
            icon = getDrawable(R.drawable.ic_translate),
        )

        if(skill != 0) userguide.addNotification(
            id++,
            titleText = "Your skill = $skill",
            text = "Добро пожаловать в квиз правда-ложь, готов потрясти своими извинилами? Нажимай на квест который больше всего нравится.",
            icon = resources.getDrawable(R.drawable.star_full)
        )

    }

    private fun getPersonalMassage(): List<ChatEntity> {
        return viewModel.getMassage()
    }

    private fun showPopupInfo(event: MotionEvent, popupType: Int) {
        val context = this
        // Create the popup window
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.translation_popup_layout, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        // Configure the popup window
        val tvPopup1 = popupView.findViewById<TextView>(R.id.tv_popup_1)
        val tvPopup2 = popupView.findViewById<TextView>(R.id.tv_popup_2)
        val tvPopup3 = popupView.findViewById<TextView>(R.id.tv_popup_3)
        val tvPopup4 = popupView.findViewById<TextView>(R.id.tv_popup_4)
        val tvPopup5 = popupView.findViewById<TextView>(R.id.tv_popup_5)
        val tvPopup6 = popupView.findViewById<TextView>(R.id.tv_popup_6)
        val tvPopup7 = popupView.findViewById<TextView>(R.id.tv_popup_7)
        val tvPopup8 = popupView.findViewById<TextView>(R.id.tv_popup_8)
        val tvPopup9 = popupView.findViewById<TextView>(R.id.tv_popup_9)
        val spListPopup1 = popupView.findViewById<Spinner>(R.id.sp_list_popup_1)
        val bPopup1 = popupView.findViewById<Button>(R.id.b_popup_1)
        val layoutSp = popupView.findViewById<LinearLayout>(R.id.l_sp)
        val layoutB = popupView.findViewById<LinearLayout>(R.id.l_b)

        if (popupType == MainActivityAdapter.POPUP_LIFE) {
            spListPopup1.visibility = View.GONE
            bPopup1.visibility = View.GONE

            layoutB.visibility = View.GONE
            layoutSp.visibility = View.GONE

            tvPopup1.visibility = View.VISIBLE
            tvPopup2.visibility = View.GONE
            tvPopup3.visibility = View.GONE
            tvPopup4.visibility = View.GONE
            tvPopup5.visibility = View.GONE
            tvPopup6.visibility = View.GONE
            tvPopup7.visibility = View.GONE
            tvPopup8.visibility = View.GONE
            tvPopup9.visibility = View.GONE

            try {
                tvPopup1.text = "Life count: ${viewModel.getProfileCount()}/${
                    viewModel.getProfileCountLife()
                        ?.times(100)
                } (${
                    (viewModel.getProfileCount()?.times(100))?.div(
                        viewModel.getProfileCountLife()
                            ?.times(100)!!
                    )
                }%)"
            } catch (e: Exception) {
                Toast.makeText(this, "Error get life counts", Toast.LENGTH_LONG).show()
            }

        } else if (popupType == MainActivityAdapter.POPUP_LIFE_GOLD) {
            tvPopup1.visibility = View.VISIBLE
            tvPopup2.visibility = View.GONE
            tvPopup3.visibility = View.GONE
            tvPopup4.visibility = View.GONE
            tvPopup5.visibility = View.GONE
            tvPopup6.visibility = View.GONE
            tvPopup7.visibility = View.GONE
            tvPopup8.visibility = View.GONE
            tvPopup9.visibility = View.GONE

            layoutB.visibility = View.GONE
            layoutSp.visibility = View.GONE

            spListPopup1.visibility = View.GONE
            bPopup1.visibility = View.GONE


            try {
                tvPopup1.text = "Life count: ${viewModel.getProfileCountGold()}/${
                    viewModel.getProfileCountGoldLife()
                        ?.times(100)
                } (${
                    (viewModel.getProfileCountGold()?.times(100))?.div(
                        viewModel.getProfileCountGoldLife()
                            ?.times(100)!!
                    )
                }%)"
            } catch (e: Exception) {
                Toast.makeText(this, "Error get life counts", Toast.LENGTH_LONG).show()
            }
        }

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupWidth = popupView.measuredWidth
        val popupHeight = popupView.measuredHeight
        val touchX = event.rawX
        val touchY = event.rawY
        popupWindow.width = popupWidth
        popupWindow.height = popupHeight
        popupWindow.showAtLocation(
            binding.viewBackground,
            Gravity.NO_GRAVITY,
            touchX.toInt() + 16,
            touchY.toInt() + 16
        )
    }

    private var timer: Timer? = null

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createTimer() {
        timer = Timer()
        val delay = 0L // Delay before the timer starts executing the task (in milliseconds)
        val period =
            100_000L // Interval between consecutive executions of the task (in milliseconds)
        val task = object : TimerTask() {
            override fun run() {
                updateProfileCount(period)
            }
        }

        // Schedule the task to run every minute, starting after the specified delay
        timer?.scheduleAtFixedRate(task, delay, period)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateProfileCount(period: Long) {
        try {
            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.post {
                val profile = viewModel.getProfile()
                val userguide = UserGuide(this)

                if ((profile.translater ?: 0) >= 100) userguide.addNotification(
                    -1,
                    text = "Translater 1 lvl: ${profile.translater} points",
                    titleText = "New qualification",
                    icon = resources.getDrawable(R.drawable.star_full)
                )
                if ((profile.translater ?: 0) >= 200) userguide.addNotification(
                    -2,
                    titleText = "New qualification",
                    text = "Translater 2 lvl: ${profile.translater} points",
                    icon = resources.getDrawable(R.drawable.star_full)
                )
                if ((profile.translater ?: 0) >= 300) userguide.addNotification(
                    -3,
                    titleText = "New qualification",
                    text = "Translater 3 lvl: ${profile.translater} points",
                    icon = resources.getDrawable(R.drawable.star_full)
                )
                if ((profile.tester ?: 0) >= 100) userguide.addNotification(
                    -4,
                    text = "Tester: ${profile.tester} points",
                    titleText = "New qualification",
                    icon = resources.getDrawable(R.drawable.star_full)
                )
                if ((profile.moderator ?: 0) >= 100) userguide.addNotification(
                    -5,
                    text = "Moderator: ${profile.moderator} points",
                    titleText = "New qualification",
                    icon = resources.getDrawable(R.drawable.star_full)
                )
                if ((profile.admin ?: 0) >= 100) userguide.addNotification(
                    -6,
                    text = "Admin: ${profile.admin} points",
                    titleText = "New qualification",
                    icon = resources.getDrawable(R.drawable.star_full)
                )
                if ((profile.developer ?: 0) >= 100) userguide.addNotification(
                    -7,
                    text = "Developer: ${profile.developer} points",
                    titleText = "New qualification",
                    icon = resources.getDrawable(R.drawable.star_full)
                )

                if ((profile.addPointsGold ?: 0) != 0) userguide.addNotification(
                    0,
                    text = "Золото: ${profile.addPointsGold} points",
                    titleText = "Награда от разработчиков",
                    icon = resources.getDrawable(R.drawable.ic_gold)
                )

                if ((profile.addPointsNolics ?: 0) != 0) userguide.addNotification(
                    0,
                    text = "Нолики: ${profile.addPointsNolics} points",
                    titleText = "Награда от разработчиков",
                    icon = resources.getDrawable(R.drawable.ic_gold)
                )

                if ((profile.addPointsSkill ?: 0) != 0) userguide.addNotification(
                    0,
                    text = "Опыт: ${profile.addPointsSkill} points",
                    titleText = "Награда от разработчиков",
                    icon = resources.getDrawable(R.drawable.ic_gold)
                )

                if (profile.addTrophy != "") userguide.addNotification(
                    0,
                    text = "Трофеи: ${profile.addTrophy} points",
                    titleText = "Награда от разработчиков",
                    icon = resources.getDrawable(R.drawable.baseline_favorite_24)
                )

                if (profile.addMassage != "") userguide.addNotification(
                    0,
                    text = " - ${profile.addMassage}",
                    titleText = "Developer massage"
                )

                val countSmsPoints = getCountMassageIdAndReset()

                log("lklklkl 1 $countSmsPoints")
                log("lklklkl 2 ${getSkillByCountInChat(countSmsPoints)}")
                viewModel.updateProfileUseCase(
                    profile.copy(
                        count = calcCount(
                            profile.count,
                            profile.countLife,
                            profile.dateCloseApp
                        ),
                        dateCloseApp = TimeManager.getCurrentTime(),
                        pointsSkill = (profile.pointsSkill!!.plus(
                            if (supportFragmentManager.findFragmentById(R.id.title_fragment) is ChatFragment) {
                                getSkillByTimeInChat(period.toInt())
                            } else {
                                getSkillByTimeInGame(period.toInt())
                            }
                        ).plus(profile.addPointsNolics ?: 0)
                            .plus(getSkillByCountInChat(countSmsPoints))),
                        pointsGold = profile.pointsGold?.plus(profile.addPointsGold ?: 0),
                        pointsNolics = profile.pointsNolics?.plus(profile.addPointsNolics ?: 0),
                        trophy = profile.trophy + profile.addTrophy,
                        timeInGamesInQuiz = if (supportFragmentManager.findFragmentById(R.id.title_fragment) is ChatFragment) {
                            profile.timeInGamesInQuiz
                        } else {
                            profile.timeInGamesInQuiz
                                ?.plus(1)
                        },

                        timeInGamesInChat = if (supportFragmentManager.findFragmentById(R.id.title_fragment) is ChatFragment) {
                            profile.timeInGamesInChat
                                ?.plus(1)
                        } else {
                            profile.timeInGamesInChat
                        },

                        timeInGamesSmsPoints = profile.timeInGamesSmsPoints?.plus(
                            countSmsPoints
                        ),

                        addPointsGold = 0,
                        addPointsNolics = 0,
                        addPointsSkill = 0,
                        addTrophy = "",
                        addMassage = ""
                    )
                )
            }
        } catch (e: Exception) {

        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun calcCount(count: Int?, countLife: Int?, dateCloseApp: String?): Int {
        val countSecTime =
            TimeManager.getSecondBetweenDates(dateCloseApp ?: "0", TimeManager.getCurrentTime())
        val calcCount = countSecTime?.times(0.025)

        val calcAllCount = count?.let { calcCount?.plus(it)?.toInt() }

        log("calcAllCount: $calcAllCount")
        return if (getMaxCount(countLife) < calcAllCount!!) getMaxCount(countLife)
        else calcAllCount

    }

    private fun getMaxCount(countLife: Int?): Int {
        return when (countLife) {
            1 -> 100
            2 -> 200
            3 -> 300
            4 -> 400
            5 -> 500
            else -> 0
        }
    }


    private fun animateValue(
        textView: TextView,
        startValue: Int,
        endValue: Int,
        duration: Long,
        startDelay: Long
    ) {
        val valueAnimator = ValueAnimator.ofInt(startValue, endValue).apply {
            setDuration(duration)
            setStartDelay(startDelay)
            interpolator = LinearInterpolator()
        }
        valueAnimator.addUpdateListener { animation ->
            textView.text = NumberFormat.getIntegerInstance().format(animation.animatedValue)
        }
        valueAnimator.start()
    }

    private fun animateValueFloat(
        textView: TextView,
        startValue: Float,
        endValue: Float,
        duration: Long,
        startDelay: Long
    ) {
        val valueAnimator = ValueAnimator.ofFloat(startValue, endValue).apply {
            setDuration(duration)
            setStartDelay(startDelay)
            interpolator = LinearInterpolator()
        }
        valueAnimator.addUpdateListener { animation ->
            textView.text = String.format("%.1f", animation.animatedValue)
        }
        valueAnimator.start()
    }

    private fun showTextWithDelay(textView: TextView, text: String, delayInMillis: Long) {
        CoroutineScope(Dispatchers.Main).launch {
            val spannableText = SpannableStringBuilder()
            for (char in text) {
                val start = spannableText.length
                spannableText.append(char.toString())
                spannableText.setSpan(
                    ForegroundColorSpan(Color.WHITE),
                    start,
                    start + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                textView.text = spannableText
                delay(delayInMillis)

                // Возвращаем цвет буквы к исходному (черный в данном случае)
                spannableText.setSpan(
                    ForegroundColorSpan(Color.BLACK),
                    start,
                    start + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                textView.text = spannableText
            }
        }
    }

    private fun startAnimationWithRepeat(
        imageView: ImageView,
        duration: Int,
        initialDelay: Long,
        repeatDelay: Long
    ) {
        val animator = ObjectAnimator.ofFloat(imageView, "rotationY", 0f, 360f).apply {
            this.duration = duration.toLong()
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                animation.removeListener(this)
                imageView.postDelayed({
                    animation.addListener(this)
                    animation.start()
                }, repeatDelay)
            }
        })

        imageView.postDelayed({
            animator.start()
        }, initialDelay)
    }

    // Использование функции в вашем коде
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        return super.onPrepareOptionsMenu(menu)
    }

    private fun listenerDrawer() {

        log("fun listenerDrawer()")

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->

            binding.drawerLayout.closeDrawer(GravityCompat.START)
            // ваш код обработки нажатия на элемент меню

            log("listenerDrawer() menuItem: ${menuItem.toString()}")
            val profile = viewModel.getProfile()
            val qualification = Qualification(
                profile.tester ?: 0,
                profile.moderator ?: 0,
                profile.sponsor ?: 0,
                profile.translater ?: 0,
                profile.admin ?: 0,
                profile.developer ?: 0
            )

            when (menuItem.toString()) {
                resources.getString(R.string.nav_profile) -> {
                    FragmentManager.setFragment(ProfileFragment.newInstance(), this)
                    SetItemMenu.setNetworkMenu(
                        binding,
                        MENU_PROFILE,
                        this,
                        profile.pointsSkill ?: 0,
                        qualification
                    )
                }
                resources.getString(R.string.nav_chat) -> {
                    FragmentManager.setFragment(ChatFragment.newInstance(), this)
                    SetItemMenu.setNetworkMenu(
                        binding,
                        MENU_CHAT,
                        this,
                        profile.pointsSkill ?: 0,
                        qualification
                    )
                }

                resources.getString(R.string.nav_downloads) -> {
                    FragmentManager.setFragment(DownloadFragment(), this)
                    SetItemMenu.setHomeMenu(
                        binding,
                        MENU_DOWNLOADS,
                        this,
                        profile.pointsSkill ?: 0,
                        qualification
                    ) // Используйте подходящий номер пункта меню
                }

                resources.getString(R.string.nav_enter) -> {
                    SetItemMenu.setNetworkMenu(
                        binding, MENU_PROFILE, this,
                        profile.pointsSkill ?: 0,
                        qualification
                    )
                }

                resources.getString(R.string.nav_exit) -> {
                    FirebaseAuth.getInstance().signOut()
                    SetItemMenu.setNetworkMenu(
                        binding, MENU_EXIT, this,
                        profile.pointsSkill ?: 0,
                        qualification
                    )
                }

                resources.getString(R.string.nav_contact) -> {
                    log("ContactList123: 1")
                    if (ContextCompat.checkSelfPermission(
                            this,
                            READ_CONTACTS
                        ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            this,
                            WRITE_CONTACTS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        // Разрешения уже предоставлены, выполнить нужную функцию
                        log("ContactList123: 2.2")
                        clickNavMenuContact(profile, qualification)

                    } else {
                        // Разрешения не предоставлены, запросить их
                        requestContactsPermission()
                    }
                }

                resources.getString(R.string.nav_global) -> {
                    FragmentManager.setFragment(FragmentMain.newInstance(5), this)
                    SetItemMenu.setNetworkMenu(
                        binding, MENU_ARENA, this,
                        profile.pointsSkill ?: 0,
                        qualification
                    )
                }

                resources.getString(R.string.nav_friends) -> {
                    SetItemMenu.setNetworkMenu(
                        binding, MENU_FRIEND, this,
                        profile.pointsSkill ?: 0,
                        qualification
                    )
                }

                resources.getString(R.string.nav_home) -> {
                    FragmentManager.setFragment(FragmentMain.newInstance(8), this)
                    SetItemMenu.setHomeMenu(
                        binding, MENU_HOME, this,
                        profile.pointsSkill ?: 0,
                        qualification
                    )
                }

                resources.getString(R.string.nav_leaders) -> {
                    SetItemMenu.setNetworkMenu(
                        binding, MENU_LEADER, this,
                        profile.pointsSkill ?: 0,
                        qualification
                    )
                }

                resources.getString(R.string.nav_massages) -> {
                    SetItemMenu.setNetworkMenu(
                        binding, MENU_MASSAGE, this,
                        profile.pointsSkill ?: 0,
                        qualification
                    )
                }

                resources.getString(R.string.nav_my_quiz) -> {
                    FragmentManager.setFragment(FragmentMain.newInstance(1), this)
                    SetItemMenu.setHomeMenu(
                        binding, MENU_MY_QUIZ, this,
                        profile.pointsSkill ?: 0,
                        qualification
                    )
                }

                resources.getString(R.string.nav_news) -> {
                    SetItemMenu.setNetworkMenu(
                        binding, MENU_NEWS, this,
                        profile.pointsSkill ?: 0,
                        qualification
                    )
                }

                resources.getString(R.string.nav_players) -> {
                    FragmentManager.setFragment(UsersFragment.newInstance(), this)
                    SetItemMenu.setNetworkMenu(
                        binding, MENU_USERS, this,
                        profile.pointsSkill ?: 0,
                        qualification
                    )
                }

                resources.getString(R.string.nav_reports) -> {

                    SetItemMenu.setNetworkMenu(
                        binding, MENU_REPORT, this,
                        profile.pointsSkill ?: 0,
                        qualification
                    )
                }

                resources.getString(R.string.nav_task) -> {
                    FragmentManager.setFragment(EventFragment.newInstance(), this)
                    SetItemMenu.setNetworkMenu(
                        binding, MENU_EVENT, this,
                        profile.pointsSkill ?: 0,
                        qualification
                    )
                }

                resources.getString(R.string.nav_settings) -> {
                    FragmentManager.setFragment(SettingsFragment.newInstance(), this)
                    SetItemMenu.setHomeMenu(
                        binding, MENU_SETTING, this,
                        profile.pointsSkill ?: 0,
                        qualification
                    )
                }

            }

            binding.navigationView.inflateMenu(R.menu.navigation_manu)
            true // не забудьте вернуть значение true, чтобы показать, что событие было обработано

        }
    }

    private fun getLogsContacts() {
        Contacts.initialize(this)
        Contacts.getQuery().find().forEach {
            if (it.givenName?.contains("Oleg") == true) {
                log("ContactList123: id: ${it.id}.")
                log("ContactList123: addresses: ${it.addresses}.")
                log("ContactList123: addresses: ${it.addresses}.")
                log("ContactList123: anniversary: ${it.anniversary}.")
                log("ContactList123: birthday: ${it.birthday}.")
                log("ContactList123: companyName: ${it.companyName}.")
                log("ContactList123: emails: ${it.emails}.")
                log("ContactList123: events: ${it.events}.")
                log("ContactList123: familyName: ${it.familyName}.")
                log("ContactList123: displayName: ${it.displayName}.")
                log("ContactList123: givenName: ${it.givenName}.")
                it.phoneNumbers.forEach {
                    log("ContactList123: phoneNumbers: ${it.number}.")
                }
                log("ContactList123: photoUri: ${it.photoUri}.")
                log("ContactList123: addresses: ${it.addresses}.")
                log("ContactList123: _________________________________________")
            }
        }
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE),
            REQUEST_CODE_STORAGE_PERMISSION
        )
    }

    private fun requestContactsPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(READ_CONTACTS, WRITE_CONTACTS),
            REQUEST_CODE_CONTACTS_PERMISSION
        )
    }

    //Окраживаем квадратики в красный и зеленый в зависимости сколько осталось запасных вопросов-дня
    private fun loadNumBoxDay() = with(binding) {
        log("numberBox setValue numDayPrizeBox: $numDayPrizeBox")
        if (numDayPrizeBox > 0) textView10.setBackgroundResource(R.color.num_chack_norice_green)
        if (numDayPrizeBox > 1) textView9.setBackgroundResource(R.color.num_chack_norice_green)
        if (numDayPrizeBox > 2) textView8.setBackgroundResource(R.color.num_chack_norice_green)
        if (numDayPrizeBox > 3) textView7.setBackgroundResource(R.color.num_chack_norice_green)
        if (numDayPrizeBox > 4) textView6.setBackgroundResource(R.color.num_chack_norice_green)
        if (numDayPrizeBox > 5) textView5.setBackgroundResource(R.color.num_chack_norice_green)
        if (numDayPrizeBox > 6) textView4.setBackgroundResource(R.color.num_chack_norice_green)
        if (numDayPrizeBox > 7) textView3.setBackgroundResource(R.color.num_chack_norice_green)
        if (numDayPrizeBox > 8) textView2.setBackgroundResource(R.color.num_chack_norice_green)
        if (numDayPrizeBox > 9) textView.setBackgroundResource(R.color.num_chack_norice_green)
    }

    fun replaceFragment(fragment: Fragment) {
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()
        transaction.replace(R.id.title_fragment, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_CONTACTS_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val profile = viewModel.getProfile()
                val qualification = Qualification(
                    profile.tester ?: 0,
                    profile.moderator ?: 0,
                    profile.sponsor ?: 0,
                    profile.translater ?: 0,
                    profile.admin ?: 0,
                    profile.developer ?: 0
                )

                log("ContactList123: 2.1")
                // Contacts permission is granted, proceed with accessing the contacts provider
                clickNavMenuContact(profile, qualification)
            } else {
                // Contacts permission is denied, handle this situation accordingly
                Toast.makeText(
                    this,
                    "Contacts permission is required to use this app",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED
            ) {
                // Storage permissions are granted, execute the necessary function
                viewModel =
                    ViewModelProvider(this, viewModelFactory)[MainActivityViewModel::class.java]
                viewModel.init()
            } else {
                // Storage permissions are not granted, show an error message
                Toast.makeText(
                    this,
                    "Storage permission is required to use this app",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun clickNavMenuContact(profile: ProfileEntity, qualification: Qualification) {
// Permissions are already granted, proceed with accessing the contacts provider
        log("ContactList123: 3")
        getLogsContacts()
        SetItemMenu.setNetworkMenu(
            binding, MENU_CONTACT, this,
            profile.pointsSkill ?: 0,
            qualification
        )
    }


    private fun setVisibleMenu(itemId: MenuItem) {

        val profile = viewModel.getProfile()
        val qualification = Qualification(
            profile.tester ?: 0,
            profile.moderator ?: 0,
            profile.sponsor ?: 0,
            profile.translater ?: 0,
            profile.admin ?: 0,
            profile.developer ?: 0
        )
        log("fun setVisibleMenu() itemId.itemId = $itemId")

        when (itemId.itemId) {

            R.id.menu_home -> {
                if (fr1 != 1) {

                    swipeRefreshLayout.isEnabled = true
                    SetItemMenu.setHomeMenu(
                        binding, MENU_HOME, this,
                        profile.pointsSkill ?: 0,
                        qualification
                    )
                    fr1 = 1
                }
            }
//
//            R.id.menu_adb -> {
//                if (fr1 != 2) {
//
//                    fr1 = 2
//                }
//            }
//
//            R.id.menu_info -> {
//                if (fr1 != 4) {
//
//                    fr1 = 4
//                }
//            }

            R.id.menu_network -> {
                if (fr1 != 2) {

                    swipeRefreshLayout.isEnabled = false
                    SetItemMenu.setNetworkMenu(
                        binding, MENU_PROFILE, this,
                        profile.pointsSkill ?: 0,
                        qualification
                    )
                    fr1 = 2
                }
            }
        }
    }

    private fun setButtonNavListener() {

        log("fun setButtonNavListener()")
        binding.bNav.setOnItemSelectedListener {
            val profile = viewModel.getProfile()
            val qualification = try {
                Qualification(
                    profile.tester ?: 0,
                    profile.moderator ?: 0,
                    profile.sponsor ?: 0,
                    profile.translater ?: 0,
                    profile.admin ?: 0,
                    profile.developer ?: 0
                )
            } catch (e: Exception) {
                Qualification(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
                )
            }
            setVisibleMenu(it)
            when (it.itemId) {

                R.id.menu_home -> {

                    log("setButtonNavListener() menu_home")
                    FragmentManager.setFragment(FragmentMain.newInstance(8), this)
                    SetItemMenu.setHomeMenu(
                        binding, MENU_HOME, this,
                        profile.pointsSkill ?: 0,
                        qualification
                    )
                }

                R.id.menu_adb -> {
                    FragmentManager.setFragment(ShopFragment.newInstance(), this)
                }

                R.id.menu_info -> {
                    log("setButtonNavListener() menu_info")
                    FragmentManager.setFragment(InfoFragment.newInstance(), this)
                }

                R.id.menu_network -> {
                    startProfile()
                }
            }
            true
        }
    }

    private fun startProfile() {
        swipeRefreshLayout.isEnabled = false
        val profile = viewModel.getProfile()
        val qualification = Qualification(
            profile.tester ?: 0,
            profile.moderator ?: 0,
            profile.sponsor ?: 0,
            profile.translater ?: 0,
            profile.admin ?: 0,
            profile.developer ?: 0
        )
        SetItemMenu.setNetworkMenu(
            binding, MENU_PROFILE, this,
            viewModel.getProfileSkill() ?: 0,
            qualification
        )
        log("setButtonNavListener() menu_network")
        val user = FirebaseAuth.getInstance()
        if (user.currentUser != null) {
            log("setButtonNavListener() Аккаунт зареган")
            Toast.makeText(this@MainActivity, "Аккаунт найден", Toast.LENGTH_LONG)
                .show()
            swipeRefreshLayout.isEnabled = false
            FragmentManager.setFragment(ProfileFragment.newInstance(), this)

        } else {

            log("setButtonNavListener() Аккаунт не зареган")
            Toast.makeText(
                this@MainActivity,
                "Аккаунт не найден, авторизуйтесь.",
                Toast.LENGTH_LONG
            ).show()
            FragmentManager.setFragment(AutorisationFragment.newInstance(), this)
        }

    }

    fun log(massage: String) {
        Logcat.log(massage, "MainActivity", Logcat.LOG_ACTIVITY)
    }

    companion object {
        const val REQUEST_CODE_STORAGE_PERMISSION = 1001
        const val REQUEST_CODE_CONTACTS_PERMISSION = 1002
    }
}

