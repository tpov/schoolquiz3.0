package com.tpov.schoolquiz.data.fierbase

import com.google.firebase.database.IgnoreExtraProperties
import com.tpov.common.data.utils.TimeManager
import com.tpov.schoolquiz.data.database.entities.ProfileEntity

@IgnoreExtraProperties
data class ProfileRemote constructor(
    val basic: Basic,
    val points: Points,
    val buy: Buy,
    val timeInGames: TimeInGames,
    val addPoints: AddPoints,
    val dates: Dates,
    val qualification: Qualification,
    val life: Life,
    val box: Box,

)
@IgnoreExtraProperties
data class Basic(
    val tpovId: Long,
    val login: String,
    val name: String,
    val nickname: String,
    val birthday: String,
    val city: String,
    val languages: String,
    val comander: Long,
    val logo: Long,
)

@IgnoreExtraProperties
data class Life(
    val countLife: Long,
    val countGoldLife: Long,
) {
    constructor() : this(
        0L, 0L
    )
}

@IgnoreExtraProperties
data class Box(
    val countBox: Long,
    val timeLastOpenBox: String,
    val countDayBox: Long
) {
    constructor() : this(
        0L, "", 0L
    )
}


@IgnoreExtraProperties
data class Qualification(
    val sponsor: Long,
    val tester: Long,
    val translater: Long,
    val moderator: Long,
    val admin: Long,
    val developer: Long
) {
    constructor() : this(
         0L, 0L, 0L, 0L, 0L, 0L
    )
}

@IgnoreExtraProperties
data class TimeInGames(
    val timeInQuiz: Long,
    val timeInChat: Long,
    val smsPoints: Long,
    val countQuestions: Long?,
    val countTrueQuestion: Long,
    val quizRating: Long
) {
    constructor() : this(
        0L, 0L, 0L, 0L, 0L, 0L
    )
}

@IgnoreExtraProperties
data class Buy(
    val quizPlace: Long,
    val theme: String,
    val music: String,
    val logo: String
) {
    constructor() : this(
         0L, "", "", ""
    )
}

@IgnoreExtraProperties
data class Points(
    val gold: Long,
    val skill: Long,
    val nolics: Long,
    val trophy: String,
    val friends: String,
) {
    constructor() : this(
        0L,  0L, 0L
    )
}

@IgnoreExtraProperties
data class AddPoints(
    val addGold: Long,
    val addSkill: Long,
    val addNolics: Long,
    val addTrophy: String,
    val addMassage: String
) {
    constructor() : this(
        0L,  0L, 0L, "", ""
    )
}

@IgnoreExtraProperties
data class Dates(
    val dataCreateAcc: String,
    val dateSynch: String,
    val datePremium: String,
    val dateBanned: String,
) {
    constructor() : this(
        "", "", "", ""
    )
}

fun ProfileEntity.toProfile(): ProfileRemote {
    return ProfileRemote(
        tpovId = tpovId.toLong(),
        login = this.login ?: "",
        name = this.name,
        nickname = this.nickname ?: "",
        birthday = this.birthday,
        points = Points(
            gold = this.pointsGold.toLong(),
            skill = this.pointsSkill.toLong()!!,
            nolics = this.pointsNolics.toLong()!!
        ),
        buy = Buy(
            quizPlace = this.buyQuizPlace.toLong()!!,
            theme = this.buyTheme!!,
            music = this.buyMusic!!,
            logo = this.buyLogo!!
        ),
        trophy = this.trophy!!,
        friends = this.friends!!,
        city = this.city!!,
        logo = this.logo.toLong(),
        timeInGames = TimeInGames(
            timeInQuiz = this.timeInGamesInQuiz.toLong()!!,
            timeInChat = this.timeInGamesInChat.toLong()!!,
            smsPoints = this.timeInGamesSmsPoints.toLong()!!,
            countQuestions = this.timeInGamesCountQuestions.toLong(),
            countTrueQuestion = this.timeInGamesCountTrueQuestion.toLong(),
            quizRating = this.timeInQuizRating.toLong()
        ),
        addPoints = AddPoints(
            this.addPointsGold.toLong(),
            this.addPointsSkill.toLong(),
            this.addPointsNolics.toLong(),
            this.addTrophy,
            this.addMassage
        ),
        dates = Dates(this.dataCreateAcc!!, this.dateSynch!!, this.datePremium, this.dateBanned),
        languages = this.languages!!,
        qualification = Qualification(
            this.sponsor?.toLong()!!,
            this.tester?.toLong()!!,
            this.translater.toLong()!!,
            this.moderator.toLong()!!,
            this.admin.toLong()!!,
            this.developer.toLong()!!
        ),
        life = Life(
            this.countLife.toLong()!!,
            this.countGoldLife.toLong()!!,
        ),
        box = Box(
            this.countBox.toLong()!!,
            this.timeLastOpenBox!!,
            this.countDayBox.toLong()!!
        ),
        0
    )
}

fun ProfileRemote.toProfileEntity(countGold: Int, count: Int): ProfileEntity {
    return ProfileEntity(
        id = null,
        tpovId = this.tpovId.toInt(),
        login = this.login,
        name = this.name,
        birthday = this.birthday,
        pointsGold = this.points.gold.toInt(),
        pointsSkill = this.points.skill.toInt(),
        pointsNolics = this.points.nolics.toInt(),
        datePremium = this.dates.datePremium,
        buyQuizPlace = this.buy.quizPlace.toInt(),
        buyTheme = this.buy.theme,
        buyMusic = this.buy.music,
        buyLogo = this.buy.logo,
        trophy = this.trophy,
        friends = this.friends,
        city = this.city,
        logo = this.logo.toInt(),
       timeInGamesInQuiz = this.timeInGames.timeInQuiz.toInt(),
        timeInGamesInChat = this.timeInGames.timeInChat.toInt(),
        timeInGamesSmsPoints = this.timeInGames.smsPoints.toInt(),
        addPointsGold = this.addPoints.addGold.toInt(),
        addPointsSkill = this.addPoints.addSkill.toInt(),
        addPointsNolics = this.addPoints.addNolics.toInt(),
        addTrophy = this.addPoints.addTrophy,
        addMassage = this.addPoints.addMassage,
        dataCreateAcc = this.dates.dataCreateAcc,
        dateSynch = this.dates.dateSynch,
        languages = this.languages,
        sponsor = this.qualification.sponsor.toInt(),
        tester = this.qualification.tester.toInt(),
        translater = this.qualification.translater.toInt(),
        moderator = this.qualification.moderator.toInt(),
        admin = this.qualification.admin.toInt(),
        developer = this.qualification.developer.toInt(),
        nickname = this.nickname,
        coundDayBox = this.box.coundDayBox.toInt(),
        countBox = this.box.countBox.toInt(),
        timeLastOpenBox = this.box.timeLastOpenBox,
        countGold = countGold.toInt(),
        count = count.toInt(),
        countGoldLife = this.life.countGoldLife.toInt(),
        countLife = this.life.countLife.toInt(),
        dateCloseApp = TimeManager.getCurrentTime(),
        timeInGamesCountQuestions = this.timeInGames.countQuestions?.toInt() ?: 0,
        timeInGamesCountTrueQuestion = this.timeInGames.countTrueQuestion.toInt(),
        timeInQuizRating = this.timeInGames.quizRating.toInt(),
        commander = this.comander.toInt(),
        dateBanned = this.dates.dateBanned
    )
}
