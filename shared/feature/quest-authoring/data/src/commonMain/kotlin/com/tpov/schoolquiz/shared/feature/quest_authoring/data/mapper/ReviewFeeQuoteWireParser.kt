package com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.ReviewFeeQuote

/**
 * Строгий декодер конверта цитаты платы `review-fee-quote-v1` (история 4.11).
 *
 * Отсутствующие обязательные поля и чужие версии конверта цитату не строят: цена на экране
 * обязана прийти ровно из той редакции расчёта, под которую написан клиент.
 */
object ReviewFeeQuoteWireParser {
    const val VIEW_VERSION = "review-fee-quote-v1"
    const val TARIFF_INITIAL = "initial"
    const val TARIFF_RECHECK = "recheck"

    fun reviewFeeQuote(data: Any?): ReviewFeeQuote {
        val root = data as? Map<*, *> ?: error("Fee quote response is not an object")
        check(root.string(FIELD_VIEW) == VIEW_VERSION) { "Unsupported fee quote view version" }
        val priorCycle = root[FIELD_PRIOR_CYCLE] as? Boolean ?: error("Field $FIELD_PRIOR_CYCLE is not a boolean")
        val tariff = root.string(FIELD_TARIFF)
        check(tariff == TARIFF_INITIAL || tariff == TARIFF_RECHECK) { "Field $FIELD_TARIFF is not a known tariff" }
        return ReviewFeeQuote(
            priorCycle = priorCycle,
            tariff = requireNotNull(tariff),
            feeNolics = root.unsignedInt(FIELD_FEE),
            tariffRevision = root.unsignedInt(FIELD_REVISION),
        )
    }

    private const val FIELD_VIEW = "view"
    private const val FIELD_PRIOR_CYCLE = "priorCycle"
    private const val FIELD_TARIFF = "tariff"
    private const val FIELD_FEE = "feeNolics"
    private const val FIELD_REVISION = "tariffRevision"

    private fun Map<*, *>.string(key: String): String? = this[key] as? String

    private fun Map<*, *>.unsignedInt(key: String): Int {
        val value = this[key] as? Long ?: this[key] as? Int ?: error("Field $key is not an integer")
        check(value in 0..Int.MAX_VALUE.toLong()) { "Field $key is out of range" }
        return value.toInt()
    }
}
