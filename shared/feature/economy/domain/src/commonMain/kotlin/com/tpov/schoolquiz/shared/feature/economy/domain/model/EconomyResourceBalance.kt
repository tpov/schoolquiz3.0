package com.tpov.schoolquiz.shared.feature.economy.domain.model

data class EconomyResourceBalance(
    val hasPremium: Boolean = false,
    val streakDays: Int = 0,
    val stars: Long = 0L,
    val nolics: Long = 0L,
    val standardHearts: Int = MaxStandardHearts,
    val goldHearts: Int = 0,
    val gold: Long = 0L,
    /**
     * Lessons opened with nolics, keyed "kind:lessonId".
     *
     * Kept with the balance rather than on its own because it is written by the same purchase
     * and arrives on the same profile document — a separate sync path for one set of strings
     * would be a second thing to keep in step.
     */
    val lessonUnlocks: Set<String> = emptySet(),
) {
    companion object {
        const val MaxStandardHearts = 5
        const val MaxGoldHearts = 1

        fun guest(): EconomyResourceBalance = EconomyResourceBalance()
    }
}
