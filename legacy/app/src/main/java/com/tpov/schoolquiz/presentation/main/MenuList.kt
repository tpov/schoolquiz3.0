package com.tpov.schoolquiz.presentation.main

import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.presentation.core.Title
import com.tpov.schoolquiz.presentation.core.TitleUserValue.skillTitleCount
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_ARENA
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_CHAT
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_CHAT_BANNED
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_CHAT_TOURNAMENT
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_CONTACT
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_DOWNLOADS
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_EVENT
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_EXIT
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_FRIEND
import com.tpov.schoolquiz.presentation.main.SetItemMenu.MENU_HOME_QUIZ
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
            id = MENU_HOME_QUIZ,
            titleRes = R.string.menu_home_quiz,
            iconRes = R.drawable.ic_home,
            inset = Inset.HOME,
            requiredRoles = mapOf()
        ),
        MenuItemRequirement(
            id = MENU_MY_QUIZ,
            titleRes = R.string.menu_user_quiz,
            iconRes = R.drawable.ic_home,
            inset = Inset.HOME,
            requiredRoles = mapOf(Role.USER to skillTitleCount[Title.RECRUIT]?.first()!!)
        ),
        MenuItemRequirement(
            id = MENU_SETTING,
            titleRes = R.string.menu_settings,
            iconRes = R.drawable.ic_settings,
            inset = Inset.HOME,
            requiredRoles = mapOf(),
        ),
        MenuItemRequirement(
            id = MENU_DOWNLOADS,
            titleRes = R.string.menu_downloads_text,
            iconRes = R.drawable.ic_upload,
            inset = Inset.HOME,
            requiredRoles = mapOf(Role.USER to skillTitleCount[Title.TEACHINGS]?.first()!!)
        ),

        MenuItemRequirement(
            id = MENU_PROFILE,
            titleRes = R.string.menu_profile,
            iconRes = R.drawable.ic_profile,
            inset = Inset.NETWORK,
            requiredRoles = mapOf()
        ),
        MenuItemRequirement(
            id = MENU_MASSAGE,
            titleRes = R.string.manu_message,
            iconRes = R.drawable.ic_light_hard,
            inset = Inset.EVENT,
            requiredRoles = mapOf(Role.USER to skillTitleCount[Title.PLAYER]?.first()!!)
        ),
        MenuItemRequirement(
            id = MENU_CHAT,
            titleRes = R.string.manu_message,
            iconRes = R.drawable.ic_light_hard,
            inset = Inset.NETWORK,
            requiredRoles = mapOf()
        ),
        MenuItemRequirement(
            id = MENU_CHAT_BANNED,
            titleRes = R.string.manu_message,
            iconRes = R.drawable.ic_light_hard,
            inset = Inset.NETWORK,
            requiredRoles = mapOf(Role.DEVELOPER to 100)
        ),
        MenuItemRequirement(
            id = MENU_CHAT_TOURNAMENT,
            titleRes = R.string.manu_message,
            iconRes = R.drawable.ic_light_hard,
            inset = Inset.NETWORK,
            requiredRoles = mapOf(Role.USER to skillTitleCount[Title.PLAYER]?.first()!!)
        ),
        MenuItemRequirement(
            id = MENU_EVENT,
            titleRes = R.string.menu_event,
            iconRes = R.drawable.ic_advanced,
            inset = Inset.NETWORK,
            requiredRoles = mapOf(Role.TESTER to 100, Role.MODERATOR to 100, Role.DEVELOPER to 100, Role.ADMIN to 100)
        ),

        MenuItemRequirement(
            id = MENU_LEADER,
            titleRes = R.string.menu_leader_text,
            iconRes = R.drawable.ic_advanced,
            inset = Inset.NETWORK,
            requiredRoles = mapOf(Role.USER to skillTitleCount[Title.TEACHINGS]?.first()!!)
        ),
        MenuItemRequirement(
            id = MENU_USERS,
            titleRes = R.string.menu_users_text,
            iconRes = R.drawable.ic_advanced,
            inset = Inset.NETWORK,
            requiredRoles = mapOf(Role.DEVELOPER to 100),
        ),
        MenuItemRequirement(
            id = MENU_NEWS,
            titleRes = R.string.menu_news_text,
            iconRes = R.drawable.ic_advanced,
            inset = Inset.NETWORK,
            requiredRoles = mapOf(Role.DEVELOPER to 100)
        ),
        MenuItemRequirement(
            id = MENU_ARENA,
            titleRes = R.string.menu_arena_text,
            iconRes = R.drawable.ic_advanced,
            inset = Inset.NETWORK,
            requiredRoles = mapOf(Role.USER to skillTitleCount[Title.TEACHINGS]?.first()!!)
        ),
        MenuItemRequirement(
            id = MENU_FRIEND,
            titleRes = R.string.menu_friend_text,
            iconRes = R.drawable.ic_advanced,
            inset = Inset.NETWORK,
            requiredRoles = mapOf(Role.DEVELOPER to 100)
        ),
        MenuItemRequirement(
            id = MENU_REPORT,
            titleRes = R.string.menu_report_text,
            iconRes = R.drawable.ic_advanced,
            inset = Inset.NETWORK,
            requiredRoles = mapOf(Role.DEVELOPER to 100)
        ),
        MenuItemRequirement(
            id = MENU_CONTACT,
            titleRes = R.string.menu_contact_text,
            iconRes = R.drawable.ic_contact,
            inset = Inset.NETWORK,
            requiredRoles = mapOf(Role.DEVELOPER to 100)
        ),
        MenuItemRequirement(
            id = MENU_EXIT,
            titleRes = R.string.menu_exit_text,
            iconRes = R.drawable.ic_advanced,
            inset = Inset.HOME,
            requiredRoles = emptyMap()
        )
    )
}