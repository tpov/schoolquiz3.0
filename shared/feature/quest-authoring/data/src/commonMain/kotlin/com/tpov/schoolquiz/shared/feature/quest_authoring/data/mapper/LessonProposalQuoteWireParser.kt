package com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper

import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonProposalFeeQuote

/**
 * Строгий декодер конверта цитаты платы предложения `lesson-proposal-fee-quote-v1` (4.21, UX-23).
 *
 * Чужие версии конверта цитату не строят: цена на экране обязана прийти ровно из той редакции
 * расчёта, под которую написан клиент.
 */
object LessonProposalQuoteWireParser {
    const val VIEW_VERSION = "lesson-proposal-fee-quote-v1"

    fun lessonProposalFeeQuote(data: Any?): LessonProposalFeeQuote {
        val root = data as? Map<*, *> ?: error("Proposal fee quote response is not an object")
        check(root.string(FIELD_VIEW) == VIEW_VERSION) { "Unsupported proposal fee quote view version" }
        val kind = root.string(FIELD_KIND)
        check(!kind.isNullOrBlank()) { "Field $FIELD_KIND is missing" }
        return LessonProposalFeeQuote(
            kind = kind,
            feeNolics = root.unsignedInt(FIELD_FEE),
            tariffRevision = root.unsignedInt(FIELD_REVISION),
        )
    }

    private const val FIELD_VIEW = "view"
    private const val FIELD_KIND = "kind"
    private const val FIELD_FEE = "feeNolics"
    private const val FIELD_REVISION = "tariffRevision"

    private fun Map<*, *>.string(key: String): String? = this[key] as? String

    private fun Map<*, *>.unsignedInt(key: String): Int {
        val value = this[key] as? Long ?: this[key] as? Int ?: error("Field $key is not an integer")
        check(value in 0..Int.MAX_VALUE.toLong()) { "Field $key is out of range" }
        return value.toInt()
    }
}
