package com.tpov.schoolquiz.data.fierbase

data class Chat(
    val time: String,
    val user: String,
    val msg: String,
    val importance: Int,
    val personalSms: Int
)