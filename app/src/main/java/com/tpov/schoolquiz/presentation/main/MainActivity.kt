package com.tpov.schoolquiz.presentation.main

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.usecase.SettingConfigObject.settingsConfig
import com.tpov.common.presentation.NavigationProvider
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.question.QuestionActivity
import com.tpov.common.presentation.utils.TextAnimator
import com.tpov.common.presentation.utils.Values
import com.tpov.network.presentation.chat.ChatFragment
import com.tpov.network.presentation.friend.FriendsFragment
import com.tpov.network.presentation.leaders.LeadersFragment
import com.tpov.network.presentation.profile.ContactFragment
import com.tpov.network.presentation.profile.ProfileFragment
import com.tpov.schoolquiz.MainApp
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.model.Qualification
import com.tpov.schoolquiz.databinding.ActivityMainBinding
import com.tpov.schoolquiz.presentation.core.NotificationHelper
import com.tpov.schoolquiz.presentation.create.CreateQuizActivity
import com.tpov.schoolquiz.presentation.create.CreateQuizViewModel
import com.tpov.schoolquiz.presentation.dowload.DownloadFragment
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_CHAT
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_CONTACT
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_DOWNLOADS
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_EXIT
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_FRIEND
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_HOME_QUIZ
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_LEADER
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_MY_QUIZ
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_PROFILE
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_SETTING
import com.tpov.schoolquiz.presentation.main.SetItemMenu.currentMenuId
import com.tpov.schoolquiz.presentation.main.SetItemMenu.setupDynamicMenu
import com.tpov.schoolquiz.presentation.model.Inset
import com.tpov.schoolquiz.presentation.setting.SettingsFragment
import com.tpov.shop.presentation.ShopFragment
import com.tpov.userguide.presentation.UserGuide
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * This is the main screen of the application, it consists of a panel that shows how much spare is left.
 * questions of the day and a fragment that displays user and system questions
 */

@InternalCoroutinesApi
class MainActivity : AppCompatActivity(), NavigationProvider {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var binding: ActivityMainBinding
    lateinit var viewModel: MainViewModel
    private val listLives by lazy {
        listOf(binding.pbLife1, binding.pbLife2, binding.pbLife3, binding.pbLife4, binding.pbLife5)
    }
 private val listGoldLives by lazy {
        listOf(binding.pbLifeGold1)
    }

    private val boxDays by lazy{ listOf(
    binding.boxDay1, binding.boxDay2, binding.boxDay3, binding.boxDay4, binding.boxDay5,
    binding.boxDay6, binding.boxDay7, binding.boxDay8, binding.boxDay9, binding.boxDay10
    )}

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Values.init(this, application)

