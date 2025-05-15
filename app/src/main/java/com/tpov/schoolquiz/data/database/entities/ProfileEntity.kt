package com.tpov.schoolquiz.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: Int? = null,
    val tpovId: Int,
    val login: String? = null,
    val name: String = "",
    val nickname: String? = null,
    val birthday: String = "",
    val datePremium: String = "",
    val dateBanned: String = "",
    val trophy: String = "",
    val friends: String = "",
    val city: String = "",
    val logo: Int = 0,
    val commander: Int = 0,

    val timeInGamesInQuiz: Int = 0,
    val timeInGamesInChat: Int = 0,
    val smsCount: Int = 0,
    val countAnswer: Int = 0,
    val countTrueAnswer: Int = 0,
    val timeInQuizRating: Int = 0,

    val pointsGold: Int = 0,
    val pointsSkill: Int = 0,
    val pointsNolics: Int = 0,
    val buyQuizPlace: Int = 0,
    val buyTheme: String = "",
    val buyMusic: String = "",
    val buyLogo: String = "",
    val addPointsGold: Int = 0,
    val addPointsSkill: Int = 0,
    val addPointsNolics: Int = 0,
    val addTrophy: String = "",
    val addMassage: String = "",

    val dataCreateAcc: String,
    val dateSynch: String = "",
    val dateCloseApp: String = "",
    val languages: String,

    val sponsor: Int? = 0,
    val tester: Int? = 0,
    val translater: Int = 0,
    val moderator: Int = 0,
    val admin: Int = 0,
    val developer: Int = 0,

    val countBox: Int = 0,
    val timeLastOpenBox: String,
    val countDayBox: Int = 0,
    val launchCount: Int = 0,

    val standardLife: Int = 300,
    val standardHearts: Int = 1,
    val goldLife: Int = 0,
    val goldHearts: Int = 0,
)
