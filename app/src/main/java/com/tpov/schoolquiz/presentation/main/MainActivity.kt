package com.tpov.schoolquiz.presentation.main

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.*
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.firebase.auth.FirebaseAuth
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.ActivityMainBinding
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.custom.CalcValues.getSkillByTimeInChat
import com.tpov.schoolquiz.presentation.custom.CalcValues.getSkillByTimeInGame
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getCountStartApp
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.setCountStartApp
import com.tpov.schoolquiz.presentation.dowload.DownloadFragment
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import com.tpov.schoolquiz.presentation.fragment.FragmentManager
import com.tpov.schoolquiz.presentation.main.info.InfoFragment
import com.tpov.schoolquiz.presentation.network.AutorisationFragment
import com.tpov.schoolquiz.presentation.network.chat.ChatFragment
import com.tpov.schoolquiz.presentation.network.event.EventFragment
import com.tpov.schoolquiz.presentation.network.profile.ProfileFragment
import com.tpov.schoolquiz.presentation.network.profile.UsersFragment
import com.tpov.schoolquiz.presentation.setting.SettingsFragment
import com.tpov.schoolquiz.presentation.shop.ShopFragment
import com.tpov.shoppinglist.utils.TimeManager
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import java.text.NumberFormat
import java.util.*
import javax.inject.Inject

/**
 * This is the main screen of the application, it consists of a panel that shows how much spare is left.
 * questions of the day and a fragment that displays user and system questions
 */

