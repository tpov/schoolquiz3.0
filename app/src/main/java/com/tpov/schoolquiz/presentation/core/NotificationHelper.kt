package com.tpov.schoolquiz.presentation.core

import android.annotation.SuppressLint
import android.content.Context
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.presentation.COUNT_SKILL_AMATEUR
import com.tpov.schoolquiz.presentation.COUNT_SKILL_BEGINNER
import com.tpov.schoolquiz.presentation.COUNT_SKILL_EXPERT
import com.tpov.schoolquiz.presentation.COUNT_SKILL_GRANDMASTER
import com.tpov.schoolquiz.presentation.COUNT_SKILL_LEGEND
import com.tpov.schoolquiz.presentation.COUNT_SKILL_PLAYER
import com.tpov.schoolquiz.presentation.COUNT_SKILL_USERGUIDE_1
import com.tpov.schoolquiz.presentation.COUNT_SKILL_VETERAN
import com.tpov.schoolquiz.presentation.LVL_ADMIN_1_LVL
import com.tpov.schoolquiz.presentation.LVL_DEVELOPER_1_LVL
import com.tpov.schoolquiz.presentation.LVL_MODERATOR_1_LVL
import com.tpov.schoolquiz.presentation.LVL_TESTER_1_LVL
import com.tpov.schoolquiz.presentation.LVL_TRANSLATOR_1_LVL
import com.tpov.schoolquiz.presentation.LVL_TRANSLATOR_2_LVL
import com.tpov.schoolquiz.presentation.LVL_TRANSLATOR_3_LVL
import com.tpov.userguide.Options
import com.tpov.userguide.UserGuide

class NotificationHelper(private val context: Context) {
    private val userguide: UserGuide = UserGuide(context)
    private var counterValue: Int = 0

    fun newVersion(versionApp: String) {
        userguide.addGuideNewVersion(
            context.getString(R.string.commit_3_1_00),
            versionApp,
            context.resources.getDrawable(R.mipmap.ic_launcher),
            options = Options(countRepeat = 1)
        )
    }

