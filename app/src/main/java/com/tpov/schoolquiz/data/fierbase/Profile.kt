package com.tpov.schoolquiz.data.fierbase

import com.google.firebase.database.IgnoreExtraProperties
import com.tpov.schoolquiz.data.database.entities.ProfileEntity

@IgnoreExtraProperties
data class Profile constructor(
    val tpovId: Int,
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
    val box: Box
) {
    constructor() : this(
        0,
        "",
        "",
        "",
        "",
        Points(0, 0, 0, 0),
        "",
        Buy(0, 0, 0, "", "", ""),
        "",
        "",
        "",
        0,
        TimeInGames(0,0,0, 0),
        AddPoints(0, 0, 0, 0, ""),
        Dates("", ""),
        "",
        "",
        Qualification(0,0,0,0,0,0,0),
        Life(0,0,0,0),
        Box(0,"0",0)
    )
}
@IgnoreExtraProperties
data class Life(
    val countLife: Int,
    val count: Int,
    val countGoldLife: Int,
    val countGold: Int
) {
    constructor() : this(
        0, 0, 0, 0
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
    val gamer: Int,
    val sponsor: Int,
    val tester: Int,
    val translater: Int,
    val moderator: Int,
    val admin: Int,
    val developer: Int
) {
    constructor() : this(
        0, 0, 0, 0, 0, 0, 0
    )
}

@IgnoreExtraProperties
data class TimeInGames(
    val allTime: Int,
    val timeInQuiz: Int,
    val timeInChat: Int,
    val smsPoints: Int
) {
    constructor() : this(
        0,0,0, 0
    )
}

@IgnoreExtraProperties
data class Buy(
    val heart: Int,
    val goldHeart: Int,
    val quizPlace: Int,
    val theme: String,
    val music: String,
    val logo: String
) {
    constructor() : this(
        0, 0, 0, "", "", ""
    )
}

@IgnoreExtraProperties
data class Points(
    val gold: Int,
    val skill: Int,
    val skillInSesone: Int,
    val nolics: Int
) {
    constructor() : this(
        0, 0, 0, 0
    )
}

@IgnoreExtraProperties
data class AddPoints(
    val addGold: Int,
    val addSkill: Int,
    val addSkillInSesone: Int,
    val addNolics: Int,
    val addTrophy: String
) {
    constructor() : this(
        0, 0, 0, 0, ""
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
        tpovId = this.tpovId!!,
        login = this.login!!,
        name = this.name ?: "",
        nickname = this.nickname!!,
        birthday = this.birthday!!,
        points = Points(
            gold = this.pointsGold!!,
            skill = this.pointsSkill!!,
            skillInSesone = this.pointsSkillInSeason!!,
            nolics = this.pointsNolics!!
        ),
        datePremium = this.datePremium!!,
        buy = Buy(
            heart = this.buyHeart!!,
            goldHeart = this.buyGoldHeart!!,
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
            allTime = this.timeInGamesAllTime!!,
            timeInQuiz = this.timeInGamesInQuiz!!,
            timeInChat = this.timeInGamesInChat!!,
            smsPoints = this.timeInGamesSmsPoints!!
        ),
        addPoints = AddPoints(
            this.addPointsGold!!,
            this.addPointsSkill!!,
            this.addPointsSkillInSeason!!,
            this.addPointsNolics!!,
            this.addTrophy!!
        ),
        dates = Dates(this.dataCreateAcc!!, this.dateSynch!!),
        idFirebase = this.idFirebase!!,
        languages = this.languages!!,
        qualification = Qualification(
            this.gamer!!,
            this.sponsor!!,
            this.tester!!,
            this.translater!!,
            this.moderator!!,
            this.admin!!,
            this.developer!!
        ),
        life = Life(
            this.countLife!!,
            this.count!!,
            this.countGoldLife!!,
            this.countGold!!
        ),
        box = Box(
            this.countBox!!,
            this.timeLastOpenBox!!,
            this.coundDayBox!!
        )

    )

}

fun Profile.toProfileEntity(): ProfileEntity {
    return ProfileEntity(
        id = null,
        tpovId = this.tpovId,
        login = this.login,
        name = this.name,
        birthday = this.birthday,
        pointsGold = this.points.gold,
        pointsSkill = this.points.skill,
        pointsSkillInSeason = this.points.skillInSesone,
        pointsNolics = this.points.nolics,
        datePremium = this.datePremium,
        buyHeart = this.buy.heart,
        buyGoldHeart = this.buy.goldHeart,
        buyQuizPlace = this.buy.quizPlace,
        buyTheme = this.buy.theme,
        buyMusic = this.buy.music,
        buyLogo = this.buy.logo,
        trophy = this.trophy,
        friends = this.friends,
        city = this.city,
        logo = this.logo,
        timeInGamesAllTime = this.timeInGames.allTime,
        timeInGamesInQuiz = this.timeInGames.timeInQuiz,
        timeInGamesInChat = this.timeInGames.timeInChat,
        timeInGamesSmsPoints = this.timeInGames.smsPoints,
        addPointsGold = this.addPoints.addGold,
        addPointsSkill = this.addPoints.addSkill,
        addPointsSkillInSeason = this.addPoints.addSkillInSesone,
        addPointsNolics = this.addPoints.addNolics,
        addTrophy = this.addPoints.addTrophy,
        dataCreateAcc = this.dates.dataCreateAcc,
        dateSynch = this.dates.dateSynch,
        idFirebase = this.idFirebase,
        languages = this.languages,
        gamer = this.qualification.gamer,
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
        countGold = this.life.countGold,
        count = this.life.count,
        countGoldLife = this.life.countGoldLife,
        countLife = this.life.countLife
    )
}