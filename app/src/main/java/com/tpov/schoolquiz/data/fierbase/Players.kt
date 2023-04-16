package com.tpov.schoolquiz.data.fierbase

import com.tpov.schoolquiz.data.database.entities.PlayersEntity


data class Players(
    val points: Points2,
    val qualification: Qualification2,
    val timeInGames: TimeInGames2
) {
    constructor() : this(Points2(), Qualification2(), TimeInGames2())
}

data class Points2(
    val skill: Int = 0
)

data class Qualification2(
    val admin: Int = 0,
    val developer: Int = 0,
    val moderator: Int = 0,
    val tester: Int = 0,
    val translater: Int = 0,
)

data class TimeInGames2(
    val allTime: Int = 0,
    val smsPoints: Int = 0,
    val timeInChat: Int = 0,
    val timeInQuiz: Int = 0
)

fun Players.toPlayersEntity(): PlayersEntity {
    return PlayersEntity(
        gamer = this.points.skill,
        sponsor = this.qualification.admin,
        tester = this.qualification.tester,
        translater = this.qualification.translater,
        moderator = this.qualification.moderator,
        admin = this.qualification.admin,
        developer = this.qualification.developer,
        timeInGamesAllTime = this.timeInGames.allTime,
        timeInGamesInQuiz = this.timeInGames.timeInQuiz,
        timeInGamesInChat = this.timeInGames.timeInChat,
        timeInGamesSmsPoints = this.timeInGames.smsPoints,
        skill = this.points.skill
    )
}