    fun updateProfile(profile: ProfileEntity) {
        var i = 0

        if ((profile.translater ?: 0) >= LVL_TRANSLATOR_1_LVL) userguide.addNotification(++i,text = context.getString(R.string.userguide_new_qualif_translate1, profile.translater),titleText = context.getString(R.string.new_qualification),icon = context.resources.getDrawable(R.drawable.star_full))
        if ((profile.translater ?: 0) >= LVL_TRANSLATOR_2_LVL) userguide.addNotification(++i,titleText = context.getString(R.string.new_qualification),text = context.getString(R.string.userguide_new_qualif_translate2, profile.translater),icon = context.resources.getDrawable(R.drawable.star_full))
        if ((profile.translater ?: 0) >= LVL_TRANSLATOR_3_LVL) userguide.addNotification(
            ++i,
            titleText = context.getString(R.string.new_qualification),
            text = context.getString(
                R.string.userguide_new_qualif_translate3, profile.translater
            ),
            icon = context.resources.getDrawable(R.drawable.star_full)
        )
        if ((profile.tester ?: 0) >= LVL_TESTER_1_LVL) userguide.addNotification(
            ++i,
            titleText = context.getString(R.string.new_qualification),
            text = context.getString(R.string.userguide_new_qualif_tester1, profile.translater),
            icon = context.resources.getDrawable(R.drawable.star_full)
        )
        if ((profile.moderator ?: 0) >= LVL_MODERATOR_1_LVL) userguide.addNotification(
            ++i,
            text = context.getString(
                R.string.userguide_new_qualif_moderator1, profile.translater
            ),
            titleText = context.getString(R.string.new_qualification),
            icon = context.resources.getDrawable(R.drawable.star_full)
        )
        if ((profile.admin ?: 0) >= LVL_ADMIN_1_LVL) userguide.addNotification(
            ++i,
            text = context.getString(R.string.userguide_new_qualif_admin1, profile.translater),
            titleText = context.getString(R.string.new_qualification),
            icon = context.resources.getDrawable(R.drawable.star_full)
        )
        if ((profile.developer ?: 0) >= LVL_DEVELOPER_1_LVL) userguide.addNotification(
            ++i,
            text = context.getString(
                R.string.userguide_new_qualif_developer1, profile.translater
            ),
            titleText = context.getString(R.string.new_qualification),
            icon = context.resources.getDrawable(R.drawable.star_full)
        )

        if ((profile.addPointsGold ?: 0) != 0) userguide.addNotification(
            ++i,
            text = context.getString(R.string.userguide_massage_add_gold, profile.addPointsGold),
            titleText = context.getString(R.string.userguide_title_add_points),
            icon = context.resources.getDrawable(R.drawable.ic_gold)
        )

        if ((profile.addPointsNolics ?: 0) != 0) userguide.addNotification(
            ++i,
            text = context.getString(R.string.userguide_massage_add_nolic, profile.addPointsNolics),
            titleText = context.getString(R.string.userguide_title_add_points),
            icon = context.resources.getDrawable(R.drawable.ic_gold)
        )

        if ((profile.addPointsSkill ?: 0) != 0) userguide.addNotification(
            ++i,
            text = context.getString(R.string.userguide_massage_add_skill, profile.addPointsSkill),
            titleText = context.getString(R.string.userguide_title_add_points),
            icon = context.resources.getDrawable(R.drawable.ic_gold)
        )

        if (profile.addTrophy != "") userguide.addNotification(
            ++i,
            text = context.getString(R.string.userguide_massage_add_trophy, profile.addTrophy),
            titleText = context.getString(R.string.userguide_title_add_points),
            icon = context.resources.getDrawable(R.drawable.baseline_favorite_24)
        )

        if (profile.addMassage != "") userguide.addNotification(
            ++i,
            text = " - ${profile.addMassage}",
            titleText = context.getString(R.string.userguide_title_developer_massage)
        )
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    fun showNotificationAll(skill: Int?) {
        userguide.setCounterValue(skill ?: 0)

        var i = 100
        userguide.addNotification(
            ++i,
            text = context.getString(R.string.userguide_massage_text_legend),
            titleText = context.getString(R.string.userguide_title_text_legend_massage),
            options = Options(countKey = COUNT_SKILL_LEGEND),
            icon = context.resources.getDrawable(R.drawable.star_full)
        )
        userguide.addNotification(
            ++i,
            text = context.getString(R.string.userguide_new_qualif_player_legend),
            titleText = context.getString(R.string.userguide_new_qualif_player_title, skill),
            options = Options(countKey = COUNT_SKILL_LEGEND),
            icon = context.resources.getDrawable(R.drawable.star_full)
        )
        userguide.addNotification(
            ++i,
            text = context.getString(R.string.userguide_new_qualif_player_expert),
            titleText = context.getString(R.string.userguide_new_qualif_player_title, skill),
            options = Options(countKey = COUNT_SKILL_EXPERT),
            icon = context.resources.getDrawable(R.drawable.star_full)
        )
        userguide.addNotification(
            ++i,
            text = context.getString(R.string.userguide_new_qualif_player_gransmaster),
            titleText = context.getString(R.string.userguide_new_qualif_player_title, skill),
            options = Options(countKey = COUNT_SKILL_GRANDMASTER),
            icon = context.resources.getDrawable(R.drawable.star_full)
        )
        userguide.addNotification(
            ++i,
            text = context.getString(R.string.userguide_new_qualif_player_player),
            titleText = context.getString(R.string.userguide_new_qualif_player_title, skill),
            options = Options(countKey = COUNT_SKILL_VETERAN),
            icon = context.resources.getDrawable(R.drawable.star_full)
        )
        userguide.addNotification(
            ++i,
            text = context.getString(R.string.userguide_massage_text_profiles_open),
            titleText = context.getString(R.string.userguide_new_qualif_player_title, skill),
            options = Options(countKey = COUNT_SKILL_AMATEUR),
            icon = context.resources.getDrawable(R.drawable.nav_user)
        )
        userguide.addNotification(
            ++i,
            text = context.getString(R.string.userguide_new_qualif_player_player),
            titleText = context.getString(R.string.userguide_new_qualif_player_title, skill),
            options = Options(countKey = COUNT_SKILL_AMATEUR),
            icon = context.resources.getDrawable(R.drawable.star_full)
        )
        userguide.addNotification(
            ++i,
            text = context.getString(R.string.userguide_massage_text_best_player_open),
            titleText = context.getString(R.string.userguide_new_qualif_player_title, skill),
            options = Options(countKey = COUNT_SKILL_BEGINNER),
            icon = context.resources.getDrawable(R.drawable.nav_leader)
        )
        userguide.addNotification(
            ++i,
            text = context.getString(R.string.userguide_new_qualif_player_player),
            titleText = context.getString(R.string.userguide_new_qualif_player_title, skill),
            options = Options(countKey = COUNT_SKILL_PLAYER),
            icon = context.resources.getDrawable(R.drawable.star_full)
        )
        /////////////////////////////

        userguide.addNotification(
            ++i,
            text = context.getString(R.string.userguide_new_qualif_player_beginner),
            titleText = context.getString(R.string.userguide_new_qualif_player_title, skill),
            options = Options(countKey = COUNT_SKILL_BEGINNER),
            icon = context.resources.getDrawable(R.drawable.star_full)
        )
        userguide.addNotification(
            ++i,
            text = context.getString(R.string.userguide_massage_text_user_quiz_open),
            titleText = context.getString(R.string.userguide_new_qualif_player_title, skill),
            options = Options(countKey = COUNT_SKILL_BEGINNER),
            icon = context.resources.getDrawable(R.drawable.nav_my_quiz)
        )
        userguide.addNotification(
            ++i,
            text = context.getString(R.string.userguide_massage_text_arena_open),
            titleText = context.getString(R.string.userguide_new_qualif_player_title, skill),
            options = Options(countKey = COUNT_SKILL_BEGINNER),
            icon = context.resources.getDrawable(R.drawable.baseline_public_24)
        )
        userguide.addNotification(
            ++i,
            text = context.getString(R.string.userguide_massage_text_menu_functuions),
            titleText = context.getString(R.string.userguide_new_qualif_player_title, skill),
            options = Options(countKey = COUNT_SKILL_USERGUIDE_1),
            icon = context.resources.getDrawable(R.drawable.star_full)
        )

        /////////////////////////////////////////
        if (skill != 0) userguide.addNotification(
            ++i,
            text = context.getString(R.string.userguide_massage_text_translate),
            titleText = context.getString(R.string.userguide_title_text_translate_massage),
            icon = context.getDrawable(R.drawable.ic_translate),
        )

        if (skill != 0) userguide.addNotification(
            ++i,
            titleText = context.getString(R.string.userguide_new_qualif_player_title, skill),
            text = context.getString(R.string.userguide_massage_text_hello),
            icon = context.resources.getDrawable(R.drawable.star_full)
        )
    }
}