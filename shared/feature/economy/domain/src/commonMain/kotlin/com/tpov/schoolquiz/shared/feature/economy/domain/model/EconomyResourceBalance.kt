package com.tpov.schoolquiz.shared.feature.economy.domain.model

data class EconomyResourceBalance(
    val hasPremium: Boolean = false,
    val streakDays: Int = 0,
    val stars: Long = 0L,
    val nolics: Long = 0L,
    val standardHearts: Int = MaxStandardHearts,
    val goldHearts: Int = 0,
    val gold: Long = 0L,
) {
    companion object {
        const val MaxStandardHearts = 5
        const val MaxGoldHearts = 1

        fun guest(): EconomyResourceBalance = EconomyResourceBalance()
    }
}
