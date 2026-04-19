package com.tpov.schoolquiz.data.fierbase

import com.google.firebase.database.IgnoreExtraProperties
import com.tpov.schoolquiz.data.database.entities.ProfileEntity

@IgnoreExtraProperties
data class ProfileRemote constructor(
    val basic: Basic = Basic(),
    val points: Points = Points(),
    val buy: Buy = Buy(),
    val timeInGames: TimeInGames = TimeInGames(),
    val addPoints: AddPoints = AddPoints(),
    val dates: Dates = Dates(),
    val qualification: Qualification = Qualification(),
    val life: Life = Life(),
    val box: Box = Box(),
    val authUid: String? = null
)

@IgnoreExtraProperties
data class Basic(
    val tpovId: Long = 0L,
    val login: String = "",
    val name: String = "",
    val nickname: String = "",
    val birthday: String = "",
    val city: String = "",
    val languages: String = "",
    val comander: Long = 0L,
    val logo: Long = 0L,
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
        0L,  0L, 0L, "",""
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
        basic = Basic(
            tpovId = this.tpovId?.toLong() ?: 0L,
            login = this.login ?: "",
            name = this.name,
            nickname = this.nickname ?: "",
            birthday = this.birthday,
            city = this.city ?: "",
            languages = this.languages ?: "",
            comander = this.commander.toLong(),
            logo = this.logo.toLongOrNull() ?: 0L,
        ),
        points = Points(
            gold = this.pointsGold.toLong(),
            skill = this.pointsSkill.toLong(),
            nolics = this.pointsNolics.toLong(),
            trophy = this.trophy ?: "",
            friends = this.friends ?: "",
        ),
        buy = Buy(
            quizPlace = this.buyQuizPlace.toLong(),
            theme = this.buyTheme,
            music = this.buyMusic,
            logo = this.buyLogo,
        ),
        timeInGames = TimeInGames(
            timeInQuiz = this.timeInGamesInQuiz.toLong(),
            timeInChat = this.timeInGamesInChat.toLong(),
            smsPoints = this.smsCount.toLong(),
            countQuestions = this.countAnswer.toLong(),
            countTrueQuestion = this.countTrueAnswer.toLong(),
            quizRating = this.timeInQuizRating.toLong(),
        ),
        addPoints = AddPoints(
            this.addPointsGold.toLong(),
            this.addPointsSkill.toLong(),
            this.addPointsNolics.toLong(),
            this.addTrophy,
            this.addMassage,
        ),
        dates = Dates(this.dataCreateAcc ?: "", this.dateSynch, this.datePremium, this.dateBanned),
        qualification = Qualification(
            this.sponsor?.toLong() ?: 0L,
            this.tester?.toLong() ?: 0L,
            this.translater.toLong(),
            this.moderator.toLong(),
            this.admin.toLong(),
            this.developer.toLong(),
        ),
        life = Life(
            this.standardHearts.toLong(),
            this.goldHearts.toLong(),
        ),
        box = Box(
            this.countBox.toLong(),
            this.timeLastOpenBox ?: "",
            this.countDayBox.toLong(),
        ),
        authUid = this.authUid
    )
}

fun ProfileRemote.toProfileEntity(): ProfileEntity {
    return ProfileEntity(
        id = null,
        tpovId = this.basic.tpovId.toInt(),
        login = this.basic.login,
        name = this.basic.name,
        nickname = this.basic.nickname,
        birthday = this.basic.birthday,
        pointsGold = this.points.gold.toInt(),
        pointsSkill = this.points.skill.toInt(),
        pointsNolics = this.points.nolics.toInt(),
        datePremium = this.dates.datePremium,
        buyQuizPlace = this.buy.quizPlace.toInt(),
        buyTheme = this.buy.theme,
        buyMusic = this.buy.music,
        buyLogo = this.buy.logo,
        trophy = this.points.trophy,
        friends = this.points.friends,
        city = this.basic.city,
        logo = this.basic.logo.toString(),
        commander = this.basic.comander.toInt(),
        timeInGamesInQuiz = this.timeInGames.timeInQuiz.toInt(),
        timeInGamesInChat = this.timeInGames.timeInChat.toInt(),
        smsCount = this.timeInGames.smsPoints.toInt(),
        countAnswer = this.timeInGames.countQuestions?.toInt() ?: 0,
        countTrueAnswer = this.timeInGames.countTrueQuestion.toInt(),
        timeInQuizRating = this.timeInGames.quizRating.toInt(),
        addPointsGold = this.addPoints.addGold.toInt(),
        addPointsSkill = this.addPoints.addSkill.toInt(),
        addPointsNolics = this.addPoints.addNolics.toInt(),
        addTrophy = this.addPoints.addTrophy,
        addMassage = this.addPoints.addMassage,
        dataCreateAcc = this.dates.dataCreateAcc,
        dateSynch = this.dates.dateSynch,
        dateCloseApp = "",
        languages = this.basic.languages,
        status = 0,
        sponsor = this.qualification.sponsor.toInt(),
        tester = this.qualification.tester.toInt(),
        translater = this.qualification.translater.toInt(),
        moderator = this.qualification.moderator.toInt(),
        admin = this.qualification.admin.toInt(),
        developer = this.qualification.developer.toInt(),
        countBox = this.box.countBox.toInt(),
        timeLastOpenBox = this.box.timeLastOpenBox,
        countDayBox = this.box.countDayBox.toInt(),
        launchCount = 0,
        standardLife = this.life.countLife.toInt(),
        standardHearts = this.life.countGoldLife.toInt(),
        goldLife = 0,
        goldHearts = 0,
        authUid = this.authUid
    )
}

