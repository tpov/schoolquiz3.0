package com.tpov.schoolquiz.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: Int?,
    val tpovId: Int,
    val login: String?,
    val name: String,
    val nickname: String?,
    val birthday: String,
    val datePremium: String,
    val dateBanned: String,
    val trophy: String,
    val friends: String,
    val city: String,
    val logo: Int,
    val commander: Int,

    val timeInGamesInQuiz: Int,
    val timeInGamesInChat: Int,
    val timeInGamesSmsPoints: Int,
    val timeInGamesCountQuestions: Int,
    val timeInGamesCountTrueQuestion: Int,
    val timeInQuizRating: Int,

    val pointsGold: Int,
    val pointsSkill: Int,
    val pointsNolics: Int,
    val buyQuizPlace: Int,
    val buyTheme: String,
    val buyMusic: String,
    val buyLogo: String,
    val addPointsGold: Int,
    val addPointsSkill: Int,
    val addPointsNolics: Int,
    val addTrophy: String,
    val addMassage: String,

    val dataCreateAcc: String,
    val dateSynch: String,
    val dateCloseApp: String,
    val languages: String,

    val sponsor: Int?,
    val tester: Int?,
    val translater: Int,
    val moderator: Int,
    val admin: Int,
    val developer: Int,

    val countBox: Int,
    val timeLastOpenBox: String,
    val coundDayBox: Int,

    val countLife: Int,
    val count: Int,
    val countGoldLife: Int,
    val countGold: Int,
) {
    constructor(
        id: Int? = null,
        dataCreateAcc: String,
        languages: String,
        timeLastOpenBox: String,
        tpovId: Int
    ) : this(
        id = id,
        tpovId = tpovId,
        login = null,
        name = "",
        nickname = null,
        birthday = "",
        datePremium = "",
        dateBanned = "",
        trophy = "",
        friends = "",
        city = "",
        logo = 0,
        commander = 0,

        timeInGamesInQuiz = 0,
        timeInGamesInChat = 0,
        timeInGamesSmsPoints = 0,
        timeInGamesCountQuestions = 0,
        timeInGamesCountTrueQuestion = 0,
        timeInQuizRating = 0,

        pointsGold = 0,
        pointsSkill = 0,
        pointsNolics = 0,
        buyQuizPlace = 0,
        buyTheme = "",
        buyMusic = "",
        buyLogo = "",
        addPointsGold = 0,
        addPointsSkill = 0,
        addPointsNolics = 0,
        addTrophy = "",
        addMassage = "",

        dataCreateAcc = dataCreateAcc,
        dateSynch = "",
        dateCloseApp = "",
        languages = languages,

        sponsor = 0,
        tester = 0,
        translater = 0,
        moderator = 0,
        admin = 0,
        developer = 0,

        countBox = 0,
        timeLastOpenBox = timeLastOpenBox,
        coundDayBox = 0,

        countLife = 1,
        count = 300,
        countGoldLife = 0,
        countGold = 0
    )
}