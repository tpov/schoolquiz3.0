package com.tpov.schoolquiz.data.fierbase

import com.google.firebase.database.IgnoreExtraProperties
import com.tpov.schoolquiz.data.database.entities.ProfileEntity
import com.tpov.shoppinglist.utils.TimeManager

@IgnoreExtraProperties
data class Profile constructor(
    val tpovId: String,
    val login: String,
    val name: String,
    val nickname: String,
    val birthday: String,
    val points: Points,
    val datePremium: String,
    val buy: Buy,
    val trophy: String,
    val friends: String,
    val city: String,
    val logo: Int,
    val timeInGames: TimeInGames,
    val addPoints: AddPoints,
    val dates: Dates,
    val idFirebase: String,
    val languages: String,
    val qualification: Qualification,
    val life: Life,
    val box: Box,
    val comander: Int
) {
    constructor() : this(
        0.toString(),
        "",
        "",
        "",
        "",
        Points( 0, 0, 0),
        "",
        Buy(0, "", "", ""),
        "",
        "",
        "",
        0,
        TimeInGames(0, 0, 0, 0, 0, 0),
        AddPoints(0, 0, 0, "", ""),
        Dates("", ""),
        "",
        "",
        Qualification(0,0, 0, 0, 0, 0),
        Life(0, 0),
        Box(0, "0", 0),
        0
    )
}

@IgnoreExtraProperties
data class Life(
    val countLife: Int,
    val countGoldLife: Int,
) {
    constructor() : this(
        0, 0
    )
}

@IgnoreExtraProperties
data class Box(
    val countBox: Int,
    val timeLastOpenBox: String,
    val coundDayBox: Int
) {
    constructor() : this(
        0, "", 0
    )
}


@IgnoreExtraProperties
data class Qualification(
    val sponsor: Int,
    val tester: Int,
    val translater: Int,
    val moderator: Int,
    val admin: Int,
    val developer: Int
) {
    constructor() : this(
         0, 0, 0, 0, 0, 0
    )
}

@IgnoreExtraProperties
data class TimeInGames(
    val timeInQuiz: Int,
    val timeInChat: Int,
    val smsPoints: Int,
    val countQuestions: Int?,
    val countTrueQuestion: Int,
    val timeInQuizRating: Int
) {
    constructor() : this(
        0, 0, 0, 0, 0, 0
    )
}

@IgnoreExtraProperties
data class Buy(
    val quizPlace: Int,
    val theme: String,
    val music: String,
    val logo: String
) {
    constructor() : this(
         0, "", "", ""
    )
}

@IgnoreExtraProperties
data class Points(
    val gold: Int,
    val skill: Int,
    val nolics: Int
) {
    constructor() : this(
        0,  0, 0
    )
}

@IgnoreExtraProperties
data class AddPoints(
    val addGold: Int,
    val addSkill: Int,
    val addNolics: Int,
    val addTrophy: String,
    val addMassage: String
) {
    constructor() : this(
        0,  0, 0, "", ""
    )
}

@IgnoreExtraProperties
data class Dates(
    val dataCreateAcc: String,
    val dateSynch: String
) {
    constructor() : this(
        "", ""
    )
}

fun ProfileEntity.toProfile(): Profile {
    return Profile(
        tpovId = this.tpovId.toString(),
        login = this.login!!,
        name = this.name ?: "",
        nickname = this.nickname!!,
        birthday = this.birthday!!,
        points = Points(
            gold = this.pointsGold!!,
            skill = this.pointsSkill!!,
            nolics = this.pointsNolics!!
        ),
        datePremium = this.datePremium!!,
        buy = Buy(
            quizPlace = this.buyQuizPlace!!,
            theme = this.buyTheme!!,
            music = this.buyMusic!!,
            logo = this.buyLogo!!
        ),
        trophy = this.trophy!!,
        friends = this.friends!!,
        city = this.city!!,
        logo = this.logo!!,
        timeInGames = TimeInGames(
            timeInQuiz = this.timeInGamesInQuiz!!,
            timeInChat = this.timeInGamesInChat!!,
            smsPoints = this.timeInGamesSmsPoints!!,
            countQuestions = this.timeInGamesCountQuestions,
            countTrueQuestion = this.timeInGamesCountTrueQuestion,
            timeInQuizRating = this.timeInQuizRating
        ),
        addPoints = AddPoints(
            this.addPointsGold ?: 0,
            this.addPointsSkill ?: 0,
            this.addPointsNolics ?: 0,
            this.addTrophy ?: "",
            this.addMassage ?: ""
        ),
        dates = Dates(this.dataCreateAcc!!, this.dateSynch!!),
        idFirebase = this.idFirebase!!,
        languages = this.languages!!,
        qualification = Qualification(
            this.sponsor!!,
            this.tester!!,
            this.translater!!,
            this.moderator!!,
            this.admin!!,
            this.developer!!
        ),
        life = Life(
            this.countLife!!,
            this.countGoldLife!!,
        ),
        box = Box(
            this.countBox!!,
            this.timeLastOpenBox!!,
            this.coundDayBox!!
        ),
        0
    )
}

fun Profile.toProfileEntity(countGold: Int, count: Int): ProfileEntity {
    return ProfileEntity(
        id = null,
        tpovId = this.tpovId.toInt(),
        login = this.login,
        name = this.name,
        birthday = this.birthday,
        pointsGold = this.points.gold,
        pointsSkill = this.points.skill,
        pointsNolics = this.points.nolics,
        datePremium = this.datePremium,
        buyQuizPlace = this.buy.quizPlace,
        buyTheme = this.buy.theme,
        buyMusic = this.buy.music,
        buyLogo = this.buy.logo,
        trophy = this.trophy,
        friends = this.friends,
        city = this.city,
        logo = this.logo,
       timeInGamesInQuiz = this.timeInGames.timeInQuiz,
        timeInGamesInChat = this.timeInGames.timeInChat,
        timeInGamesSmsPoints = this.timeInGames.smsPoints,
        addPointsGold = this.addPoints.addGold,
        addPointsSkill = this.addPoints.addSkill,
        addPointsNolics = this.addPoints.addNolics,
        addTrophy = this.addPoints.addTrophy,
        addMassage = this.addPoints.addMassage,
        dataCreateAcc = this.dates.dataCreateAcc,
        dateSynch = this.dates.dateSynch,
        idFirebase = this.idFirebase,
        languages = this.languages,
        sponsor = this.qualification.sponsor,
        tester = this.qualification.tester,
        translater = this.qualification.translater,
        moderator = this.qualification.moderator,
        admin = this.qualification.admin,
        developer = this.qualification.developer,
        nickname = this.nickname,
        coundDayBox = this.box.coundDayBox,
        countBox = this.box.countBox,
        timeLastOpenBox = this.box.timeLastOpenBox,
        countGold = countGold,
        count = count,
        countGoldLife = this.life.countGoldLife,
        countLife = this.life.countLife,
        dateCloseApp = TimeManager.getCurrentTime(),
        timeInGamesCountQuestions = this.timeInGames.countQuestions,
        timeInGamesCountTrueQuestion = this.timeInGames.countTrueQuestion,
        timeInQuizRating = this.timeInGames.timeInQuizRating,
        commander = this.comander,
    )
}