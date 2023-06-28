package com.tpov.schoolquiz.presentation.main

import android.content.Context
import android.view.Menu
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.ActivityMainBinding
import com.tpov.schoolquiz.presentation.custom.Logcat
import kotlinx.coroutines.InternalCoroutinesApi

object SetItemMenu {

    const val MENU_HOME = 0
    const val MENU_MY_QUIZ = 1
    const val MENU_SETTING = 2
    const val MENU_DOWNLOADS = 3

    const val MENU_PROFILE = 0

    fun setHomeMenu(binding: ActivityMainBinding, fr2: Int, context: Context) {
        log("fioesjoifjsei, $fr2")
        val menu = binding.navigationView.menu
        menu.clear() // Очистите текущее меню

        var menuItemsToAdd = mutableListOf(
            R.string.nav_home to R.drawable.ic_home,
            R.string.nav_my_quiz to R.drawable.nav_my_quiz,
            R.string.nav_settings to R.drawable.ic_settings,
            R.string.nav_downloads to R.drawable.baseline_download_24
        )
        menuItemsToAdd.removeAt(fr2)

        for (item in menuItemsToAdd) {
            val menuItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, item.first)
            menuItem.icon = ContextCompat.getDrawable(context, item.second)
        }

    }

    fun setNetworkMenu(binding: ActivityMainBinding, fr2: Int, context: Context) {
        log("fun setNetworkMenu()")
        val menu = binding.navigationView.menu
        menu.clear() // Очистите текущее меню

        var menuItemsToAdd: MutableList<Pair<Int, Int>> = if (FirebaseAuth.getInstance().currentUser?.uid == null) emptyList<Pair<Int, Int>>().toMutableList()
        else mutableListOf(
            R.string.nav_profile to R.drawable.nav_profile,
            R.string.nav_massages to R.drawable.nav_massage,
            R.string.nav_chat to R.drawable.nav_chat,
            R.string.nav_task to R.drawable.nav_task,
            R.string.nav_leaders to R.drawable.nav_leader,
            R.string.nav_players to R.drawable.nav_user,
            R.string.nav_news to R.drawable.ic_new,
            R.string.nav_global to R.drawable.baseline_public_24,
            R.string.nav_friends to R.drawable.ic_baseline_drive_folder_upload_24,
            R.string.nav_enter to R.drawable.nav_add_acc,
            R.string.nav_reports to R.drawable.nav_report,
            R.string.nav_exit to R.drawable.nav_exit,
        )

        menuItemsToAdd.removeAt(fr2)

        for (item in menuItemsToAdd) {
            val menuItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, item.first)
            menuItem.icon = ContextCompat.getDrawable(context, item.second)
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    fun log(massage: String) { Logcat.log(massage, "SetItemMenu", Logcat.LOG_ACTIVITY) }
}
