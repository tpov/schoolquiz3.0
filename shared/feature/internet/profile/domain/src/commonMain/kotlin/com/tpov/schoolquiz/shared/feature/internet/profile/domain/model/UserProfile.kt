package com.tpov.schoolquiz.shared.feature.internet.profile.domain.model

data class UserProfile(
    val uid: String,
    val nickname: String,
    val status: ProfileStatus,
    val avatarUrl: String?,
    val knownLanguages: List<String>,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val skillPoints: Int,
    val gold: Long,
    val nolics: Long,
    val standardHearts: Int,
    val goldHearts: Int,
    val activityRatings: ProfileActivityRatings = ProfileActivityRatings(),
    val qualification: ProfileQualification,
    val boxCount: Int = 0,
    val boxStreakDays: Int = 0,
    val nextBoxAtMs: Long = 0L,
    val premiumUntilMs: Long = 0L,
    /** Named badges the player holds; see TROPHY_VERIFIED in functions/trophies.js. */
    val trophies: Set<String> = emptySet(),
    val ownedLogos: List<String> = emptyList(),
    /**
     * Activity budget. A heart is a slot worth [LIFE_POINTS_PER_HEART] points, and playing costs
     * points rather than whole hearts — the model inherited from the legacy app. The server is the
     * authority: it regenerates and charges these points, the client only mirrors them.
     */
    val lifePoints: Int = standardHearts * LIFE_POINTS_PER_HEART,
    val lifePointsUpdatedAtMs: Long = 0L,
    /**
     * Плазма: [goldHearts] — сколько слотов куплено, эти очки — сколько в них накоплено.
     *
     * Раздельно по той же причине, что и у обычного заряда: одно число не может значить сразу и
     * вместимость, и остаток, а плазма восстанавливается — за сутки на заряд.
     */
    val plasmaPoints: Int = goldHearts * LIFE_POINTS_PER_HEART,
    val plasmaPointsUpdatedAtMs: Long = 0L,
    /**
     * What a person says about themselves so a human can check who they are.
     *
     * Personal data, and kept deliberately small — a name, a birthday, a city and a way to reach
     * them. It lives on the account document, which only its owner can read.
     */
    val realName: String? = null,
    val birthday: String? = null,
    val city: String? = null,
    val telegram: String? = null,
    /** When an admin or developer confirmed the identity. Zero means nobody has. */
    val verifiedAtMs: Long = 0L,
) {
    /**
     * The tick.
     *
     * Read from the trophy rather than from [status]: the badge is what the decision actually
     * grants, and deriving the mark from it means the two can never disagree.
     */
    val isVerified: Boolean get() = TROPHY_VERIFIED in trophies

    /** Ceiling: every owned heart slot holds [LIFE_POINTS_PER_HEART] points. */
    val maxLifePoints: Int get() = standardHearts * LIFE_POINTS_PER_HEART

    /** Потолок плазмы: каждый купленный слот держит [LIFE_POINTS_PER_HEART] очков. */
    val maxPlasmaPoints: Int get() = goldHearts * LIFE_POINTS_PER_HEART

    /** Сколько целых плазменных зарядов показать игроку. Дробную плазму потратить нельзя. */
    val plasmaCharges: Int get() = plasmaPoints / LIFE_POINTS_PER_HEART

    /**
     * Хватает ли на попытку, которая стоит [pricePoints].
     *
     * Цену называет вызывающий, а не эта модель: плоской цены попытки больше нет — обычный урок,
     * арена, контрольная, экзамен и турнир стоят по-разному, и прейскурант живёт в серверной
     * таблице (`EconomyConstants.priceOf`). Здесь стояло сравнение с зашитой тридцать тройкой, и
     * после прейскуранта оно означало бы «хватает на турнир», когда хватает только на урок.
     */
    fun canAfford(pricePoints: Int): Boolean = lifePoints >= pricePoints

    init {
        require(uid.isNotBlank() || status == ProfileStatus.OFFLINE) {
            "uid must be present for online profiles"
        }
        require(nickname.isNotBlank()) { "nickname must not be blank" }
        require(skillPoints >= 0) { "skillPoints must be non-negative" }
        require(gold >= 0) { "gold must be non-negative" }
        require(nolics >= 0) { "nolics must be non-negative" }
        require(standardHearts >= 0) { "standardHearts must be non-negative" }
        require(goldHearts >= 0) { "goldHearts must be non-negative" }
        require(boxCount >= 0) { "boxCount must be non-negative" }
        require(boxStreakDays >= 0) { "boxStreakDays must be non-negative" }
        require(nextBoxAtMs >= 0) { "nextBoxAtMs must be non-negative" }
        require(premiumUntilMs >= 0) { "premiumUntilMs must be non-negative" }
        require(lifePoints >= 0) { "lifePoints must be non-negative" }
        require(plasmaPoints >= 0) { "plasmaPoints must be non-negative" }
        require(plasmaPointsUpdatedAtMs >= 0) { "plasmaPointsUpdatedAtMs must be non-negative" }
        require(lifePointsUpdatedAtMs >= 0) { "lifePointsUpdatedAtMs must be non-negative" }
    }

    companion object {
        /** One heart holds this many life points. Mirrors LIFE_POINTS_PER_HEART in functions/life-points.js. */
        const val LIFE_POINTS_PER_HEART = 100

        /** Cost of one lesson attempt, from the legacy price list. */

        /** Mirrors TROPHY_VERIFIED in functions/trophies.js. */
        const val TROPHY_VERIFIED = "verified"

        fun offline(): UserProfile =
            UserProfile(
                uid = "",
                nickname = "Гость",
                status = ProfileStatus.OFFLINE,
                avatarUrl = null,
                knownLanguages = emptyList(),
                createdAtMs = 0L,
                updatedAtMs = 0L,
                skillPoints = 0,
                gold = 0L,
                nolics = 0L,
                standardHearts = 5,
                goldHearts = 0,
                qualification = ProfileQualification(),
            )
    }
}
