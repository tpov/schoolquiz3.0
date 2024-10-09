package com.tpov.schoolquiz.presentation.main

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tpov.common.COUNT_LIFE_POINTS_IN_LIFE
import com.tpov.common.CoastValues.CoastValuesLife.VALUE_COUNT_LIFE
import com.tpov.common.DELAY_SHOW_TEXT_IN_MAINACTIVITY_NICK
import com.tpov.common.data.utils.TimeManager
import com.tpov.common.domain.SettingConfigObject.settingsConfig
import com.tpov.common.presentation.quiz.QuizFragment
import com.tpov.common.presentation.utils.Values
import com.tpov.common.presentation.utils.Values.getColorNickname
import com.tpov.log_api.logger.Logger
import com.tpov.network.presentation.chat.ChatFragment
import com.tpov.network.presentation.friend.FriendsFragment
import com.tpov.network.presentation.leaders.LeadersFragment
import com.tpov.network.presentation.referal.ReferralProgramFragment
import com.tpov.network.presentation.roles.RolesFragment
import com.tpov.schoolquiz.MainApp
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.databinding.ActivityMainBinding
import com.tpov.schoolquiz.presentation.about.AboutFragment
import com.tpov.schoolquiz.presentation.contact.Contacts
import com.tpov.schoolquiz.presentation.core.NewValue.setNewSkill
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager
import com.tpov.schoolquiz.presentation.create_quiz.CreateQuizActivity
import com.tpov.schoolquiz.presentation.dowload.DownloadFragment
import com.tpov.schoolquiz.presentation.setting.SettingsFragment
import com.tpov.shop.presentation.ShopFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneOffset
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject

/**
 * This is the main screen of the application, it consists of a panel that shows how much spare is left.
 * questions of the day and a fragment that displays user and system questions
 */

