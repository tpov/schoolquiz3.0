package com.tpov.schoolquiz.presentation.main

import android.content.Context
import android.view.Menu
import android.view.View
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.tpov.schoolquiz.data.model.Qualification
import com.tpov.schoolquiz.databinding.ActivityMainBinding
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.custom.VisibleItems.getShowItemsMenuHome
import com.tpov.schoolquiz.presentation.custom.VisibleItems.getShowItemsMenuNetwork
import kotlinx.android.synthetic.main.info_fragment.view.*
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

    fun setHomeMenu(
        binding: ActivityMainBinding,
        fr2: Int,
        context: Context,
        skill: Int,
        qualification: Qualification
    ) {
        log("fioesjoifjsei, $fr2")
        val menu = binding.navigationView.menu
        menu.clear() // Очистите текущее меню

        var menuItemsToAdd = getShowItemsMenuHome(qualification).toMutableList()
            //menuItemsToAdd.removeAt(fr2)

        for (item in menuItemsToAdd) {
            val menuItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, item.first)
            menuItem.icon = ContextCompat.getDrawable(context, item.second)
        }

        if (menuItemsToAdd.isEmpty()) {
            binding.imbManu.visibility = View.GONE
            binding.navigationView.visibility = View.GONE
        } else {
            binding.navigationView.visibility = View.VISIBLE
            binding.imbManu.visibility = View.VISIBLE
        }

//        binding.navigationView.post {
//            val recyclerView = binding.navigationView.getChildAt(0) as RecyclerView
//            val position = 2
//            val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)?.itemView
//            //Userguide(context).addGuide(viewHolder!!, "EAH!", options = Options(countRepeat = 100))
//        }
    }

    fun setNetworkMenu(
        binding: ActivityMainBinding,
        fr2: Int,
        context: Context,
        skill: Int,
        qualification: Qualification
    ) {
        log("fun setNetworkMenu() remove menu: $fr2")
        val menu = binding.navigationView.menu
        menu.clear() // Очистите текущее меню

        var menuItemsToAdd: MutableList<Pair<Int, Int>> =
            if (FirebaseAuth.getInstance().currentUser?.uid == null) emptyList<Pair<Int, Int>>().toMutableList()
            else getShowItemsMenuNetwork(skill, qualification).toMutableList()

        if (FirebaseAuth.getInstance().currentUser?.uid != null) //menuItemsToAdd.removeAt(fr2)

        for (item in menuItemsToAdd) {
            val menuItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, item.first)
            menuItem.icon = ContextCompat.getDrawable(context, item.second)
        }

        if (menuItemsToAdd.isEmpty()) {
            binding.imbManu.visibility = View.GONE
            binding.navigationView.visibility = View.GONE
        } else {
            binding.navigationView.visibility = View.VISIBLE
            binding.imbManu.visibility = View.VISIBLE
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    fun log(massage: String) {
        Logcat.log(massage, "SetItemMenu", Logcat.LOG_ACTIVITY)
    }
}
