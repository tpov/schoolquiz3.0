package com.tpov.schoolquiz.data.fierbase

// будет серверная функция которая проходитт по всем рефералам и обновляет открытых боксов за сезон начисляя награды командирам
data class ReferalRemote(
    val name: String,
    val icon: String,
    val allOpenBox: Int,
    val openBoxInSeason: Int
)