@InternalCoroutinesApi
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    lateinit var binding: ActivityMainBinding
    lateinit var viewModel: MainViewModel
    private var recreateActivity: Boolean = false

    private var timerStarted = false
    private var timer: Timer? = null
    private var unixTimeDayThis = 0L
    private var unixTimeThis = 0L

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        timer = null
        timerStarted = false
    }


    @SuppressLint("SetTextI18n")
    @Logger
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        var launchCount = sharedPreferences.getInt("launchCount", 0) + 1
        sharedPreferences.edit().putInt("launchCount", launchCount).apply()

        Values.context = this
        setupUI()
        initViewModel()
        setupDrawerLayout()
        initBottomMenu()
        setupAnimations()
        syncProfile()
        initData()
    }

    @Logger
    private fun initData() {
        getDataToday()

        lifecycleScope.launch(Dispatchers.Default) {
            if (!isNetworkAvailable(this@MainActivity)) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Нет подключения к интернету. Попробуйте позже.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }
            viewModel.getNewStructureDataANDQuizzes()
        }
    }

    private fun getDataToday() {
        val currentUnixTime = Instant.now()

        val nextDay = currentUnixTime.atZone(ZoneOffset.UTC)

        unixTimeDayThis = nextDay.toEpochSecond()
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork?.isConnectedOrConnecting == true
    }

    private fun syncProfile() {
        lifecycleScope.launch {
            viewModel.profileState.collect { profile ->
                Log.d("qweqwe", "2 $profile")
                profile?.apply {
                    showNick(nickname, trophy)
                    showPremiumOrBan(datePremium, dateBanned)
                    showPoints(pointsNolics, pointsGold, pointsSkill)
                    showLife(countLife, count, countGoldLife, countGold)
                    showTimerLife(countLife, count, countGoldLife, countGold, dateCloseApp.toLongOrNull(), profile)
                    showFabs(buyQuizPlace, countBox)
                    val newBoxDay = showBoxDayANDGetNew(timeLastOpenBox, coundDayBox)
                    val newPoints = showAddPoints(profile)


                    viewModel.updateProfile(
                        this.copy(
                            coundDayBox = newBoxDay,
                            timeLastOpenBox = unixTimeDayThis.toString(),

                            trophy = newPoints?.trophy ?: this.trophy,
                            pointsSkill = newPoints?.pointsSkill ?: this.pointsSkill,
                            pointsNolics = newPoints?.pointsNolics ?: this.pointsNolics,
                            pointsGold = newPoints?.pointsGold ?: this.pointsGold,

                            addTrophy = "",
                            addPointsSkill = 0,
                            addPointsNolics = 0,
                            addPointsGold = 0,
                            addMassage = ""
                        )
                    )
                }

            }
        }
    }

    private fun showTimerLife(
        countLife: Int,
        initialCount: Int,
        countGoldLife: Int,
        initialCountGold: Int,
        dateCloseApp: Long?,
        profile: ProfileEntity
    ) {
        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        var count = initialCount
        var countGold = initialCountGold

        var launchCount = sharedPreferences.getInt("launchCount", 0)

        val maxCount = if (launchCount <= 3) 300 else countLife * 100
        val maxCountGold = if (launchCount <= 3) 300 else countGoldLife * 100

        val elapsedTimeSeconds = (unixTimeThis - (dateCloseApp ?: unixTimeThis))
        val elapsedMinutes = elapsedTimeSeconds / 60

        val ratePerMinute = 100

        count += (elapsedMinutes * ratePerMinute).toInt().coerceAtMost(maxCount - count)
        countGold += (elapsedMinutes * ratePerMinute).toInt().coerceAtMost(maxCountGold - countGold)

        if (!timerStarted) {
            timerStarted = true
            timer = Timer()
            timer?.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    runOnUiThread {
                        if (count < maxCount) {
                            count = (count + ratePerMinute).coerceAtMost(maxCount)
                        }
                        if (countGold < maxCountGold) {
                            countGold = (countGold + ratePerMinute).coerceAtMost(maxCountGold)
                        }

                        unixTimeThis = System.currentTimeMillis() / 1000
                        viewModel.updateProfile(profile.copy(count = count, countGold = countGold, dateCloseApp = unixTimeThis.toString()))
                    }
                }
            }, 0, 60000) // Executes every 1 minute
        }
    }

    private fun showAddPoints(profile: ProfileEntity): ProfileEntity? {
        var updatedProfile: ProfileEntity? = null
        profile.apply {
            if (addPointsGold != 0 ||
                addPointsSkill != 0 ||
                addPointsNolics != 0 ||
                addTrophy.isNotEmpty() ||
                addMassage.isNotEmpty()
            ) {

                updatedProfile = profile.copy(
                    pointsGold = pointsGold + addPointsGold,
                    pointsSkill = pointsSkill + addPointsSkill,
                    pointsNolics = pointsNolics + addPointsNolics,
                    trophy = trophy + addTrophy,
                    addPointsGold = 0,
                    addPointsSkill = 0,
                    addPointsNolics = 0,
                    addTrophy = "",
                    addMassage = ""
                )
            }
            //val notification = NotificationHelper(this@MainActivity)
            //notification.updateProfileUserGuide()
            //notification.showBuilderUpdateProfile()
        }

        return updatedProfile
    }

    private fun showBoxDayANDGetNew(timeLastOpenBox: String, countDayBox: Int): Int {
        val diffDays = unixTimeDayThis.toInt() - timeLastOpenBox.toInt()

        val newCountDayBox: Int = when {
            diffDays >= 2 -> 0
            diffDays == 1 -> countDayBox + 1
            diffDays == 0 -> countDayBox
            else -> countDayBox
        }

        val boxDays = listOf(
            binding.boxDay1, binding.boxDay2, binding.boxDay3, binding.boxDay4, binding.boxDay5,
            binding.boxDay6, binding.boxDay7, binding.boxDay8, binding.boxDay9, binding.boxDay10
        )

        boxDays.take(countDayBox).forEach {
            it.setBackgroundColor(ContextCompat.getColor(this, R.color.green))
        }

        return newCountDayBox
    }

    private fun showFabs(buyQuizPlace: Int, countBox: Int) = with(binding) {
        if (countBox > 1) {
            fabBox.visibility = View.VISIBLE
            tvNumberBox.text = countBox.toString()
        }
        if (buyQuizPlace > 1) tvNumberPlaceUserQuiz.text = buyQuizPlace.toString()
    }

    private fun showLife(
        countLife: Int,
        count: Int,
        countGoldLife: Int,
        countGold: Int,
    ) {

        val lifeIndicators = listOf(
            binding.pbLife1, binding.pbLife2, binding.pbLife3, binding.pbLife4, binding.pbLife5
        )

        val filledLives = count / 100
        val partialLifePercentage = count % 100

        for (i in 0 until countLife) {
            val lifeIndicator = lifeIndicators[i]
            val layerDrawable = createLayerDrawable(
                R.drawable.baseline_favorite_24_empty,
                R.drawable.baseline_favorite_24
            )

            when {
                i < filledLives -> layerDrawable.level = 10000
                i == filledLives && partialLifePercentage > 0 ->
                    layerDrawable.level = partialLifePercentage * 100

                else -> layerDrawable.level = 0
            }
            lifeIndicator.setImageDrawable(layerDrawable)
        }

        if (countGoldLife > 0) {
            val layerDrawableGold = createLayerDrawable(
                R.drawable.baseline_favorite_24_empty,
                R.drawable.baseline_favorite_24_gold
            )

            when {
                countGold >= 100 -> layerDrawableGold.level = 10000
                countGold > 0 -> layerDrawableGold.level = countGold * 100
                else -> layerDrawableGold.level = 0
            }
            binding.pbLifeGold1.setImageDrawable(layerDrawableGold)
        }
    }

    private fun showPoints(pointsNolics: Int, pointsGold: Int, pointsSkill: Int) {
        binding.tvNolics.text = pointsNolics.toString()
        binding.tvGold.text = pointsGold.toString()
        binding.tvStars.text = pointsSkill.toString()
    }

    private fun showPremiumOrBan(datePremium: String, dateBanned: String) {
        val lastDayPremium = (datePremium.toLongOrNull() ?: unixTimeDayThis) - unixTimeDayThis
        val lastDayBan = (dateBanned.toLongOrNull()?: unixTimeDayThis) - unixTimeDayThis

        if (lastDayPremium > 0L) {
            binding.imvPremiun.setBackgroundColor(Color.YELLOW)
            binding.tvCountPremiun.text = lastDayPremium.toString()
        } else if (lastDayBan > 0L) {
            binding.imvPremiun.setBackgroundColor(Color.RED)
            binding.tvCountPremiun.text = lastDayBan.toString()
        } else {
            binding.imvPremiun.setBackgroundColor(Color.WHITE)
            binding.tvCountPremiun.text = 0.toString()
        }
    }

    private fun showNick(nickname: String?, trophy: String?) {

        showTextWithDelay(
            binding.tvName,
            "${nickname ?: ""} ${trophy ?: ""}",
            25,
            getColorNickname(0),
            getColorNickname(settingsConfig.nicknameColor)
        )
    }

    private fun setupUI() {
        val versionName = getAppVersionName()
        supportActionBar?.hide()

        binding.imbManu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        val layerDrawable1 = createLayerDrawable(
            R.drawable.baseline_favorite_24_empty,
            R.drawable.baseline_favorite_24
        )
        val layerDrawable2 = createLayerDrawable(
            R.drawable.baseline_favorite_24_empty,
            R.drawable.baseline_favorite_24
        )
        val layerDrawable3 = createLayerDrawable(
            R.drawable.baseline_favorite_24_empty,
            R.drawable.baseline_favorite_24
        )
        val layerDrawable4 = createLayerDrawable(
            R.drawable.baseline_favorite_24_empty,
            R.drawable.baseline_favorite_24
        )
        val layerDrawable5 = createLayerDrawable(
            R.drawable.baseline_favorite_24_empty,
            R.drawable.baseline_favorite_24
        )
        val layerDrawableGold = createLayerDrawable(
            R.drawable.baseline_favorite_24_empty,
            R.drawable.baseline_favorite_24_gold
        )

        binding.pbLife1.setImageDrawable(layerDrawable1)
        binding.pbLife2.setImageDrawable(layerDrawable2)
        binding.pbLife3.setImageDrawable(layerDrawable3)
        binding.pbLife4.setImageDrawable(layerDrawable4)
        binding.pbLife5.setImageDrawable(layerDrawable5)
        binding.pbLifeGold1.setImageDrawable(layerDrawableGold)
    }

    private fun initViewModel() {
        (application as MainApp).applicationComponent.inject(this)
        viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]
    }

    private fun updateUI(profile: ProfileEntity?) {
        updateLifeBars(profile)
        updateLifeVisibility(profile?.countLife ?: 0)
        updateNickName(profile)
        updateCounters(profile)
        setNewSkill(profile?.pointsSkill)
    }

    private fun updateLifeBars(profile: ProfileEntity?) {
        val countLifePoints = (profile?.count ?: 0) * COUNT_LIFE_POINTS_IN_LIFE
        val lifeDrawables = arrayOf(
            binding.pbLife1.drawable,
            binding.pbLife2.drawable,
            binding.pbLife3.drawable,
            binding.pbLife4.drawable,
            binding.pbLife5.drawable
        )
        var count = countLifePoints

        lifeDrawables.forEach { drawable ->
            (drawable as? LayerDrawable)?.findDrawableByLayerId(android.R.id.progress)?.level =
                count
            count -= VALUE_COUNT_LIFE
        }

        (binding.pbLifeGold1.drawable as? LayerDrawable)?.findDrawableByLayerId(android.R.id.progress)?.level =
            (profile?.countGold ?: 0) * COUNT_LIFE_POINTS_IN_LIFE

        binding.pbLifeGold1.visibility =
            if (profile?.countGoldLife == 1) View.VISIBLE else View.GONE
    }

    private fun updateLifeVisibility(countLife: Int) {
        val lifeViews = arrayOf(
            binding.pbLife1,
            binding.pbLife2,
            binding.pbLife3,
            binding.pbLife4,
            binding.pbLife5
        )
        lifeViews.forEachIndexed { index, imageView ->
            imageView.visibility = if (index < countLife) View.VISIBLE else View.GONE
        }
    }

    private fun updateNickName(profile: ProfileEntity?) {
        val nickname = profile?.nickname ?: ""
        val trophy = profile?.trophy ?: ""
        val displayedNickname = try {
            "$nickname $trophy"
        } catch (e: Exception) {
            ""
        }

        if (SharedPreferencesManager.getNick() != nickname) {
            showTextWithDelay(
                binding.tvName,
                displayedNickname,
                DELAY_SHOW_TEXT_IN_MAINACTIVITY_NICK,
                getColorNickname(1),
                getColorNickname(settingsConfig.nicknameColor)
            )
        } else {
            binding.tvName.text = displayedNickname
        }

        try {
            binding.tvName.setShadowLayer(
                10F,
                0F,
                0F,
                getShadowColor(binding.tvName.currentTextColor)
            )
            binding.tvName.setTypeface(null, Typeface.BOLD)
        } catch (e: Exception) {
        }
    }

    private fun getShadowColor(currentColor: Int): Int {
        return when (currentColor) {
            ContextCompat.getColor(this, R.color.default_nick_color6) -> {
                binding.progressBar2.progressDrawable.setColorFilter(
                    Color.WHITE,
                    PorterDuff.Mode.SRC_IN
                )
                Color.WHITE
            }

            ContextCompat.getColor(this, R.color.default_nick_color7) -> {
                binding.progressBar2.progressDrawable.setColorFilter(
                    Color.YELLOW,
                    PorterDuff.Mode.SRC_IN
                )
                Color.YELLOW
            }

            else -> Color.TRANSPARENT
        }
    }

    private fun updateCounters(profile: ProfileEntity?) {
        val animationDuration = 1000L

        animateValue(
            binding.tvNolics,
            SharedPreferencesManager.getNolic(),
            profile?.pointsNolics ?: 0,
            animationDuration,
            500
        )
        animateValue(
            binding.tvGold,
            SharedPreferencesManager.getGold(),
            profile?.pointsGold ?: 0,
            animationDuration,
            500
        )
        animateValueFloat(
            binding.tvStars,
            SharedPreferencesManager.getSkill().toFloat() / 100,
            profile?.pointsSkill?.toFloat()?.div(100) ?: 0f,
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
                profile?.datePremium ?: "",
                TimeManager.getCurrentTime()
            ).toInt(),
            animationDuration,
            500
        )
    }

    private fun setupDrawerLayout() {
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                val slideX = drawerView.width * slideOffset
                binding.cv.translationX = slideX

                // Пример взаимодействия с другими элементами
                binding.progressBar2.alpha =
                    1 - slideOffset // Меняем прозрачность ProgressBar в зависимости от открытия меню
                binding.tvPbLoad.translationX =
                    slideX / 2 // Перемещаем текст в зависимости от открытия меню
            }

            override fun onDrawerOpened(drawerView: View) {}
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.title) {
                getString(R.string.nav_home) -> switchFragment(MainFragment())
                getString(R.string.nav_my_quests) -> switchFragment(QuizFragment())
                getString(R.string.nav_downloads) -> switchFragment(DownloadFragment())
                getString(R.string.nav_settings) -> switchFragment(SettingsFragment())
                getString(R.string.nav_tasks) -> switchFragment(QuizFragment())
                getString(R.string.nav_about) -> switchFragment(AboutFragment())

                getString(R.string.nav_chat) -> switchFragment(ChatFragment())
                getString(R.string.nav_arena) -> switchFragment(QuizFragment())
                getString(R.string.nav_leaders) -> switchFragment(LeadersFragment())
                getString(R.string.nav_tournament) -> switchFragment(QuizFragment())
                getString(R.string.nav_archive) -> switchFragment(QuizFragment())
                getString(R.string.nav_referral_program) -> switchFragment(ReferralProgramFragment())
                getString(R.string.nav_friends) -> switchFragment(FriendsFragment())
                getString(R.string.nav_roles) -> switchFragment(RolesFragment())
                getString(R.string.nav_logout) -> {
                    logout()
                }

                else -> false
            }
            binding.drawerLayout.closeDrawers()
            true
        }
    }

    private fun logout() {

    }

    private fun initBottomMenu() {
        binding.bNav.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_home -> switchFragment(MainFragment())
                R.id.menu_adb -> switchFragment(ShopFragment())
                R.id.menu_info -> startInfoFragment()
                R.id.menu_network -> {
                    startActivity(Intent(this, CreateQuizActivity::class.java))
                    //SetItemMenu.setNetworkMenu(binding, )
                }

                else -> false
            }
            true
        }
    }

    private fun startInfoFragment() {

    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.title_fragment, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun setupAnimations() {
        val viewsToAnimate = arrayOf(
            binding.imvStars,
            binding.imvNolics,
            binding.pbLife1,
            binding.pbLife2,
            binding.pbLife3,
            binding.pbLife4,
            binding.pbLife5,
            binding.pbLifeGold1,
            binding.imvGold,
            binding.imvPremiun
        )

        var initialDelay = 100L
        viewsToAnimate.forEach { view ->
            startAnimationWithRepeat(view, 500, initialDelay, 1000)
            initialDelay += 1000
        }
    }

    private fun createLayerDrawable(emptyRes: Int, filledRes: Int): LayerDrawable {
        val emptyDrawable = ContextCompat.getDrawable(this, emptyRes)
        val filledDrawable = ClipDrawable(
            ContextCompat.getDrawable(this, filledRes),
            Gravity.LEFT,
            ClipDrawable.HORIZONTAL
        )

        return LayerDrawable(arrayOf(emptyDrawable, filledDrawable)).apply {
            setDrawableByLayerId(0, emptyDrawable)
            setDrawableByLayerId(1, filledDrawable)
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
    }

    private fun getAppVersionName(): String {
        val pInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
        return pInfo.versionName
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

    private fun showTextWithDelay(
        textView: TextView,
        text: String,
        delayInMillis: Long,
        firstColorId: Int,
        secondColorId: Int
    ) {
        val existingText = textView.text.toString()
        if (existingText != text) {
            val commonPrefixLength = existingText.commonPrefixWith(text).length

            CoroutineScope(Dispatchers.Main).launch {
                val spannableText = SpannableStringBuilder()
                spannableText.append(text.substring(0, commonPrefixLength))
                for (i in commonPrefixLength until text.length) {
                    val char = text[i]
                    val start = spannableText.length
                    spannableText.append(char.toString())
                    spannableText.setSpan(
                        ForegroundColorSpan(firstColorId),
                        start,
                        start + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    textView.text = spannableText
                    delay(delayInMillis)

                    spannableText.setSpan(
                        ForegroundColorSpan(secondColorId),
                        start,
                        start + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    textView.text = spannableText
                }
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

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        return super.onPrepareOptionsMenu(menu)
    }

    //Todo test
    private fun getLogsContacts() {
        Contacts.initialize(this)
        Contacts.getQuery().find().forEach {
            if (it.givenName?.contains("Oleg") == true) {

            }
        }
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

                //clickNavMenuContact()
            } else {
                Toast.makeText(
                    this,
                    "Contacts permission is required to use this app",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        const val REQUEST_CODE_STORAGE_PERMISSION = 1001
        const val REQUEST_CODE_CONTACTS_PERMISSION = 1002
    }
}