// Новый метод для преобразования Map<String, Any> в ProfileRemote
fun fromHashMap(data: Map<String, Any>): ProfileRemote {
    // Получаем вложенные карты для каждого раздела
    val basicMap = data["basic"] as? Map<String, Any> ?: emptyMap()
    val pointsMap = data["points"] as? Map<String, Any> ?: emptyMap()
    val buyMap = data["buy"] as? Map<String, Any> ?: emptyMap()
    val timeInGamesMap = data["timeInGames"] as? Map<String, Any> ?: emptyMap()
    val addPointsMap = data["addPoints"] as? Map<String, Any> ?: emptyMap()
    val datesMap = data["dates"] as? Map<String, Any> ?: emptyMap()
    val qualificationMap = data["qualification"] as? Map<String, Any> ?: emptyMap()
    val lifeMap = data["life"] as? Map<String, Any> ?: emptyMap()
    val boxMap = data["box"] as? Map<String, Any> ?: emptyMap()

    return ProfileRemote(
        basic = Basic(
            tpovId = basicMap["tpovId"] as? Long ?: 0L,
            login = basicMap["login"] as? String ?: "",
            name = basicMap["name"] as? String ?: "",
            nickname = basicMap["nickname"] as? String ?: "",
            birthday = basicMap["birthday"] as? String ?: "",
            city = basicMap["city"] as? String ?: "",
            languages = basicMap["languages"] as? String ?: "",
            comander = basicMap["comander"] as? Long ?: 0L,
            logo = basicMap["logo"] as? Long ?: 0L
        ),
        points = Points(
            gold = pointsMap["gold"] as? Long ?: 0L,
            skill = pointsMap["skill"] as? Long ?: 0L,
            nolics = pointsMap["nolics"] as? Long ?: 0L,
            trophy = pointsMap["trophy"] as? String ?: "",
            friends = pointsMap["friends"] as? String ?: ""
        ),
        buy = Buy(
            quizPlace = buyMap["quizPlace"] as? Long ?: 0L,
            theme = buyMap["theme"] as? String ?: "",
            music = buyMap["music"] as? String ?: "",
            logo = buyMap["logo"] as? String ?: ""
        ),
        timeInGames = TimeInGames(
            timeInQuiz = timeInGamesMap["timeInQuiz"] as? Long ?: 0L,
            timeInChat = timeInGamesMap["timeInChat"] as? Long ?: 0L,
            smsPoints = timeInGamesMap["smsPoints"] as? Long ?: 0L,
            countQuestions = timeInGamesMap["countQuestions"] as? Long ?: 0L,
            countTrueQuestion = timeInGamesMap["countTrueQuestion"] as? Long ?: 0L,
            quizRating = timeInGamesMap["quizRating"] as? Long ?: 0L
        ),
        addPoints = AddPoints(
            addGold = addPointsMap["addGold"] as? Long ?: 0L,
            addSkill = addPointsMap["addSkill"] as? Long ?: 0L,
            addNolics = addPointsMap["addNolics"] as? Long ?: 0L,
            addTrophy = addPointsMap["addTrophy"] as? String ?: "",
            addMassage = addPointsMap["addMassage"] as? String ?: ""
        ),
        dates = Dates(
            dataCreateAcc = datesMap["dataCreateAcc"] as? String ?: "",
            dateSynch = datesMap["dateSynch"] as? String ?: "",
            datePremium = datesMap["datePremium"] as? String ?: "",
            dateBanned = datesMap["dateBanned"] as? String ?: ""
        ),
        qualification = Qualification(
            sponsor = qualificationMap["sponsor"] as? Long ?: 0L,
            tester = qualificationMap["tester"] as? Long ?: 0L,
            translater = qualificationMap["translater"] as? Long ?: 0L,
            moderator = qualificationMap["moderator"] as? Long ?: 0L,
            admin = qualificationMap["admin"] as? Long ?: 0L,
            developer = qualificationMap["developer"] as? Long ?: 0L
        ),
        life = Life(
            countLife = lifeMap["countLife"] as? Long ?: 0L,
            countGoldLife = lifeMap["countGoldLife"] as? Long ?: 0L
        ),
        box = Box(
            countBox = boxMap["countBox"] as? Long ?: 0L,
            timeLastOpenBox = boxMap["timeLastOpenBox"] as? String ?: "",
            countDayBox = boxMap["countDayBox"] as? Long ?: 0L
        ),
        authUid = data["authUid"] as? String
    )
}

