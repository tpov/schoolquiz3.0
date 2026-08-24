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
)

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
