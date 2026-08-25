package com.tpov.schoolquiz.shared.feature.internet.profile.domain.model

/**
 * The answer to "can I have this name", asked while somebody types.
 *
 * It is a hint and not a reservation: another account can claim the name between this answer and
 * the save, so the screen has to survive being refused after showing a tick.
 */
data class NicknameAvailability(
    val nickname: String,
    val available: Boolean,
    val reason: NicknameRejection?,
    /**
     * Gold this name would cost, as the server reckons it. Zero while the free choice is unspent.
     *
     * Answered by the server rather than worked out here: the price and the allowance both live in
     * policy, and a client that derives them shows the wrong number the moment either changes.
     */
    val price: Long = 0L,
    /** How short the name is, as the shop labels it. */
    val tier: NicknameTier = NicknameTier.COMMON,
    /**
     * The name worn by whoever holds this one, when somebody else does.
     *
     * A taken name is shown rather than hidden — a market where every taken name vanishes reads as
     * empty, when in fact it is the opposite.
     */
    val holder: String? = null,
)

/** Rarity, which here is only a word for how short a name is. */
enum class NicknameTier {
    LEGENDARY,
    RARE,
    COMMON,
    ;

    companion object {
        fun fromCode(code: String?): NicknameTier =
            when (code?.lowercase()) {
                "legendary" -> LEGENDARY
                "rare" -> RARE
                else -> COMMON
            }
    }
}

/** Why a name was refused. Stable codes, so the wording stays a UI decision. */
enum class NicknameRejection {
    TOO_SHORT,
    TOO_LONG,
    UNSUPPORTED_CHARACTERS,
    BLOCKED_SYMBOL,
    BLOCKED_WORD,
    TAKEN,

    /** Free, because it is already yours — the one refusal that still means "go ahead". */
    YOURS,

    ;

    companion object {
        fun fromCode(code: String?): NicknameRejection? =
            when (code) {
                "too-short" -> TOO_SHORT
                "too-long" -> TOO_LONG
                "unsupported-characters" -> UNSUPPORTED_CHARACTERS
                "blocked-symbol" -> BLOCKED_SYMBOL
                "blocked-word" -> BLOCKED_WORD
                "taken" -> TAKEN
                "yours" -> YOURS
                else -> null
            }
    }
}