// Метод для преобразования ProfileRemote в HashMap для Firestore
fun ProfileRemote.toHashMap(): Map<String, Any> {
    val result = HashMap<String, Any>()

    // Basic
    val basicMap = HashMap<String, Any>()
    basicMap["tpovId"] = this.basic.tpovId
    basicMap["login"] = this.basic.login
    basicMap["name"] = this.basic.name
    basicMap["nickname"] = this.basic.nickname
    basicMap["birthday"] = this.basic.birthday
    basicMap["city"] = this.basic.city
    basicMap["languages"] = this.basic.languages
    basicMap["comander"] = this.basic.comander
    basicMap["logo"] = this.basic.logo
    result["basic"] = basicMap

    // Points
    val pointsMap = HashMap<String, Any>()
    pointsMap["gold"] = this.points.gold
    pointsMap["skill"] = this.points.skill
    pointsMap["nolics"] = this.points.nolics
    pointsMap["trophy"] = this.points.trophy
    pointsMap["friends"] = this.points.friends
    result["points"] = pointsMap

    // Buy
    val buyMap = HashMap<String, Any>()
    buyMap["quizPlace"] = this.buy.quizPlace
    buyMap["theme"] = this.buy.theme
    buyMap["music"] = this.buy.music
    buyMap["logo"] = this.buy.logo
    result["buy"] = buyMap

    // TimeInGames
    val timeInGamesMap = HashMap<String, Any>()
    timeInGamesMap["timeInQuiz"] = this.timeInGames.timeInQuiz
    timeInGamesMap["timeInChat"] = this.timeInGames.timeInChat
    timeInGamesMap["smsPoints"] = this.timeInGames.smsPoints
    timeInGamesMap["countQuestions"] = this.timeInGames.countQuestions ?: 0L
    timeInGamesMap["countTrueQuestion"] = this.timeInGames.countTrueQuestion
    timeInGamesMap["quizRating"] = this.timeInGames.quizRating
    result["timeInGames"] = timeInGamesMap

    // AddPoints
    val addPointsMap = HashMap<String, Any>()
    addPointsMap["addGold"] = this.addPoints.addGold
    addPointsMap["addSkill"] = this.addPoints.addSkill
    addPointsMap["addNolics"] = this.addPoints.addNolics
    addPointsMap["addTrophy"] = this.addPoints.addTrophy
    addPointsMap["addMassage"] = this.addPoints.addMassage
    result["addPoints"] = addPointsMap

    // Dates
    val datesMap = HashMap<String, Any>()
    datesMap["dataCreateAcc"] = this.dates.dataCreateAcc
    datesMap["dateSynch"] = this.dates.dateSynch
    datesMap["datePremium"] = this.dates.datePremium
    datesMap["dateBanned"] = this.dates.dateBanned
    result["dates"] = datesMap

    // Qualification
    val qualificationMap = HashMap<String, Any>()
    qualificationMap["sponsor"] = this.qualification.sponsor
    qualificationMap["tester"] = this.qualification.tester
    qualificationMap["translater"] = this.qualification.translater
    qualificationMap["moderator"] = this.qualification.moderator
    qualificationMap["admin"] = this.qualification.admin
    qualificationMap["developer"] = this.qualification.developer
    result["qualification"] = qualificationMap

    // Life
    val lifeMap = HashMap<String, Any>()
    lifeMap["countLife"] = this.life.countLife
    lifeMap["countGoldLife"] = this.life.countGoldLife
    result["life"] = lifeMap

    // Box
    val boxMap = HashMap<String, Any>()
    boxMap["countBox"] = this.box.countBox
    boxMap["timeLastOpenBox"] = this.box.timeLastOpenBox
    boxMap["countDayBox"] = this.box.countDayBox
    result["box"] = boxMap

    return result
}
