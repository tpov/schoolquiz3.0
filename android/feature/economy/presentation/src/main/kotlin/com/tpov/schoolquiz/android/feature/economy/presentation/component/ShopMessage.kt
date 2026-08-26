package com.tpov.schoolquiz.android.feature.economy.presentation.component

/**
 * What the shop may announce in its message line.
 *
 * Neutral data rather than finished sentences: wording is a screen concern, so the component only
 * says what happened and lets the composable pick the words — including which language they are.
 * [Notice] carries text handed over from the purchasing layer below because its wording is not
 * ours; [Failure] carries the raw platform detail, with the screen supplying the fallback line.
 */
sealed interface ShopMessage {
    /** A claimed name: gold was charged, or zero for the free first claim. */
    data class NicknameClaimed(val charged: Long) : ShopMessage

    data class NicknameWorn(val nickname: String) : ShopMessage

    data class NicknameListed(val price: Long) : ShopMessage

    data object ListingCancelled : ShopMessage

    data class NicknameBought(val commission: Long) : ShopMessage

    data class LogoPurchased(val charged: Long) : ShopMessage

    /** The shelf itself cannot be reached. */
    data object ShopUnavailable : ShopMessage

    data class Notice(val text: String) : ShopMessage

    data class Failure(val detail: String?) : ShopMessage
}
