package com.tpov.schoolquiz.presentation.core

import com.tpov.common.COUNT_SKILL_BEGINNER
import com.tpov.common.LVL_ADMIN_1_LVL
import com.tpov.common.LVL_DEVELOPER_1_LVL
import com.tpov.common.LVL_MODERATOR_1_LVL
import com.tpov.common.LVL_TESTER_1_LVL
import com.tpov.common.LVL_TRANSLATOR_1_LVL
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.model.Qualification

object VisibleItems {

    fun getShowItemsMenuNetwork(qualification: Qualification): List<Pair<Int, Int>> {
        val skill = SharedPreferencesManager.getSkill()

        val items = mutableListOf(
            R.string.nav_chat to R.drawable.nav_chat
        )
        items.add(
                R.string.nav_profile to R.drawable.nav_profile
                )
        items.add(R.string.nav_players to R.drawable.nav_user)
        items.add(R.string.nav_massages to R.drawable.nav_massage)
        items.add(R.string.nav_chat to R.drawable.ic_profile)
        items.add(R.string.nav_arena to R.drawable.ic_profile)
        items.add(R.string.nav_leaders to R.drawable.ic_profile)
        items.add(R.string.nav_tournament to R.drawable.ic_profile)
        items.add(R.string.nav_archive to R.drawable.ic_profile)
        items.add(R.string.nav_referral_program to R.drawable.ic_profile)
        items.add(R.string.nav_friends to R.drawable.ic_profile)
        items.add(R.string.nav_roles to R.drawable.ic_profile)
        items.add(R.string.nav_logout to R.drawable.ic_profile)
        if (skill >= COUNT_SKILL_BEGINNER) {
        }


        if (skill >= 6_0000) {

        }

        if (qualification.translator >= LVL_TRANSLATOR_1_LVL
            || qualification.tester >= LVL_TESTER_1_LVL
            || qualification.admin >= LVL_ADMIN_1_LVL
            || qualification.moderator >= LVL_MODERATOR_1_LVL
            || qualification.developer >= LVL_DEVELOPER_1_LVL
        ) {

        }

        if (qualification.developer >= LVL_DEVELOPER_1_LVL) {
        }

        return items

    }
    fun getShowItemsMenuHome(qualification: Qualification): List<Pair<Int, Int>> {
        val skill = SharedPreferencesManager.getSkill()
        val items = mutableListOf(
            R.string.nav_home to R.drawable.ic_home
        )
        items.add(R.string.nav_my_quests to R.drawable.ic_profile)
        items.add(R.string.nav_downloads to R.drawable.ic_profile)
        items.add(R.string.nav_settings to R.drawable.ic_profile)
        items.add(R.string.nav_tasks to R.drawable.ic_profile)
        items.add(R.string.nav_about to R.drawable.ic_profile)

        if (skill >= COUNT_SKILL_BEGINNER) {
            items.add(R.string.nav_my_quiz to R.drawable.nav_my_quiz)
        }


        if (qualification.developer >= LVL_DEVELOPER_1_LVL) {
        }

        return items
    }

}


