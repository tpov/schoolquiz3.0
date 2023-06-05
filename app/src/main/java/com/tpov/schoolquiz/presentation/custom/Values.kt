package com.tpov.schoolquiz.presentation.custom

import java.util.*

object Values {
    fun getNameThropy(language: String): String {
        return when (language) {
            //🥇🥈️🥉🎖🏅🏆🎗️🎃🎄🎁🎧🎞️🪙📀🏆⭐🎯🎓🏰❤
            "\uD83E\uDD47" -> "занявший 1 место в турнире"
            "\uD83E\uDD48" -> "Игрок занявший 2 место в турнире"
            "\uD83E\uDD49" -> "занявший 3 место в турнире"
            "Немецкий" -> "de"
            "Испанский" -> "es"
            else -> Locale.getDefault().language
        }
    }
}