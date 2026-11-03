package com.tpov.network.data.models.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_data")
data class
ChatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int?,
    val tpovId: Int,
    val time: String,
    val user: String,
    val msg: String,
    val importance: Int,
    val icon: Int,
    val rating: Int,
    val reaction: String
)

