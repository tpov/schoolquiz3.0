package com.tpov.network.data.models.remote

import com.tpov.network.data.models.local.ChatEntity

data class ChatRemote(
    val time: String = "",
    val user: String = "",
    val msg: String = "",
    val importance: Int = 0,
    val icon: Int = 0,
    val rating: Int = 0,
    val reaction: String = "",
    val tpovId: Int = 0
) {
    fun toChatEntity() = ChatEntity(
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
