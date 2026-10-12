@file:Suppress("ktlint:standard:max-line-length")

package com.tpov.schoolquiz.android.feature.economy.presentation.view

import com.tpov.schoolquiz.android.feature.economy.presentation.R
import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyReceipt

/**
 * Словарь «ключ сервера → строка ресурса с порядком параметров» (история 5.4).
 *
 * Текст квитанции собирает устройство на языке игрока: сервер присылает ключ и параметры, а
 * порядок слов знает только перевод. Незнакомый ключ — не падение: общая строка с суммой честно
 * говорит «начислено», пока перевод новой причины не доехал с релизом.
 */
internal data class ReceiptText(val resId: Int, val paramOrder: List<String> = emptyList())

private val BOX_OPENED = ReceiptText(R.string.receipt_box_opened)
private val BOX_ACCRUAL = ReceiptText(R.string.receipt_box_accrual)
private val GOLD_PACK_CREDITED = ReceiptText(R.string.receipt_gold_pack_credited, listOf("gold"))
private val PREMIUM_CREDITED = ReceiptText(R.string.receipt_premium_credited, listOf("days"))
private val BOX_PACK_CREDITED = ReceiptText(R.string.receipt_box_pack_credited, listOf("boxes"))
private val GOLD_PACK_REFUNDED = ReceiptText(R.string.receipt_gold_pack_refunded, listOf("gold"))
private val PREMIUM_REFUNDED = ReceiptText(R.string.receipt_premium_refunded, listOf("days"))
private val SEASON_LEADERBOARD = ReceiptText(R.string.receipt_season_leaderboard, listOf("rank", "boxes"))
private val SEASON_REFERRAL_PERCENT = ReceiptText(R.string.receipt_season_referral_percent, listOf("boxes", "openings"))
private val REFERRAL_BONUS = ReceiptText(R.string.receipt_referral_bonus, listOf("invitees", "each", "boxes"))

private val RECEIPT_TEXTS: Map<String, ReceiptText> =
    mapOf(
        "receipt.box_opened" to BOX_OPENED,
        "receipt.box_accrual" to BOX_ACCRUAL,
        "receipt.gold_pack_credited" to GOLD_PACK_CREDITED,
        "receipt.premium_credited" to PREMIUM_CREDITED,
        "receipt.box_pack_credited" to BOX_PACK_CREDITED,
        "receipt.gold_pack_refunded" to GOLD_PACK_REFUNDED,
        "receipt.premium_refunded" to PREMIUM_REFUNDED,
        "receipt.season_leaderboard" to SEASON_LEADERBOARD,
        "receipt.season_referral_percent" to SEASON_REFERRAL_PERCENT,
        "receipt.referral_bonus" to REFERRAL_BONUS,
    )

/** Турнирные квитанции называются по виду движения: `receipt.tournament_<kind>` без параметров. */
private const val TOURNAMENT_KEY_PREFIX = "receipt.tournament_"

/**
 * Человеческое объяснение квитанции: [resolve] доставляет строку ресурса с подставленными
 * параметрами (контекст и локаль знает вызывающий экран).
 */
internal fun receiptText(
    receipt: EconomyReceipt,
    resolve: (resId: Int, args: List<String>) -> String,
): String {
    val entry = RECEIPT_TEXTS[receipt.messageKey]
    return when {
        entry != null -> resolve(entry.resId, entry.paramOrder.map { receipt.params[it] ?: "" })
        receipt.messageKey.startsWith(TOURNAMENT_KEY_PREFIX) ->
            resolve(R.string.receipt_tournament, listOf(receipt.amount.toString()))
        else -> resolve(R.string.receipt_generic, listOf(receipt.amount.toString(), receipt.currency))
    }
}
