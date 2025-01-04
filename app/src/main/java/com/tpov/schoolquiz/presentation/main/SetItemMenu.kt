package com.tpov.schoolquiz.presentation.main

import android.view.Menu
import com.tpov.schoolquiz.data.model.Qualification
import com.tpov.schoolquiz.databinding.ActivityMainBinding
import com.tpov.schoolquiz.presentation.model.Inset
import com.tpov.schoolquiz.presentation.model.Role

object SetItemMenu {

    const val MENU_HOME_QUIZ = 0
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
    const val MENU_CONTACT = 9
    const val MENU_EXIT = 10
    const val MENU_CHAT_TOURNAMENT = 11
    const val MENU_CHAT_BANNED = 12

    var currentMenuId = 0

    fun setupDynamicMenu(
        binding: ActivityMainBinding,
        qualification: Qualification,
        currentSkill: Int,
        inset: Inset
    ): Boolean {
        val menu = binding.navigationView.menu
        menu.clear()

        val visibleItems = MenuList.allMenuItems.filter { menuItem ->
            menuItem.inset == inset &&
                    menuItem.id != currentMenuId &&
                    menuItem.requiredRoles.all { (role, level) ->
                        getRoleLevel(role, qualification, currentSkill) >= level
                    }
        }

        binding.navigationView.post {
            menu.clear()
            visibleItems.forEach { menuItem ->
                val item = menu.add(Menu.NONE, menuItem.id, Menu.NONE, menuItem.titleRes)
                item.setIcon(menuItem.iconRes)
            }
        }

        return true
    }

    private fun getRoleLevel(role: Role, qualification: Qualification, currentSkill: Int): Int {
        return when (role) {
            Role.USER -> currentSkill
            Role.TESTER -> qualification.tester
            Role.MODERATOR -> qualification.moderator
            Role.SPONSOR -> qualification.sponsor
            Role.TRANSLATOR -> qualification.translator
            Role.ADMIN -> qualification.admin
            Role.DEVELOPER -> qualification.developer
        }
    }

    fun selectivelyRemoveItemsFromMenu(menu: Menu, keepItems: Set<Int>) {
        for (i in menu.size() - 1 downTo 0) {
            val item = menu.getItem(i)
            if (item.itemId !in keepItems) {
                menu.removeItem(item.itemId)
            }
        }
    }

    fun removeUnwantedMenuItems(binding: ActivityMainBinding) {
        val menu = binding.navigationView.menu

        val keepItems = setOf(SetItemMenu.MENU_HOME_QUIZ, SetItemMenu.MENU_SETTING)

        selectivelyRemoveItemsFromMenu(menu, keepItems)

        binding.navigationView.invalidate()
        binding.navigationView.requestLayout()
    }

    private fun Role.getLevel(qualification: Qualification): Int {
        return when (this) {
            Role.TESTER -> qualification.tester
            Role.MODERATOR -> qualification.moderator
            Role.SPONSOR -> qualification.sponsor
            Role.TRANSLATOR -> qualification.translator
            Role.ADMIN -> qualification.admin
            Role.DEVELOPER -> qualification.developer
            Role.USER -> TODO()
        }
    }
}
