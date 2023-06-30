package com.tpov.schoolquiz.presentation.main

import android.content.Context
import android.view.Menu
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.model.Qualification
import com.tpov.schoolquiz.databinding.ActivityMainBinding
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.custom.VisibleItems.getShowItemsMenu
import kotlinx.coroutines.InternalCoroutinesApi

object SetItemMenu {

    const val MENU_HOME = 0
    const val MENU_MY_QUIZ = 1
    const val MENU_SETTING = 2
    const val MENU_DOWNLOADS = 3

    const val MENU_PROFILE = 0
    const val MENU_MASSAGE = 1
    const val MENU_CHAT = 2
    const val MENU_EVENT = 3
    const val MENU_LEADER = 4
    const val MENU_USERS = 5
    const val MENU_NEWS = 6
    const val MENU_ARENA = 7
    const val MENU_FRIEND = 8
    const val MENU_REPORT = 9
    const val MENU_EXIT = 10

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

    fun setNetworkMenu(binding: ActivityMainBinding, fr2: Int, context: Context, skill: Int, qualification: Qualification) {
        log("fun setNetworkMenu()")
        val menu = binding.navigationView.menu
        menu.clear() // Очистите текущее меню

        var menuItemsToAdd: MutableList<Pair<Int, Int>> =
            if (FirebaseAuth.getInstance().currentUser?.uid == null) emptyList<Pair<Int, Int>>().toMutableList()

            else {
                getShowItemsMenu(skill, qualification).toMutableList()
            }

        if (FirebaseAuth.getInstance().currentUser?.uid != null) menuItemsToAdd.removeAt(fr2)

        for (item in menuItemsToAdd) {
            val menuItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, item.first)
            menuItem.icon = ContextCompat.getDrawable(context, item.second)
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    fun log(massage: String) {
        Logcat.log(massage, "SetItemMenu", Logcat.LOG_ACTIVITY)
    }
}
