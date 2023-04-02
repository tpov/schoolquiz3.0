package com.tpov.schoolquiz.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_data")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int?,

    @ColumnInfo(name = "time")
    val time: String,

    @ColumnInfo(name = "user")
    val user: String,

    @ColumnInfo(name = "msg")
    val msg: String,

    @ColumnInfo(name = "importance")
    val importance: Int,

    @ColumnInfo(name = "personalSms")
    val personalSms: Int,
)