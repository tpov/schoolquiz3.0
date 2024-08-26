package com.tpov.schoolquiz.presentation.main

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
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
import com.tpov.common.presentation.quiz.QuizFragment
import com.tpov.network.presentation.AutorisationFragment
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
import com.tpov.schoolquiz.presentation.dowload.DownloadFragment
import com.tpov.schoolquiz.presentation.setting.SettingsFragment
import com.tpov.shop.presentation.ShopFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
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

    override fun onDestroy() {
        super.onDestroy()

        timer?.cancel()
        timer = null
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        initViewModel()
        setupDrawerLayout()
        initBottomMenu()
        setupAnimations()
        createTimer()
        updateProfile()
        initData()
    }

    private fun initData() {
        lifecycleScope.launch {
            val listNewQuiz = viewModel.loadHomeCategory()
            if (!isNetworkAvailable(this@MainActivity)) {
                Toast.makeText(this@MainActivity, "Нет подключения к интернету. Попробуйте позже.", Toast.LENGTH_LONG).show()
            }
            if (viewModel.firstStartApp) {
                viewModel.loadQuizByStructure(listNewQuiz)
                viewModel.createProfile()
            }
        }
    }
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork?.isConnectedOrConnecting == true
    }
    private fun updateProfile() {
        lifecycleScope.launch {
            viewModel.profileState.collect { profile ->
                if (profile != null) {
                    with(profile) {
                        nickname
                        datePremium
                        dateBanned
                        trophy
                        pointsGold
                        pointsSkill
                        pointsNolics
                        buyQuizPlace
                        buyTheme

                        countBox
                        timeLastOpenBox
                        coundDayBox
                        countLife
                        count
                        countGoldLife
                        countGold
                        if (addPointsGold != 0 ||
                            addPointsSkill != 0 ||
                            addPointsNolics != 0 ||
                            addTrophy.isNotEmpty() ||
                            addMassage.isNotEmpty()
                        ) {

                            val updatedProfile = profile.copy(
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

                            viewModel.updateProfile(updatedProfile)
                        }
                    }
                } else Toast.makeText(applicationContext, "profile not created", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupUI() {
        val versionName = getAppVersionName()
        supportActionBar?.hide()

        binding.imbManu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        val layerDrawable1 = createLayerDrawable(R.drawable.baseline_favorite_24_empty, R.drawable.baseline_favorite_24)
        val layerDrawable2 = createLayerDrawable(R.drawable.baseline_favorite_24_empty, R.drawable.baseline_favorite_24)
        val layerDrawable3 = createLayerDrawable(R.drawable.baseline_favorite_24_empty, R.drawable.baseline_favorite_24)
        val layerDrawable4 = createLayerDrawable(R.drawable.baseline_favorite_24_empty, R.drawable.baseline_favorite_24)
        val layerDrawable5 = createLayerDrawable(R.drawable.baseline_favorite_24_empty, R.drawable.baseline_favorite_24)
        val layerDrawableGold = createLayerDrawable(R.drawable.baseline_favorite_24_empty, R.drawable.baseline_favorite_24_gold)

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
            (drawable as? LayerDrawable)?.findDrawableByLayerId(android.R.id.progress)?.level = count
            count -= VALUE_COUNT_LIFE
        }

        (binding.pbLifeGold1.drawable as? LayerDrawable)?.findDrawableByLayerId(android.R.id.progress)?.level =
            (profile?.countGold ?: 0) * COUNT_LIFE_POINTS_IN_LIFE

        binding.pbLifeGold1.visibility = if (profile?.countGoldLife == 1) View.VISIBLE else View.GONE
    }

    private fun updateLifeVisibility(countLife: Int) {
        val lifeViews = arrayOf(binding.pbLife1, binding.pbLife2, binding.pbLife3, binding.pbLife4, binding.pbLife5)
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
            showTextWithDelay(binding.tvName, displayedNickname, DELAY_SHOW_TEXT_IN_MAINACTIVITY_NICK)
        } else {
            binding.tvName.text = displayedNickname
        }

        try {
            binding.tvName.setShadowLayer(10F, 0F, 0F, getShadowColor(binding.tvName.currentTextColor))
            binding.tvName.setTypeface(null, Typeface.BOLD)
        } catch (e: Exception) {
        }
    }

    private fun getShadowColor(currentColor: Int): Int {
        return when (currentColor) {
            ContextCompat.getColor(this, R.color.default_nick_color6) -> {
                binding.progressBar2.progressDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                Color.WHITE
            }
            ContextCompat.getColor(this, R.color.default_nick_color7) -> {
                binding.progressBar2.progressDrawable.setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN)
                Color.YELLOW
            }
            else -> Color.TRANSPARENT
        }
    }

    private fun updateCounters(profile: ProfileEntity?) {
        val animationDuration = 1000L

        animateValue(binding.tvNolics, SharedPreferencesManager.getNolic(), profile?.pointsNolics ?: 0, animationDuration, 500)
        animateValue(binding.tvGold, SharedPreferencesManager.getGold(), profile?.pointsGold ?: 0, animationDuration, 500)
        animateValueFloat(
            binding.tvStars,
            SharedPreferencesManager.getSkill().toFloat() / 100,
            profile?.pointsSkill?.toFloat()?.div(100) ?: 0f,
            animationDuration,
            500
        )
        animateValue(
            binding.tvCountPremiun,
            TimeManager.getDaysBetweenDates(SharedPreferencesManager.getPremium(), TimeManager.getCurrentTime()).toInt() ?: 0,
            TimeManager.getDaysBetweenDates(profile?.datePremium ?: "", TimeManager.getCurrentTime()).toInt(),
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
                binding.progressBar2.alpha = 1 - slideOffset // Меняем прозрачность ProgressBar в зависимости от открытия меню
                binding.tvPbLoad.translationX = slideX / 2 // Перемещаем текст в зависимости от открытия меню
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
                    switchFragment(AutorisationFragment())
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
            binding.imvStars, binding.imvNolics, binding.pbLife1, binding.pbLife2,
            binding.pbLife3, binding.pbLife4, binding.pbLife5, binding.pbLifeGold1, binding.imvGold, binding.imvPremiun
        )

        var initialDelay = 100L
        viewsToAnimate.forEach { view ->
            startAnimationWithRepeat(view, 500, initialDelay, 1000)
            initialDelay += 1000
        }
    }

    private fun createLayerDrawable(emptyRes: Int, filledRes: Int): LayerDrawable {
        val emptyDrawable = ContextCompat.getDrawable(this, emptyRes)
        val filledDrawable = ClipDrawable(ContextCompat.getDrawable(this, filledRes), Gravity.LEFT, ClipDrawable.HORIZONTAL)

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

    private var timer: Timer? = null

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createTimer() {
        timer = Timer()
        val delay = 0L // Delay before the timer starts executing the task (in milliseconds)
        val task = object : TimerTask() {
            override fun run() {
            }
        }

        timer?.scheduleAtFixedRate(task, delay, 1000)
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

    private fun loadNumBoxDay() = with(binding) {
        val views = arrayOf(textView10, textView9, textView8, textView7, textView6, textView5, textView4, textView3, textView2, textView)
        views.forEachIndexed { index, view ->
            if (10 > index) view.setBackgroundResource(R.color.green)
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

