package com.tpov.schoolquiz.presentation.main

import com.tpov.schoolquiz.R
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
import com.tpov.schoolquiz.presentation.model.Inset
import com.tpov.schoolquiz.presentation.model.MenuItemRequirement
import com.tpov.schoolquiz.presentation.model.Role

object MenuList {
    val allMenuItems = listOf(
        MenuItemRequirement(
            id = MENU_HOME,
            titleRes = R.string.menu_home_text,
            iconRes = R.drawable.ic_home,
            inset = Inset.HOME,
            requiredRoles = mapOf(Role.TESTER to 1, Role.ADMIN to 1),
            requiredSkill = 0
        ),
        MenuItemRequirement(
            id = MENU_MY_QUIZ,
            titleRes = R.string.menu_home_text,
            iconRes = R.drawable.ic_home,
            inset = Inset.HOME,
            requiredRoles = mapOf(Role.TESTER to 2, Role.DEVELOPER to 1),
            requiredSkill = 5
        ),
        MenuItemRequirement(
            id = MENU_SETTING,
            titleRes = R.string.menu_settings,
            iconRes = R.drawable.ic_settings,
            inset = Inset.HOME,
            requiredRoles = mapOf(Role.ADMIN to 2),
            requiredSkill = 3
        ),
        MenuItemRequirement(
            id = MENU_DOWNLOADS,
            titleRes = R.string.menu_downloads_text,
            iconRes = R.drawable.ic_upload,
            inset = Inset.HOME,
            requiredRoles = mapOf(Role.SPONSOR to 1, Role.TESTER to 1),
            requiredSkill = 0
        ),

        // Profile Menu
        MenuItemRequirement(
            id = MENU_PROFILE,
            titleRes = R.string.menu_profile,
            iconRes = R.drawable.ic_profile,
            inset = Inset.EVENT,
            requiredRoles = mapOf(Role.TRANSLATOR to 1, Role.TESTER to 1),
            requiredSkill = 0
        ),
        MenuItemRequirement(
            id = MENU_MASSAGE,
            titleRes = R.string.manu_message,
            iconRes = R.drawable.ic_light_hard,
            inset = Inset.EVENT,
            requiredRoles = mapOf(Role.MODERATOR to 1, Role.ADMIN to 1),
            requiredSkill = 2
        ),
        MenuItemRequirement(
            id = MENU_CHAT,
            titleRes = R.string.manu_message,
            iconRes = R.drawable.ic_light_hard,
            inset = Inset.EVENT,
            requiredRoles = mapOf(Role.MODERATOR to 2),
            requiredSkill = 3
        ),
        MenuItemRequirement(
            id = MENU_EVENT,
            titleRes = R.string.menu_event,
            iconRes = R.drawable.ic_advanced,
            inset = Inset.EVENT,
            requiredRoles = mapOf(Role.DEVELOPER to 1, Role.SPONSOR to 2),
            requiredSkill = 4
        ),

        // Network Menu
        MenuItemRequirement(
            id = MENU_LEADER,
            titleRes = R.string.menu_leader_text,
            iconRes = R.drawable.ic_advanced,
            inset = Inset.NETWORK,
            requiredRoles = mapOf(Role.ADMIN to 1, Role.DEVELOPER to 2),
            requiredSkill = 5
        ),
        MenuItemRequirement(
            id = MENU_USERS,
            titleRes = R.string.menu_users_text,
            iconRes = R.drawable.ic_advanced,
            inset = Inset.NETWORK,
            requiredRoles = mapOf(Role.MODERATOR to 1),
            requiredSkill = 1
        ),
        MenuItemRequirement(
            id = MENU_NEWS,
            titleRes = R.string.menu_news_text,
            iconRes = R.drawable.ic_advanced,
            inset = Inset.NETWORK,
            requiredRoles = mapOf(Role.TRANSLATOR to 1),
            requiredSkill = 0
        ),
        MenuItemRequirement(
            id = MENU_ARENA,
            titleRes = R.string.menu_arena_text,
            iconRes = R.drawable.ic_advanced,
            inset = Inset.NETWORK,
            requiredRoles = mapOf(Role.TESTER to 1, Role.SPONSOR to 1),
            requiredSkill = 3
        ),
        MenuItemRequirement(
            id = MENU_FRIEND,
            titleRes = R.string.menu_friend_text,
            iconRes = R.drawable.ic_advanced,
            inset = Inset.NETWORK,
            requiredRoles = mapOf(Role.TESTER to 2),
            requiredSkill = 4
        ),
        MenuItemRequirement(
            id = MENU_REPORT,
            titleRes = R.string.menu_report_text,
            iconRes = R.drawable.ic_advanced,
            inset = Inset.NETWORK,
            requiredRoles = mapOf(Role.MODERATOR to 2),
            requiredSkill = 5
        ),
        MenuItemRequirement(
            id = MENU_CONTACT,
            titleRes = R.string.menu_contact_text,
            iconRes = R.drawable.ic_contact,
            inset = Inset.NETWORK,
            requiredRoles = mapOf(Role.SPONSOR to 1, Role.DEVELOPER to 1),
            requiredSkill = 3
        ),
        MenuItemRequirement(
            id = MENU_EXIT,
            titleRes = R.string.menu_exit_text,
            iconRes = R.drawable.ic_advanced,
            inset = Inset.HOME,
            requiredRoles = emptyMap(),
            requiredSkill = 0
        )
    )

}