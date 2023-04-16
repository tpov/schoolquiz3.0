package com.tpov.schoolquiz.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tpov.schoolquiz.data.fierbase.Players

@Entity(tableName = "table_players")

data class PlayersEntity(
    @PrimaryKey
    val id: Int? = null,
    val gamer: Int = 0,
    val sponsor: Int = 0,
    val tester: Int = 0,
    val translater: Int = 0,
    val moderator: Int = 0,
    val admin: Int = 0,
    val developer: Int = 0,
    val timeInGamesAllTime: Int = 0,
    val timeInGamesInQuiz: Int = 0,
    val timeInGamesInChat: Int = 0,
    val timeInGamesSmsPoints: Int = 0,
    val ratingPlayer: Int = 0,
    val ratingAnswer: Int = 0,
    val ratingQuiz: Int = 0,
    val skill: Int = 0
)