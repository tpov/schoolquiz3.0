package com.tpov.schoolquiz.data.fierbase

import com.google.firebase.database.IgnoreExtraProperties
import com.tpov.common.data.utils.TimeManager
import com.tpov.schoolquiz.data.database.entities.ProfileEntity

@IgnoreExtraProperties
data class Profile constructor(
    val tpovId: String,
    val login: String,
    val name: String,
    val nickname: String,
    val birthday: String,
    val points: Points,
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
    fun toHashMap(): HashMap<String, Any> {
        val result = HashMap<String, Any>()

        result["tpovId"] = tpovId
        result["login"] = login
        result["name"] = name
        result["nickname"] = nickname
        result["birthday"] = birthday
        result["points"] = hashMapOf(
            "gold" to points.gold,
            "skill" to points.skill,
            "nolics" to points.nolics
        )
        result["buy"] = hashMapOf(
            "quizPlace" to buy.quizPlace,
            "theme" to buy.theme,
            "music" to buy.music,
            "logo" to buy.logo
        )
        result["trophy"] = trophy
        result["friends"] = friends
        result["city"] = city
        result["logo"] = logo
        result["timeInGames"] = hashMapOf(
            "timeInQuiz" to timeInGames.timeInQuiz,
            "timeInChat" to timeInGames.timeInChat,
            "smsPoints" to timeInGames.smsPoints,
            "countQuestions" to timeInGames.countQuestions,
            "countTrueQuestion" to timeInGames.countTrueQuestion,
            "quizRating" to timeInGames.quizRating
        )
        result["addPoints"] = hashMapOf(
            "addGold" to addPoints.addGold,
            "addSkill" to addPoints.addSkill,
            "addNolics" to addPoints.addNolics,
            "addTrophy" to addPoints.addTrophy,
            "addMassage" to addPoints.addMassage
        )
        result["dates"] = hashMapOf(
            "dataCreateAcc" to dates.dataCreateAcc,
            "dateSynch" to dates.dateSynch,
            "datePremium" to dates.datePremium,
            "dateBanned" to dates.dateBanned
        )
        result["idFirebase"] = idFirebase
        result["languages"] = languages
        result["qualification"] = hashMapOf(
            "sponsor" to qualification.sponsor,
            "tester" to qualification.tester,
            "translater" to qualification.translater,
            "moderator" to qualification.moderator,
            "admin" to qualification.admin,
            "developer" to qualification.developer
        )
        result["life"] = hashMapOf(
            "countLife" to life.countLife,
            "countGoldLife" to life.countGoldLife
        )
        result["box"] = hashMapOf(
            "countBox" to box.countBox,
            "timeLastOpenBox" to box.timeLastOpenBox,
            "coundDayBox" to box.coundDayBox
        )
        result["comander"] = comander

        return result
    }

    fun fromHashMap(map: Map<String, Any>): Profile {
        return Profile(
            tpovId = map["tpovId"] as String? ?: "",
            login = map["login"] as String? ?: "",
            name = map["name"] as String? ?: "",
            nickname = map["nickname"] as String? ?: "",
            birthday = map["birthday"] as String? ?: "",
            points = Points(
                gold = (map["points"] as? Map<String, Any>)?.get("gold") as? Int ?: 0,
                skill = (map["points"] as? Map<String, Any>)?.get("skill") as? Int ?: 0,
                nolics = (map["points"] as? Map<String, Any>)?.get("nolics") as? Int ?: 0
            ),
            buy = Buy(
                quizPlace = (map["buy"] as? Map<String, Any>)?.get("quizPlace") as? Int ?: 0,
                theme = (map["buy"] as? Map<String, Any>)?.get("theme") as? String ?: "",
                music = (map["buy"] as? Map<String, Any>)?.get("music") as? String ?: "",
                logo = (map["buy"] as? Map<String, Any>)?.get("logo") as? String ?: ""
            ),
            trophy = map["trophy"] as String? ?: "",
            friends = map["friends"] as String? ?: "",
            city = map["city"] as String? ?: "",
            logo = map["logo"] as? Int ?: 0,
            timeInGames = TimeInGames(
                timeInQuiz = (map["timeInGames"] as? Map<String, Any>)?.get("timeInQuiz") as? Int ?: 0,
                timeInChat = (map["timeInGames"] as? Map<String, Any>)?.get("timeInChat") as? Int ?: 0,
                smsPoints = (map["timeInGames"] as? Map<String, Any>)?.get("smsPoints") as? Int ?: 0,
                countQuestions = (map["timeInGames"] as? Map<String, Any>)?.get("countQuestions") as? Int ?: 0,
                countTrueQuestion = (map["timeInGames"] as? Map<String, Any>)?.get("countTrueQuestion") as? Int ?: 0,
                quizRating = (map["timeInGames"] as? Map<String, Any>)?.get("quizRating") as? Int ?: 0
            ),
            addPoints = AddPoints(
                addGold = (map["addPoints"] as? Map<String, Any>)?.get("addGold") as? Int ?: 0,
                addSkill = (map["addPoints"] as? Map<String, Any>)?.get("addSkill") as? Int ?: 0,
                addNolics = (map["addPoints"] as? Map<String, Any>)?.get("addNolics") as? Int ?: 0,
                addTrophy = (map["addPoints"] as? Map<String, Any>)?.get("addTrophy") as? String ?: "",
                addMassage = (map["addPoints"] as? Map<String, Any>)?.get("addMassage") as? String ?: ""
            ),
            dates = Dates(
                dataCreateAcc = (map["dates"] as? Map<String, Any>)?.get("dataCreateAcc") as? String ?: "",
                dateSynch = (map["dates"] as? Map<String, Any>)?.get("dateSynch") as? String ?: "",
                datePremium = (map["dates"] as? Map<String, Any>)?.get("datePremium") as? String ?: "",
                dateBanned = (map["dates"] as? Map<String, Any>)?.get("dateBanned") as? String ?: ""
            ),
            idFirebase = map["idFirebase"] as String? ?: "",
            languages = map["languages"] as String? ?: "",
            qualification = Qualification(
                sponsor = (map["qualification"] as? Map<String, Any>)?.get("sponsor") as? Int ?: 0,
                tester = (map["qualification"] as? Map<String, Any>)?.get("tester") as? Int ?: 0,
                translater = (map["qualification"] as? Map<String, Any>)?.get("translater") as? Int ?: 0,
                moderator = (map["qualification"] as? Map<String, Any>)?.get("moderator") as? Int ?: 0,
                admin = (map["qualification"] as? Map<String, Any>)?.get("admin") as? Int ?: 0,
                developer = (map["qualification"] as? Map<String, Any>)?.get("developer") as? Int ?: 0
            ),
            life = Life(
                countLife = (map["life"] as? Map<String, Any>)?.get("countLife") as? Int ?: 0,
                countGoldLife = (map["life"] as? Map<String, Any>)?.get("countGoldLife") as? Int ?: 0
            ),
            box = Box(
                countBox = (map["box"] as? Map<String, Any>)?.get("countBox") as? Int ?: 0,
                timeLastOpenBox = (map["box"] as? Map<String, Any>)?.get("timeLastOpenBox") as? String ?: "",
                coundDayBox = (map["box"] as? Map<String, Any>)?.get("coundDayBox") as? Int ?: 0
            ),
            comander = map["comander"] as? Int ?: 0
        )
    }

    constructor() : this(
        0.toString(),
        "",
        "",
        "",
        "",
        Points(),
        Buy(),
        "",
        "",
        "",
        0,
        TimeInGames(),
        AddPoints(),
        Dates(),
        "",
        "",
        Qualification(),
        Life(),
        Box(),
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
    val quizRating: Int
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
    val dateSynch: String,
    val datePremium: String,
    val dateBanned: String,
) {
    constructor() : this(
        "", "", "", ""
    )
}

fun ProfileEntity.toProfile(): Profile {
    return Profile(
        tpovId = this.tpovId.toString(),
        login = this.login!!,
        name = this.name ?: "",
        nickname = this.nickname!!,
        birthday = this.birthday,
        points = Points(
            gold = this.pointsGold,
            skill = this.pointsSkill!!,
            nolics = this.pointsNolics!!
        ),
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
            quizRating = this.timeInQuizRating
        ),
        addPoints = AddPoints(
            this.addPointsGold ?: 0,
            this.addPointsSkill ?: 0,
            this.addPointsNolics ?: 0,
            this.addTrophy ?: "",
            this.addMassage ?: ""
        ),
        dates = Dates(this.dataCreateAcc!!, this.dateSynch!!, this.datePremium, this.dateBanned),
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
        datePremium = this.dates.datePremium,
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
        timeInGamesCountQuestions = this.timeInGames.countQuestions!!,
        timeInGamesCountTrueQuestion = this.timeInGames.countTrueQuestion,
        timeInQuizRating = this.timeInGames.quizRating,
        commander = this.comander,
        dateBanned = this.dates.dateBanned
    )
}