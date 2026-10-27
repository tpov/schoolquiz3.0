package com.tpov.schoolquiz.platform.firebase.economy

import com.tpov.schoolquiz.shared.feature.economy.data.remote.GiftBoxReceipt
import com.tpov.schoolquiz.shared.feature.economy.data.remote.GiftBoxReceiptStatus
import com.tpov.schoolquiz.shared.feature.economy.data.remote.GiftBoxRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxReward
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/** Typed reader for the persisted callable wire. This adapter no longer performs a second RPC. */
class FirebaseGiftBoxRemoteDataSource : GiftBoxRemoteDataSource {
    override fun openingFromReceipt(
        payloadJson: String,
        ownerUid: String,
        openingId: String,
    ): GiftBoxReceipt {
        val envelope = Json.parseToJsonElement(payloadJson) as? JsonObject ?: error("Missing envelope")
        val replayed = envelope["replayed"] as? JsonPrimitive ?: error("Missing replay marker")
        check(!replayed.isString && replayed.booleanOrNull != null)
        val stored = envelope.objectAt("result")
        // Completion-записи OPEN_GIFT_BOX живут в двух формах (retro e7 F6): записи до серверной
        // правки конверта (c2c1a414) хранили в `result` весь ПЛАН с вложенным `response`; новые
        // записи — плоское тело ответа (плоский ответ не содержит ключа `response`, так что
        // развязка однозначная). Читаются обе — лежащий пре-конвертный ключ доигрывает доставку,
        // а не падает RECEIPT_INVALID навсегда; новые записи пишутся только плоскими.
        val result = stored["response"] as? JsonObject ?: stored
        check(result.number("schemaVersion") == 1L)
        check(result.text("ownerUid") == ownerUid && result.text("operationId") == openingId)
        check(result.text("kind") == "OPEN")
        val status = GiftBoxReceiptStatus.valueOf(result.text("status"))
        result.number("boxCount")
        result.number("earnedBoxes")
        result.number("boxStreakDays")
        result.number("nextBoxAtMs")
        result.number("nextBonusBoxAtMs")
        result.number("premiumUntilMs")
        val reward =
            when (status) {
                GiftBoxReceiptStatus.OPENED -> {
                    check(result.number("openedBoxes") == 1L)
                    result.reward()
                }
                GiftBoxReceiptStatus.NO_BOXES -> {
                    check(result.number("openedBoxes") == 0L && result.number("boxCount") == 0L)
                    check(!result.containsKey("reward"))
                    null
                }
            }
        return GiftBoxReceipt(openingId, ownerUid, status, reward)
    }

    private fun JsonObject.reward(): GiftBoxReward {
        val reward = objectAt("reward")
        val amount = reward.number("amount").also { check(it > 0) }
        return when (reward.text("type")) {
            "addNolics" -> GiftBoxReward.Nolics(amount)
            "addGold" -> {
                val credit = objectAt("goldCredit")
                val gross = credit.number("gross")
                val net = credit.number("net")
                val cleared = credit.number("shortfallCleared")
                check(gross == amount && net <= gross && cleared == gross - net)
                check(credit.number("goldAfter") - credit.number("goldBefore") == net)
                check(credit.number("shortfallBefore") - credit.number("shortfallAfter") == cleared)
                // The overlay says "credited": debt-cleared gold is not spendable gold.
                GiftBoxReward.Gold(net)
            }
            "datePremium" -> GiftBoxReward.Premium(amount)
            "logo" -> GiftBoxReward.Logo(reward.text("itemName"), amount)
            "trophy" -> {
                reward.text("itemName")
                GiftBoxReward.Trophy(amount)
            }
            else -> error("Unsupported gift box reward")
        }
    }

    private fun JsonObject.objectAt(key: String) = get(key) as? JsonObject ?: error("Missing $key")

    private fun JsonObject.text(key: String): String {
        val value = get(key) as? JsonPrimitive ?: error("Missing $key")
        check(value.isString && value.content.isNotBlank())
        return value.content
    }

    private fun JsonObject.number(key: String): Long {
        val value = get(key) as? JsonPrimitive ?: error("Missing $key")
        check(!value.isString)
        return checkNotNull(value.longOrNull).also { check(it in 0..MAX_SAFE_INTEGER) }
    }

    private companion object {
        const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
    }
}
