package com.tpov.schoolquiz.presentation.core

import android.annotation.SuppressLint
import android.content.Context
import com.tpov.common.COUNT_SKILL_AMATEUR
import com.tpov.common.COUNT_SKILL_BEGINNER
import com.tpov.common.COUNT_SKILL_EXPERT
import com.tpov.common.COUNT_SKILL_GRANDMASTER
import com.tpov.common.COUNT_SKILL_LEGEND
import com.tpov.common.COUNT_SKILL_PLAYER
import com.tpov.common.COUNT_SKILL_USERGUIDE_1
import com.tpov.common.COUNT_SKILL_VETERAN
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.presentation.main.MainActivity
import com.tpov.userguide.Options
import com.tpov.userguide.UserGuide
import kotlinx.coroutines.InternalCoroutinesApi

class NotificationHelper(private val context: Context) {
    private val userguide: UserGuide = UserGuide(context)
    private var counterValue: Int = 0

    private val builderNewQualification = userguide.guideBuilder()
    private val builderSetupUserGuide = userguide.guideBuilder()
    private val builderNewSmsInChat = userguide.guideBuilder()
    private val builderGuide = userguide.guideBuilder()
    private val builderUpdateProfile = userguide.guideBuilder()

    @SuppressLint("UseCompatLoadingForDrawables")
    fun setupUserGuide(versionApp: String) {
        builderSetupUserGuide
            .setText(context.getString(R.string.commit_3_0_18))
            .setIcon(context.resources.getDrawable(R.mipmap.ic_launcher, context.theme))
            .setTitleText(versionApp)
            .setOptions(Options(showDot = false, exactMatchKey = versionApp.toIntOrNull() ?: 0, idGroupGuide = "setupUserGuide".hashCode()))
            .build()
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    fun updateProfileUserGuide(textUpdateProfile: String, ) {
        builderUpdateProfile.setTitleText("Награда от разработчиков")
            .setText(textUpdateProfile)
            .setIcon(context.getDrawable(R.drawable.ic_box))
            .setOptions(Options(idGroupGuide = "updateProfileUserGuide".hashCode(), showWithoutOptions = true))
    }

    fun showBuilderNewQualification() {
        builderNewQualification.show()
    }

    fun showBuilderUpdateProfile() {
        builderUpdateProfile.show()
    }

    fun showBuilderSetupUserGuide() {
        builderSetupUserGuide.show()
    }

    fun showBuilderNewSmsInChat() {
        builderNewSmsInChat.show()
    }

    fun showBuilderGuide() {
        builderGuide.show()
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun newQualification(text: String, valueSkill: Int) {
        builderNewQualification.setTitleText("Новая квалификация")
            .setText(text)
            .setIcon(context.getDrawable(com.tpov.common.R.drawable.back_main))
            .setOptions(Options(showDot = false, minValueKey = valueSkill, idGroupGuide = "newQualification".hashCode()))
    }

    @OptIn(InternalCoroutinesApi::class)
    @SuppressLint("UseCompatLoadingForDrawables")
    private fun newSmsInChat(number: Int) {
        builderSetupUserGuide.setView((context as? MainActivity)?.findViewById(R.id.menu_settings))
            .setOptions(Options(showDot = true, numberDot = number.toString(), showWithoutOptions = number > 0, idGroupGuide = "chat".hashCode()))
    }

       @OptIn(InternalCoroutinesApi::class)
    @SuppressLint("UseCompatLoadingForDrawables")
    private fun guide(numberSkill: Int, textGuide: String, minNumberSkill: Int) {
           builderGuide.setTitleText("Опыт: $numberSkill")
            .setText(textGuide)
            .setIcon(context.getDrawable(R.drawable.star_full))
            .setOptions(Options(minValueKey = minNumberSkill, idGroupGuide = "guide".hashCode()))
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    fun showNotificationAll(skill: Int?) {
        userguide.setExactMatchKey(skill ?: 0)

        fun buildGuide(textGuide: String, minNumberSkill: Int) {
            guide(skill ?: 0, textGuide, minNumberSkill)
        }

        buildGuide(
            context.getString(R.string.userguide_massage_text_legend),
            COUNT_SKILL_LEGEND
        )
        buildGuide(
            context.getString(R.string.userguide_new_qualif_player_legend),
            COUNT_SKILL_LEGEND
        )
        buildGuide(
            context.getString(R.string.userguide_new_qualif_player_expert),
            COUNT_SKILL_EXPERT
        )
        buildGuide(
            context.getString(R.string.userguide_new_qualif_player_gransmaster),
            COUNT_SKILL_GRANDMASTER
        )
        buildGuide(
            context.getString(R.string.userguide_new_qualif_player_player),
            COUNT_SKILL_VETERAN
        )
        buildGuide(
            context.getString(R.string.userguide_massage_text_profiles_open),
            COUNT_SKILL_AMATEUR
        )
        buildGuide(
            context.getString(R.string.userguide_new_qualif_player_player),
            COUNT_SKILL_AMATEUR
        )
        buildGuide(
            context.getString(R.string.userguide_massage_text_best_player_open),
            COUNT_SKILL_BEGINNER
        )
        buildGuide(
            context.getString(R.string.userguide_new_qualif_player_player),
            COUNT_SKILL_PLAYER
        )
        buildGuide(
            context.getString(R.string.userguide_new_qualif_player_beginner),
            COUNT_SKILL_BEGINNER
        )
        buildGuide(
            context.getString(R.string.userguide_massage_text_user_quiz_open),
            COUNT_SKILL_BEGINNER
        )
        buildGuide(
            context.getString(R.string.userguide_massage_text_arena_open),
            COUNT_SKILL_BEGINNER
        )
        buildGuide(
            context.getString(R.string.userguide_massage_text_menu_functuions),
            COUNT_SKILL_USERGUIDE_1
        )

        if (skill != 0) {
            buildGuide(
                context.getString(R.string.userguide_massage_text_translate),
                0
            )
            buildGuide(
                context.getString(R.string.userguide_massage_text_hello),
                0
            )
        }

        builderGuide.show()
    }
}
