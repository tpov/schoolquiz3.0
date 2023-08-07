package com.tpov.schoolquiz.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "table_players")

data class PlayersEntity(
    @PrimaryKey
    val id: Int? = null,
    val sponsor: Int = 0,
    val tester: Int = 0,
    val translater: Int = 0,
    val moderator: Int = 0,
    val admin: Int = 0,
    val developer: Int = 0,
    val skill: Int = 0,
    val ratingTimeInQuiz: Int = 0,
    val ratingTimeInChat: Int = 0,
    val ratingSmsPoints: Int = 0,
    val ratingCountQuestions: Int = 0,
    val ratingCountTrueQuestion: Int = 0,
    val ratingQuiz: Int = 0,
    val userName: String = ""
)