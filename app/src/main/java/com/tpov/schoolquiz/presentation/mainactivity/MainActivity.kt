package com.tpov.schoolquiz.presentation.mainactivity

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.firebase.auth.FirebaseAuth
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.ActivityMainBinding
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import com.tpov.schoolquiz.presentation.fragment.FragmentManager
import com.tpov.schoolquiz.presentation.mainactivity.info.InfoActivity
import com.tpov.schoolquiz.presentation.network.AutorisationFragment
import com.tpov.schoolquiz.presentation.network.chat.ChatFragment
import com.tpov.schoolquiz.presentation.network.event.EventFragment
import com.tpov.schoolquiz.presentation.network.profile.ProfileFragment
import com.tpov.schoolquiz.presentation.settings.SettingsActivity
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Inject

/**
 * This is the main screen of the application, it consists of a panel that shows how much spare is left.
 * questions of the day and a fragment that displays user and system questions
 */

@InternalCoroutinesApi
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var iAd: InterstitialAd? = null
    private var numQuestionNotDate = 0
    private lateinit var viewModel: MainActivityViewModel
    private var fr1 = 1
    private var fr2 = 1

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private var recreateActivity: Boolean = false

    private val component by lazy {
        (application as MainApp).component
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        component.inject(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        log("onCreate()")
        // Remove the action bar
        supportActionBar?.hide()

        val imageResGold = R.drawable.baseline_favorite_24_gold
        val imageRes = R.drawable.baseline_favorite_24

        val filledDrawable = ContextCompat.getDrawable(this, imageRes)
        val filledDrawableGold = ContextCompat.getDrawable(this, imageResGold)
        val emptyDrawable = ContextCompat.getDrawable(this, R.drawable.baseline_favorite_24_empty)

        val layers = arrayOf(
            emptyDrawable,
            ClipDrawable(filledDrawable, Gravity.LEFT, ClipDrawable.HORIZONTAL)
        )
        val layersGold = arrayOf(
            emptyDrawable,
            ClipDrawable(filledDrawableGold, Gravity.LEFT, ClipDrawable.HORIZONTAL)
        )
        val layerDrawable = LayerDrawable(layers)
        val layerDrawableGold = LayerDrawable(layersGold)

        layerDrawable.setDrawableByLayerId(0, emptyDrawable)
        layerDrawableGold.setDrawableByLayerId(0, emptyDrawable)

        layerDrawable.setDrawableByLayerId(
            1,
            ClipDrawable(filledDrawable, Gravity.LEFT, ClipDrawable.HORIZONTAL)
        )
        layerDrawableGold.setDrawableByLayerId(
            1,
            ClipDrawable(filledDrawableGold, Gravity.LEFT, ClipDrawable.HORIZONTAL)
        )

        layerDrawable.setId(0, android.R.id.background)
        layerDrawableGold.setId(0, android.R.id.background)
        layerDrawable.setId(1, android.R.id.progress)
        layerDrawableGold.setId(1, android.R.id.progress)

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
            viewModel = ViewModelProvider(this, viewModelFactory)[MainActivityViewModel::class.java]
            viewModel.init()
        } else {
            // Разрешения не предоставлены, запросить их
            requestStoragePermission()
        }


        setButtonNavListener()
        numQuestionNotDate = intent.getIntExtra(NUM_QUESTION_NOT_NUL, 0)
        FragmentManager.setFragment(FragmentMain.newInstance(), this)

        loadNumQuestionNotDate()

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
        val imageViewGold = binding.pbLifeGold1
        val imageViewLife1 = binding.pbLife1
        val imageViewLife2 = binding.pbLife2
        val imageViewLife3 = binding.pbLife3
        val imageViewLife4 = binding.pbLife4
        val imageViewLife5 = binding.pbLife5

        imageViewGold.setImageDrawable(layerDrawableGold)
        //imageViewLife1.setImageDrawable(layerDrawable)
        //imageViewLife2.setImageDrawable(layerDrawable)
        //imageViewLife3.setImageDrawable(layerDrawable)
        //imageViewLife4.setImageDrawable(layerDrawable)
        imageViewLife5.setImageDrawable(layerDrawable)

        val level = 5000 //0..10000
        layerDrawable.findDrawableByLayerId(android.R.id.progress).level = 3000
        layerDrawableGold.findDrawableByLayerId(android.R.id.progress).level = 7000
        SetItemMenu.setHomeMenu(binding, fr2)


    }

    private fun listenerDrawer() {

        log("fun listenerDrawer()")

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            // ваш код обработки нажатия на элемент меню

            log("listenerDrawer() menuItem: ${menuItem.toString()}")
            when (menuItem.toString()) {
                resources.getString(R.string.nav_chat) -> {

                    FragmentManager.setFragment(ChatFragment.newInstance(), this)
                    SetItemMenu.setNetworkMenu(binding, 3)
                }
                resources.getString(R.string.nav_downloads) -> {
                    FragmentManager.setFragment(FragmentMain.newInstance(), this)

                    SetItemMenu.setHomeMenu(binding, 4)
                }
                resources.getString(R.string.nav_enter) -> {
                    SetItemMenu.setNetworkMenu(binding, 10)
                }
                resources.getString(R.string.nav_exit) -> {

                    SetItemMenu.setNetworkMenu(binding, 11)
                    FirebaseAuth.getInstance().signOut()
                }
                resources.getString(R.string.nav_global) -> {

                    SetItemMenu.setNetworkMenu(binding, 8)
                }
                resources.getString(R.string.nav_friends) -> {


                    SetItemMenu.setNetworkMenu(binding, 9)
                }
                resources.getString(R.string.nav_home) -> {


                    SetItemMenu.setHomeMenu(binding, 1)
                }
                resources.getString(R.string.nav_leaders) -> {


                    SetItemMenu.setNetworkMenu(binding, 11)
                }
                resources.getString(R.string.nav_massages) -> {


                    SetItemMenu.setNetworkMenu(binding, 5)
                }
                resources.getString(R.string.nav_my_quiz) -> {


                    SetItemMenu.setHomeMenu(binding, 2)
                }
                resources.getString(R.string.nav_news) -> {


                    SetItemMenu.setNetworkMenu(binding, 7)
                }
                resources.getString(R.string.nav_players) -> {


                    SetItemMenu.setNetworkMenu(binding, 6)
                }
                resources.getString(R.string.nav_reports) -> {


                }
                resources.getString(R.string.nav_task) -> {
                    FragmentManager.setFragment(EventFragment.newInstance(), this)
                }
                resources.getString(R.string.nav_settings) -> {}
            }

            Toast.makeText(this, "menuItem $menuItem", Toast.LENGTH_LONG).show()
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
    private fun loadNumQuestionNotDate() = with(binding) {

        if (numQuestionNotDate > 0) textView10.setBackgroundResource(R.color.num_chack_norice_green)
        if (numQuestionNotDate > 1) textView9.setBackgroundResource(R.color.num_chack_norice_green)
        if (numQuestionNotDate > 2) textView8.setBackgroundResource(R.color.num_chack_norice_green)
        if (numQuestionNotDate > 3) textView7.setBackgroundResource(R.color.num_chack_norice_green)
        if (numQuestionNotDate > 4) textView6.setBackgroundResource(R.color.num_chack_norice_green)
        if (numQuestionNotDate > 5) textView5.setBackgroundResource(R.color.num_chack_norice_green)
        if (numQuestionNotDate > 6) textView4.setBackgroundResource(R.color.num_chack_norice_green)
        if (numQuestionNotDate > 7) textView3.setBackgroundResource(R.color.num_chack_norice_green)
        if (numQuestionNotDate > 8) textView2.setBackgroundResource(R.color.num_chack_norice_green)
        if (numQuestionNotDate > 9) textView.setBackgroundResource(R.color.num_chack_norice_green)
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
                    SetItemMenu.setHomeMenu(binding, 1)
                    fr1 = 1
                }
            }

            R.id.menu_new_quiz -> {
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
                    SetItemMenu.setNetworkMenu(binding, 1)
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
                    FragmentManager.setFragment(FragmentMain.newInstance(), this)
                }

                R.id.menu_new_quiz -> {
                    log("setButtonNavListener() menu_new_quiz")
                    FragmentManager.currentFrag?.onClickNew("", 0)
                }

                R.id.menu_settings -> {
                    log("setButtonNavListener() menu_settings")
                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                }

                R.id.menu_info -> {
                    log("setButtonNavListener() menu_info")
                    startActivity(Intent(this@MainActivity, InfoActivity::class.java))
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

        const val NUM_QUESTION_NOT_NUL = "num_question_not_nul"
        const val SHOP_LIST = "shop_list"
        const val REQUEST_CODE_STORAGE_PERMISSION = 1001

    }


}

