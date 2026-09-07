package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty

/** Persist the entire command before using it in a future request journal. Never recreate its ID on retry. */
sealed interface ArenaPrepareRequest {
    val ownerUid: String
    val requestId: String
    val difficulty: Difficulty

    data class Random(
        override val ownerUid: String,
        override val requestId: String,
        override val difficulty: Difficulty,
    ) : ArenaPrepareRequest {
        init {
            requireArenaDocumentId(ownerUid)
            requireArenaDocumentId(requestId)
        }
    }

    /** [maxPriceNolics] is explicit user consent, not a value inferred from a recovered ticket. */
    data class Selected(
        override val ownerUid: String,
        override val requestId: String,
        override val difficulty: Difficulty,
        val lessonId: String,
        val maxPriceNolics: Int,
    ) : ArenaPrepareRequest {
        init {
            requireArenaDocumentId(ownerUid)
            requireArenaDocumentId(requestId)
            requireArenaDocumentId(lessonId)
            require(maxPriceNolics in 0..MAX_PRICE_NOLICS)
        }
    }
}

data class ArenaTicketListRequest(
    val ownerUid: String,
    val afterTicketId: String? = null,
    val pageSize: Int = MAX_PAGE_SIZE,
) {
    init {
        requireArenaDocumentId(ownerUid)
        afterTicketId?.let(::requireArenaTicketId)
        require(pageSize in 1..MAX_PAGE_SIZE)
    }
}

data class ArenaTicketGetRequest(val ownerUid: String, val ticketId: String) {
    init {
        requireArenaDocumentId(ownerUid)
        requireArenaTicketId(ticketId)
    }
}

/** A known header binds refund amounts and attempt identity. It is not current launch permission. */
data class ArenaTicketCancelRequest(val ticket: ArenaTicketHeader, val requestId: String) {
    init {
        requireArenaDocumentId(requestId)
    }
}

private fun requireArenaDocumentId(value: String) {
    require(value.isNotBlank() && '/' !in value && value != "." && value != "..")
    require(value.encodeToByteArray().size <= MAX_DOCUMENT_ID_BYTES)
}

private fun requireArenaTicketId(value: String) {
    require(value.length == SHA256_HEX_LENGTH && value.all { it in '0'..'9' || it in 'a'..'f' })
}

private const val MAX_PRICE_NOLICS = 100
private const val MAX_PAGE_SIZE = 20
private const val MAX_DOCUMENT_ID_BYTES = 1_500
private const val SHA256_HEX_LENGTH = 64
