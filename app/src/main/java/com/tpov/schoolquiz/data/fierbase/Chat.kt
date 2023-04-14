package com.tpov.schoolquiz.data.fierbase

import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity

data class Chat(
    val time: String,
    val user: String,
    val msg: String,
    val importance: Int,
    val personalSms: Int,
    val icon: String
) {
    constructor() : this(
        time = "",
        user = "",
        msg = "",
        importance = 0,
        personalSms = 0,
        icon = "",
    )
}

fun Chat.toChatEntity(): ChatEntity {
    return ChatEntity(
        id = null,
        time = time,
        user = user,
        msg = msg,
        importance = importance,
        personalSms = personalSms,
        icon = icon
    )
}
