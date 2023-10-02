package com.tpov.schoolquiz.presentation.custom

import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.model.Qualification
import com.tpov.schoolquiz.presentation.*

object VisibleItems {

    fun getShowItemsMenuNetwork(qualification: Qualification): List<Pair<Int, Int>> {
        val skill = SharedPreferencesManager.getSkill()

        val items = mutableListOf(
            R.string.nav_chat to R.drawable.nav_chat
        )

        if (skill >= 2000) {
            items.add(R.string.nav_global to R.drawable.baseline_public_24)
        }

        if (skill >= COUNT_SKILL_BEGINNER) {
        }

        if (skill >= 6_0000) {

        }

        if (qualification.translator >= LVL_TRANSLATOR_1_LVL
            || qualification.tester >= LVL_TESTER_1_LVL
            || qualification.admin >= LVL_ADMIN_1_LVL
            || qualification.moderator >= LVL_MODERATOR_1_LVL
            || qualification.developer >= LVL_DEVELOPER_1_LVL) {
            items.add(
                R.string.nav_profile to R.drawable.nav_profile
            )
            items.add(
                R.string.nav_task to R.drawable.nav_task
            )
        }


        if (qualification.developer >= LVL_DEVELOPER_1_LVL) {
            items.add(R.string.nav_leaders to R.drawable.nav_leader)
            items.add(R.string.nav_players to R.drawable.nav_user)
            items.add(R.string.nav_news to R.drawable.ic_new)
            items.add(R.string.nav_massages to R.drawable.nav_massage)
            items.add(R.string.nav_reports to R.drawable.nav_report)
            items.add(R.string.nav_friends to R.drawable.ic_baseline_drive_folder_upload_24)
            items.add(R.string.nav_contact to R.drawable.ic_contact)
        }

        items.add(R.string.nav_exit to R.drawable.nav_exit)
        return items

    }
    fun getShowItemsMenuHome(qualification: Qualification): List<Pair<Int, Int>> {
        val skill = SharedPreferencesManager.getSkill()
        val items = mutableListOf(
            R.string.nav_home to R.drawable.ic_home
        )

        if (skill >= 2000) {
            items.add(R.string.nav_my_quiz to R.drawable.nav_my_quiz)
        }


        if (qualification.developer >= LVL_DEVELOPER_1_LVL) {
            items.add(R.string.nav_downloads to R.drawable.baseline_download_24)
            items.add(R.string.nav_settings to R.drawable.ic_settings)
        }


        return items
    }

}


