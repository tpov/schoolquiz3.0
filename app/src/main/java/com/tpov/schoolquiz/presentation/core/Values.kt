package com.tpov.schoolquiz.presentation.core

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.schoolquiz.presentation.*
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesSkill.COUNT_COLOR_8
import com.tpov.schoolquiz.presentation.network.event.log
import com.tpov.shoppinglist.utils.TimeManager
import java.util.*

@SuppressLint("StaticFieldLeak")
object Values {

    var loadText: MutableLiveData<String> = MutableLiveData()
    var loadProgress: MutableLiveData<Int> = MutableLiveData()
    lateinit var context: Context


    fun init(context: Context) {
        this.context = context
    }

    fun getNameThropy(language: String): String {
        return when (language) {
            //🎖🏅🏆🎗️🎃🎄🎁🎧🎞️📀🪙⭐🏆🎯🎓🏰❤
            THROPHY1 -> context.getString(R.string.thropy1)
            THROPHY2 -> context.getString(R.string.thropy2)
            THROPHY3 -> context.getString(R.string.thropy2)
            else -> context.getString(R.string.thropy_error)
        }
    }

    fun getMapStars(): Map<Int, String> {
        return mapOf(
            COUNT_SKILL_EDUCATION to context.getString(R.string.education),
            COUNT_SKILL_BEGINNER to context.getString(R.string.beginner),
            COUNT_SKILL_PLAYER to context.getString(R.string.player),
            COUNT_SKILL_AMATEUR to context.getString(R.string.amateur),
            COUNT_SKILL_VETERAN to context.getString(R.string.veteran),
            COUNT_SKILL_GRANDMASTER to context.getString(R.string.grandmaster),
            COUNT_SKILL_EXPERT to context.getString(R.string.expert),
            COUNT_SKILL_LEGEND to context.getString(R.string.legend),
        )
    }

    fun getColorNickname(importance: Int): Int {

        log("importance:  $importance")
        return when (importance) {
            0 -> ContextCompat.getColor(context, R.color.default_nick_color0)
            1 -> ContextCompat.getColor(context, R.color.default_nick_color1)
            2 -> ContextCompat.getColor(context, R.color.default_nick_color2)
            3 -> ContextCompat.getColor(context, R.color.default_nick_color3)
            4 -> ContextCompat.getColor(context, R.color.default_nick_color4)
            5 -> ContextCompat.getColor(context, R.color.default_nick_color5)
            6 -> ContextCompat.getColor(context, R.color.default_nick_color6)
            7 -> ContextCompat.getColor(context, R.color.default_nick_color7)
            8 -> ContextCompat.getColor(context, R.color.default_nick_color8)
            else -> ContextCompat.getColor(context, R.color.default_nick_color1)
        }
    }

    fun getImportance(profile: ProfileEntity): Int {
        return if (profile.pointsSkill!! < COUNT_COLOR_8) 8
        else if (profile.tester!! >= LVL_TESTER_1_LVL) 2
        else if (profile.translater!! >= LVL_TRANSLATOR_1_LVL) 3
        else if (profile.moderator!! >= LVL_MODERATOR_1_LVL) 5
        else if (profile.admin!! >= LVL_ADMIN_1_LVL) 0
        else if (profile.developer!! >= LVL_DEVELOPER_1_LVL) 6
        else if (profile.datePremium!! > TimeManager.getCurrentTime()) 7
        else if (profile.pointsSkill >= COUNT_SKILL_LEGEND) 4
        else 1
    }


}