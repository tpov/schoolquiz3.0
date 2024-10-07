package com.tpov.schoolquiz.data.fierbase

import com.google.firebase.database.IgnoreExtraProperties
import com.tpov.common.data.utils.TimeManager
import com.tpov.schoolquiz.data.database.entities.ProfileEntity

@IgnoreExtraProperties
data class Profile constructor(
    val tpovId: Long,
    val login: String,
    val name: String,
    val nickname: String,
    val birthday: String,
    val points: Points,
    val buy: Buy,
    val trophy: String,
    val friends: String,
    val city: String,
    val logo: Long,
    val timeInGames: TimeInGames,
    val addPoints: AddPoints,
    val dates: Dates,
    val languages: String,
    val qualification: Qualification,
    val life: Life,
    val box: Box,
    val comander: Long
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
            tpovId = map["tpovId"] as Long? ?: 0,
            login = map["login"] as String? ?: "",
            name = map["name"] as String? ?: "",
            nickname = map["nickname"] as String? ?: "",
            birthday = map["birthday"] as String? ?: "",
            points = Points(
                gold = (map["points"] as? Map<String, Any>)?.get("gold") as? Long ?: 0,
                skill = (map["points"] as? Map<String, Any>)?.get("skill") as? Long ?: 0,
                nolics = (map["points"] as? Map<String, Any>)?.get("nolics") as? Long ?: 0
            ),
            buy = Buy(
                quizPlace = (map["buy"] as? Map<String, Any>)?.get("quizPlace") as? Long ?: 0,
                theme = (map["buy"] as? Map<String, Any>)?.get("theme") as? String ?: "",
                music = (map["buy"] as? Map<String, Any>)?.get("music") as? String ?: "",
                logo = (map["buy"] as? Map<String, Any>)?.get("logo") as? String ?: ""
            ),
            trophy = map["trophy"] as String? ?: "",
            friends = map["friends"] as String? ?: "",
            city = map["city"] as String? ?: "",
            logo = map["logo"] as? Long ?: 0,
            timeInGames = TimeInGames(
                timeInQuiz = (map["timeInGames"] as? Map<String, Any>)?.get("timeInQuiz") as? Long ?: 0,
                timeInChat = (map["timeInGames"] as? Map<String, Any>)?.get("timeInChat") as? Long ?: 0,
                smsPoints = (map["timeInGames"] as? Map<String, Any>)?.get("smsPoints") as? Long ?: 0,
                countQuestions = (map["timeInGames"] as? Map<String, Any>)?.get("countQuestions") as? Long ?: 0,
                countTrueQuestion = (map["timeInGames"] as? Map<String, Any>)?.get("countTrueQuestion") as? Long ?: 0,
                quizRating = (map["timeInGames"] as? Map<String, Any>)?.get("quizRating") as? Long ?: 0
            ),
            addPoints = AddPoints(
                addGold = (map["addPoints"] as? Map<String, Any>)?.get("addGold") as? Long ?: 0,
                addSkill = (map["addPoints"] as? Map<String, Any>)?.get("addSkill") as? Long ?: 0,
                addNolics = (map["addPoints"] as? Map<String, Any>)?.get("addNolics") as? Long ?: 0,
                addTrophy = (map["addPoints"] as? Map<String, Any>)?.get("addTrophy") as? String ?: "",
                addMassage = (map["addPoints"] as? Map<String, Any>)?.get("addMassage") as? String ?: ""
            ),
            dates = Dates(
                dataCreateAcc = (map["dates"] as? Map<String, Any>)?.get("dataCreateAcc") as? String ?: "",
                dateSynch = (map["dates"] as? Map<String, Any>)?.get("dateSynch") as? String ?: "",
                datePremium = (map["dates"] as? Map<String, Any>)?.get("datePremium") as? String ?: "",
                dateBanned = (map["dates"] as? Map<String, Any>)?.get("dateBanned") as? String ?: ""
            ),
            languages = map["languages"] as String? ?: "",
            qualification = Qualification(
                sponsor = (map["qualification"] as? Map<String, Any>)?.get("sponsor") as? Long ?: 0,
                tester = (map["qualification"] as? Map<String, Any>)?.get("tester") as? Long ?: 0,
                translater = (map["qualification"] as? Map<String, Any>)?.get("translater") as? Long ?: 0,
                moderator = (map["qualification"] as? Map<String, Any>)?.get("moderator") as? Long ?: 0,
                admin = (map["qualification"] as? Map<String, Any>)?.get("admin") as? Long ?: 0,
                developer = (map["qualification"] as? Map<String, Any>)?.get("developer") as? Long ?: 0
            ),
            life = Life(
                countLife = (map["life"] as? Map<String, Any>)?.get("countLife") as? Long ?: 0,
                countGoldLife = (map["life"] as? Map<String, Any>)?.get("countGoldLife") as? Long ?: 0
            ),
            box = Box(
                countBox = (map["box"] as? Map<String, Any>)?.get("countBox") as? Long ?: 0,
                timeLastOpenBox = (map["box"] as? Map<String, Any>)?.get("timeLastOpenBox") as? String ?: "",
                coundDayBox = (map["box"] as? Map<String, Any>)?.get("coundDayBox") as? Long ?: 0
            ),
            comander = map["comander"] as? Long ?: 0
        )
    }

    constructor() : this(
        0L,
        "",
        "",
        "",
        "",
        Points(),
        Buy(),
        "",
        "",
        "",
        0L,
        TimeInGames(),
        AddPoints(),
        Dates(),
        "",
        Qualification(),
        Life(),
        Box(),
        0L
    )
}

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
    val coundDayBox: Long
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
    val nolics: Long
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

fun ProfileEntity.toProfile(): Profile {
    return Profile(
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
            this.coundDayBox.toLong()!!
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