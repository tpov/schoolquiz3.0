package com.tpov.schoolquiz.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: Int?,
    val tpovId: Int?,
    val login: String?,
    val name: String?,
    val nickname: String?,
    val birthday: String?,
    val datePremium: String?,
    val trophy: String?,
    val friends: String?,
    val city: String?,
    val logo: Int?,
    val timeInGamesAllTime: Int?,
    val timeInGamesInQuiz: Int?,
    val timeInGamesInChat: Int?,
    val timeInGamesSmsPoints: Int?,
    val pointsGold: Int?,
    val pointsSkill: Int?,
    val pointsSkillInSeason: Int?,
    val pointsNolics: Int?,
    val buyHeart: Int?,
    val buyGoldHeart: Int?,
    val buyQuizPlace: Int?,
    val buyTheme: String?,
    val buyMusic: String?,
    val buyLogo: String?,
    val addPointsGold: Int?,
    val addPointsSkill: Int?,
    val addPointsSkillInSeason: Int?,
    val addPointsNolics: Int?,
    val addTrophy: String?,

    val dataCreateAcc: String?,
    val dateSynch: String?,
    val idFirebase: String?,
    val languages: String?,

    val gamer: Int?,
    val sponsor: Int?,
    val tester: Int?,
    val translater: Int?,
    val moderator: Int?,
    val admin: Int?,
    val developer: Int?,
    val countBox: Int?,
    val timeLastOpenBox: String?,
    val coundDayBox: Int?,

    val countLife: Int?,
    val count: Int?,
    val countGoldLife: Int?,
    val countGold: Int?
)