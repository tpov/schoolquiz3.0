package com.tpov.schoolquiz.presentation.custom

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
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
            //🥇🥈️🥉🎖🏅🏆🎗️🎃🎄🎁🎧🎞️📀🪙⭐🏆🎯🎓🏰❤
            "\uD83E\uDD47" -> "занявший 1 место в турнире"
            "\uD83E\uDD48" -> "Игрок занявший 2 место в турнире"
            "\uD83E\uDD49" -> "занявший 3 место в турнире"
            "Немецкий" -> "de"
            "Испанский" -> "es"
            else -> Locale.getDefault().language
        }
    }

    fun getMapStars(): Map<Double, String> {
        return mapOf(
            0.0 to "Обучение",
            0.2 to "Новичек",
            2.0 to "Игрок",
            6.0 to "Любитель",
            13.0 to "Ветеран",
            25.0 to "Гроссместер",
            50.0 to "Єксперт",
            100.0 to "Легенда",
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
            else -> ContextCompat.getColor(context, R.color.default_nick_color1)
        }
    }

    fun getImportance(profile: ProfileEntity): Int {
        log("profile.datePremium!! > TimeManager.getCurrentTime():  ${profile.datePremium!! > TimeManager.getCurrentTime()}")
        log("TimeManager.getCurrentTime():  ${TimeManager.getCurrentTime()}")
        log("profile.datePremium:  ${profile.datePremium}")
        return if (profile.tester!! >= 100) 2
        else if (profile.translater!! >= 100) 3
        else if (profile.moderator!! >= 100) 5
        else if (profile.admin!! >= 100) 0
        else if (profile.developer!! >= 100) 6
        else if (profile.datePremium!! > TimeManager.getCurrentTime()) 7
        else if (profile.pointsSkill!! >= 1000_0000 ) 4
        else 1
    }


}