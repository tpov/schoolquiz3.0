package com.tpov.network.data.models.remote

import com.tpov.network.data.models.local.ChatEntity

data class Chat(
    val time: String,
    val user: String,
    val msg: String,
    val importance: Int,
    val icon: String,
    val rating: Int,
    val reaction: Int,
    val tpovId: Int
) {
    constructor() : this(
        time = "",
        user = "",
        msg = "",
        importance = 0,
        icon = "",
        rating = 0,
        reaction = 0,
        tpovId = 0
    )
}

fun Chat.toChatEntity(): ChatEntity {
    return ChatEntity(
        id = null,
        time = time,
        user = user,
        msg = msg,
        importance = importance,
        icon = icon,
        rating = rating,
        reaction = reaction,
        tpovId = tpovId
    )
}