        initViewModel()
        observeData()
        setupDrawerLayout()
        initBottomMenu()
        setupMenu(MENU_HOME_QUIZ)
        setupAnimations()
        val view = findViewById<View?>(R.id.menu_network)
        initUserguide(view)
        initSetOnClickListeners()
    }

    private fun initSetOnClickListeners() {
        binding.fabAddItem.setOnClickListener {
            val intent = CreateQuizActivity.newIntent(
                context = this,
                regime = CreateQuizViewModel.REGIME_CREATE_QUIZ
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        }
    }

    private fun initViewModel() {
        (application as MainApp).applicationComponent.inject(this)
        viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]
        viewModel.initProfile()
    }

    private fun observeData() {
        observeLife()
        observeAddPoints()
        observePremium()
        observeNickname()
        observeDayInGameAndBox()
        observeTaskStatus()
    }

    private fun observeLife() = lifecycleScope.launch {
        viewModel.livesState.collect { state ->
            listLives.forEachIndexed { index, imageView ->
                if (index < state.standardHearts) {
                    imageView.visibility = View.VISIBLE
                    imageView.setImageDrawable(
                        viewModel.createHeartDrawable(
                            lifePoints = state.standardLife,
                            heartIndex = index,
                            isGold = false
                        )
                    )
                } else imageView.visibility = View.GONE
            }

            listGoldLives[0].apply {
                visibility = if (state.goldHearts > 0) View.VISIBLE else View.GONE
                if (state.goldHearts > 0) {
                    setImageDrawable(
                        viewModel.createHeartDrawable(
                            lifePoints = state.goldLife,
                            heartIndex = 0,
                            isGold = true
                        )
                    )
                }
            }

            viewModel.updateProfile(
                goldHearts = state.goldHearts,
                countGoldLife = state.goldHearts,
                goldLife = state.goldLife,
                updateTime = state.updateTime,
                standardLife = state.standardLife,
                standardHearts = state.standardHearts
            )
        }
    }

    private fun observeAddPoints() = lifecycleScope.launch {
        viewModel.addPointsState.collect { state ->
            if (state.addGold > 0L) {
                showUserGuide("Вам начислили: ${state.addGold} золота")
                viewModel.updateProfile(
                    gold = viewModel.profileState.value?.pointsGold?.toLong()?.plus(state.addGold),
                    addGold = 0
                )
            }
            if (state.addSkill > 0L) {
                showUserGuide("Вам начислили: ${state.addSkill} опыта")
                viewModel.updateProfile(
                    skill = viewModel.profileState.value?.pointsSkill?.toLong()?.plus(state.addSkill), addSkill = 0
                )
            }
            if (state.addNolics > 0L) {
                showUserGuide("Вам начислили: ${state.addNolics} ноликов")
                viewModel.updateProfile(
                    nolics = viewModel.profileState.value?.pointsNolics?.toLong()?.plus(state.addNolics), addNolics = 0
                )
            }
            if (state.addTrophy.isNotEmpty()) {
                showUserGuide("Вам начислили трофеи: ${state.addTrophy}")
                viewModel.updateProfile(trophy = viewModel.profileState.value?.trophy + state.addTrophy, addTrophy = "")
            }
            if (state.addMassage.isNotEmpty()) {
                showUserGuide("Вам пришло сообщение от разработчика: ${state.addMassage}")
            }
        }
    }

    private fun showUserGuide(text: String) {
        UserGuide(this).guideBuilder()
            .setText(text)
            .build()
    }

    private fun observePremium() = lifecycleScope.launch {
        viewModel.premiumState.collect {
            binding.tvCountPremiun.text = it
        }
    }
    private fun observeNickname() = lifecycleScope.launch {
        viewModel.nicknameState.collect {
            binding.tvName.text = it
        }
    }

    private fun observeDayInGameAndBox() = lifecycleScope.launch {
        viewModel.daysInGameState.collect {
            boxDays.take(it.countDayBox.toInt()).forEach {
                it.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.green))
            }

            binding.tvNumberBox.text = it.countBox.toString()
            binding.fabBox.visibility = if (it.countBox > 0) View.VISIBLE else View.GONE
        }
    }

    private fun observeTaskStatus() = lifecycleScope.launch {
        viewModel.taskState.collect { state ->
            binding.tvPbLoad.text = state.currentTaskName.ifEmpty { getString(R.string.loading_completed) }
            binding.progressBar2.progress = (state.progressPercentage * 100).toInt()

            val visibility = if (state.isRunning && state.tasks.isNotEmpty()) View.VISIBLE else View.GONE
            binding.tvPbLoad.visibility = visibility
            binding.progressBar2.visibility = visibility
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopLifesUpdate()
    }

    fun initUserguide(view: View) {
        val notification = NotificationHelper(this)
        notification.setupUserGuide(view)
    }

    private fun setupDrawerLayout() {
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                val slideX = drawerView.width * slideOffset
                binding.cv.translationX = slideX
                binding.progressBar2.alpha = 1 - slideOffset
                binding.tvPbLoad.translationX = slideX / 2
            }

            override fun onDrawerOpened(drawerView: View) {}
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            val handled = when (menuItem.itemId) {
                MENU_HOME_QUIZ -> { navigateTo(MainFragment.newInstance(EventQuiz.QUIZ_HOME)); true }
                MENU_MY_QUIZ -> { navigateTo(MainFragment.newInstance(EventQuiz.QUIZ_BY_USER)); true }
                MENU_DOWNLOADS -> { navigateTo(DownloadFragment()); true }
                MENU_SETTING -> { navigateTo(SettingsFragment()); true }
                MENU_PROFILE -> { navigateTo(ProfileFragment()); true }
                MENU_CHAT -> { navigateTo(ChatFragment()); true }
                MENU_LEADER -> { navigateTo(LeadersFragment()); true }
                MENU_FRIEND -> { navigateTo(FriendsFragment()); true }
                MENU_CONTACT -> { navigateTo(ContactFragment()); true }
                MENU_EXIT -> { logout(); true }
                else -> false
            }

            if (handled) {
                setupMenu(menuItem.itemId)
            }
            binding.drawerLayout.closeDrawers()
            true
        }

    }

    private fun setupMenu(itemId: Int) {
        currentMenuId = itemId
        setupDynamicMenu(
            binding, Qualification(200, 200, 200, 200, 200, 300),
            currentSkill = 500000,
            inset = Inset.HOME
        )
    }

    private fun logout() {

    }

    private fun initBottomMenu() {
        navigateTo(MainFragment.newInstance(EventQuiz.QUIZ_HOME)) // Инициализация начального фрагмента
        binding.bNav.setOnNavigationItemSelectedListener { menuItem ->
            val handled = when (menuItem.itemId) {
                R.id.menu_home -> {
                    setupMenu(MENU_HOME_QUIZ)
                    navigateTo(MainFragment.newInstance(EventQuiz.QUIZ_HOME))
                    true
                }
                R.id.menu_adb -> { navigateTo(ShopFragment()); true }
                R.id.menu_info -> { startInfoFragment(); true }
                R.id.menu_network -> { /* TODO: Handle network click or remove if not used */ true }
                else -> false
            }
            handled
        }
    }

    private fun startInfoFragment() {
        // TODO: Implement or remove if not used. Example: navigateTo(InfoFragment())
    }

    // Метод switchFragment больше не нужен, так как его заменил navigateTo
    // private fun switchFragment(fragment: Fragment) {
    // supportFragmentManager.beginTransaction()
    // .replace(R.id.title_fragment, fragment)
    // .addToBackStack(null)
    // .commit()
    // }

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
            TextAnimator().startAnimationWithRepeat(view, 500, initialDelay, 10000)
            initialDelay += 1000
        }
    }


    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        return super.onPrepareOptionsMenu(menu)
    }

    // Метод replaceFragment больше не нужен, так как его функциональность покрывается navigateTo
    // fun replaceFragment(fragment: Fragment) {
    //     val fragmentManager = supportFragmentManager
    //     val transaction = fragmentManager.beginTransaction()
    //     transaction.replace(R.id.title_fragment, fragment)
    //     transaction.addToBackStack(null)
    //     transaction.commit()
    // }

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

    override fun openQuestionActivity(pathStructure: PathStructure, hardQuestion: Boolean) {
        val intent = QuestionActivity.newIntent(
            context = this,
            hardQuestion = hardQuestion,
            pathStructure = pathStructure,
            life = if (hardQuestion) settingsConfig.goldLife else settingsConfig.life
        )
        startActivity(intent)
    }

    override fun navigateTo(fragment: Fragment, addToBackStack: Boolean, replace: Boolean) {
        val transaction = supportFragmentManager.beginTransaction()
        if (replace) {
            transaction.replace(R.id.title_fragment, fragment)
        } else {
            transaction.add(R.id.title_fragment, fragment)
        }

        if (addToBackStack) {
            transaction.addToBackStack(null)
        }
        transaction.commit()
    }
}

