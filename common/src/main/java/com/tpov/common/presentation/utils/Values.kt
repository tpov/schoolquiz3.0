package com.tpov.common.presentation.utils

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.tpov.common.COUNT_SKILL_AMATEUR
import com.tpov.common.COUNT_SKILL_BEGINNER
import com.tpov.common.COUNT_SKILL_EDUCATION
import com.tpov.common.COUNT_SKILL_EXPERT
import com.tpov.common.COUNT_SKILL_GRANDMASTER
import com.tpov.common.COUNT_SKILL_LEGEND
import com.tpov.common.COUNT_SKILL_PLAYER
import com.tpov.common.COUNT_SKILL_VETERAN
import com.tpov.common.R
import com.tpov.common.THROPHY1
import com.tpov.common.THROPHY2
import com.tpov.common.THROPHY3

@SuppressLint("StaticFieldLeak")
object Values {
    lateinit var context: Context

    var synthLiveData = MutableLiveData<Int>()
    var loadText: MutableLiveData<String> = MutableLiveData()
    var loadProgress: MutableLiveData<Int> = MutableLiveData()
    var synth = 0

    fun init(context: Context) {
        Values.context = context
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
}