package com.tpov.schoolquiz.presentation.custom

import androidx.lifecycle.MutableLiveData
import java.util.*

object Values {

    var loadText: MutableLiveData<String> = MutableLiveData()
    var loadProgress: MutableLiveData<Int> = MutableLiveData()

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

    //Пройти квест
    //Открыть вкладки
}