@InternalCoroutinesApi
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

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

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        component.inject(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        log("onCreate()")
        // Remove the action bar
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
                )?.toInt() ?: 0,
                TimeManager.getDaysBetweenDates(
                    it?.datePremium ?: "",
                    TimeManager.getCurrentTime()
                )
                    ?.toInt() ?: 0,
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
        }
        pb_life1.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                // Rating bar clicked, handle the event here
                // You can call your method to show the translation popup/dialog
                showPopupInfo(event, MainActivityAdapter.POPUP_LIFE)
            }
            true
        }
        pb_life2.setOnTouchListener { view, event ->
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

        FragmentManager.setFragment(FragmentMain.newInstance(8), this)
        SetItemMenu.setHomeMenu(binding, 1, this)

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

        SetItemMenu.setHomeMenu(binding, fr2, this)

        val yRotateAnimationDuration = 1000
        val repeatDelay = 6000L // Задержка между повторениями (1 минута)
        var initialDelay = 1000L // Начальная задержка перед запуском анимации
        var addInitialDelay = 250L


        showTextWithDelay(binding.tvPbLoad, "Соединение с сервером...", 50)

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
        popupWindow.width = popupWidth.toInt()
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
            viewModel.updateProfileUseCase(
                viewModel.getProfile().copy(
                    count = calcCount(
                        viewModel.getProfileCount(),
                        viewModel.getProfileCountLife(),
                        viewModel.getProfileDateCloseAp()
                    ),
                    dateCloseApp = TimeManager.getCurrentTime(),
                    pointsSkill = (viewModel.getProfileSkill()!!.plus(
                        if (supportFragmentManager.findFragmentById(R.id.title_fragment) is ChatFragment) {
                            getSkillByTimeInChat(period.toInt())
                        } else {
                            getSkillByTimeInGame(period.toInt())
                        })),
                    timeInGamesInQuiz =
                    if (supportFragmentManager.findFragmentById(R.id.title_fragment) is ChatFragment) {
                        viewModel.getProfileTimeInGame()
                    } else {
                        viewModel.getProfileTimeInGame()
                            ?.plus(getSkillByTimeInGame(period.toInt()))
                    },

                    timeInGamesInChat = if (supportFragmentManager.findFragmentById(R.id.title_fragment) is ChatFragment) {
                        viewModel.getProfileTimeInChat()
                            .plus(getSkillByTimeInGame(period.toInt()))
                    } else {
                        viewModel.getProfileTimeInChat()
                    }
                ))
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
            when (menuItem.toString()) {
                resources.getString(R.string.nav_chat) -> {
                    FragmentManager.setFragment(ChatFragment.newInstance(), this)
                    SetItemMenu.setNetworkMenu(binding, 3, this)
                }

                resources.getString(R.string.nav_downloads) -> {
                    FragmentManager.setFragment(DownloadFragment(), this)
                    SetItemMenu.setHomeMenu(
                        binding,
                        4,
                        this
                    ) // Используйте подходящий номер пункта меню
                }

                resources.getString(R.string.nav_enter) -> {
                    SetItemMenu.setNetworkMenu(binding, 10, this)
                }

                resources.getString(R.string.nav_exit) -> {
                    SetItemMenu.setNetworkMenu(binding, 11, this)
                    FirebaseAuth.getInstance().signOut()
                }

                resources.getString(R.string.nav_global) -> {
                    FragmentManager.setFragment(FragmentMain.newInstance(5), this)
                    SetItemMenu.setNetworkMenu(binding, 8, this)
                }

                resources.getString(R.string.nav_friends) -> {
                    SetItemMenu.setNetworkMenu(binding, 9, this)
                }

                resources.getString(R.string.nav_home) -> {
                    FragmentManager.setFragment(FragmentMain.newInstance(8), this)
                    SetItemMenu.setHomeMenu(binding, 1, this)
                }

                resources.getString(R.string.nav_leaders) -> {
                    SetItemMenu.setNetworkMenu(binding, 11, this)
                }

                resources.getString(R.string.nav_massages) -> {
                    SetItemMenu.setNetworkMenu(binding, 5, this)
                }

                resources.getString(R.string.nav_my_quiz) -> {
                    FragmentManager.setFragment(FragmentMain.newInstance(1), this)
                    SetItemMenu.setHomeMenu(binding, 2, this)
                }

                resources.getString(R.string.nav_news) -> {
                    SetItemMenu.setNetworkMenu(binding, 7, this)
                }

                resources.getString(R.string.nav_players) -> {
                    FragmentManager.setFragment(UsersFragment.newInstance(), this)
                    SetItemMenu.setNetworkMenu(binding, 6, this)
                }

                resources.getString(R.string.nav_reports) -> {

                }

                resources.getString(R.string.nav_task) -> {
                    FragmentManager.setFragment(EventFragment.newInstance(), this)
                }

                resources.getString(R.string.nav_settings) -> {
                    FragmentManager.setFragment(SettingsFragment.newInstance(), this)
                }

            }

            binding.navigationView.inflateMenu(R.menu.navigation_manu)
            true // не забудьте вернуть значение true, чтобы показать, что событие было обработано

        }
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE),
            REQUEST_CODE_STORAGE_PERMISSION
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

        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED
            ) {
                // Разрешения получены, выполнить нужную функцию
                viewModel =
                    ViewModelProvider(this, viewModelFactory)[MainActivityViewModel::class.java]
                viewModel.init()
            } else {
                // Разрешения не получены, вывести сообщение об ошибке
                Toast.makeText(
                    this,
                    "Storage permission is required to use this app",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    private fun setVisibleMenu(itemId: MenuItem) {

        log("fun setVisibleMenu()")
        log("setVisibleMenu() itemId.itemId = ${itemId}")
        when (itemId.itemId) {

            R.id.menu_home -> {
                if (fr1 != 1) {
                    SetItemMenu.setHomeMenu(binding, 1, this)
                    fr1 = 1
                }
            }

            R.id.menu_adb -> {
                if (fr1 != 2) {

                    fr1 = 2
                }
            }

            R.id.menu_settings -> {
                if (fr1 != 3) {

                    fr1 = 3
                }
            }

            R.id.menu_info -> {
                if (fr1 != 4) {

                    fr1 = 4
                }
            }

            R.id.menu_network -> {
                if (fr1 != 5) {
                    SetItemMenu.setNetworkMenu(binding, 1, this)
                    fr1 = 5
                }
            }
        }
    }

    private fun setButtonNavListener() {

        log("fun setButtonNavListener()")
        binding.bNav.setOnItemSelectedListener {

            setVisibleMenu(it)
            when (it.itemId) {

                R.id.menu_home -> {

                    log("setButtonNavListener() menu_home")
                    FragmentManager.setFragment(FragmentMain.newInstance(8), this)
                    SetItemMenu.setHomeMenu(binding, 1, this)
                }

                R.id.menu_adb -> {
                    FragmentManager.setFragment(ShopFragment.newInstance(), this)
                }

                R.id.menu_settings -> {
                    log("setButtonNavListener() menu_settings")
                }

                R.id.menu_info -> {
                    log("setButtonNavListener() menu_info")
                    FragmentManager.setFragment(InfoFragment.newInstance(), this)
                }

                R.id.menu_network -> {

                    log("setButtonNavListener() menu_network")
                    val user = FirebaseAuth.getInstance()
                    if (user.currentUser != null) {
                        log("setButtonNavListener() Аккаунт зареган")
                        Toast.makeText(this@MainActivity, "Аккаунт найден", Toast.LENGTH_LONG)
                            .show()

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
            }
            true
        }
    }

    fun log(massage: String) {
        Logcat.log(massage, "MainActivity", Logcat.LOG_ACTIVITY)
    }

    companion object {

        const val REQUEST_CODE_STORAGE_PERMISSION = 1001

    }
}

