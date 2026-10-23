package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.feature.economy.domain.model.OperationHistoryRecord
import com.tpov.schoolquiz.shared.feature.economy.domain.model.OwnHistoryPage
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PendingOperation

/**
 * Разбор страницы собственной истории (AD-30, история 8.6) — строгий, по контракту O10.
 *
 * Чужая форма — отказ разбора, а не пустая страница: пустой ответ на AD-8 не превращается в
 * «подтверждённое отсутствие истории». Порядок (atMs, id) по убыванию проверяется здесь же:
 * сервер гарантирует его, и нарушение — повод отказать странице, а не показать её задом
 * наперёд.
 */
object OwnHistoryWireParser {

    fun page(reply: Any?): OwnHistoryPage {
        val map = reply.asMap()
        val entries =
            (map["entries"] as? List<*>)
                ?.mapIndexed { index, raw -> record(raw.asMap("entries[$index]")) }
                .orEmpty()
        val pending =
            (map["pending"] as? List<*>)
                ?.mapIndexed { index, raw -> pendingRow(raw.asMap("pending[$index]")) }
                .orEmpty()
        val nextCursor = (map["nextCursor"] as? String)?.takeIf { it.isNotBlank() }
        for (index in 1 until entries.size) {
            val previous = entries[index - 1]
            val current = entries[index]
            require(previous.atMs > current.atMs || previous.atMs == current.atMs && previous.id > current.id) {
                "OwnHistoryWireParser: entries must arrive newest first (atMs, id)"
            }
        }
        return OwnHistoryPage(entries = entries, pending = pending, nextCursor = nextCursor)
    }

    private fun record(map: Map<*, *>): OperationHistoryRecord =
        OperationHistoryRecord(
            id = map.string("id"),
            atMs = map.long("atMs"),
            reason = map.string("reason"),
            currency = map.string("currency"),
            delta = map.long("delta"),
            status = map.string("status"),
            lessonId = (map["lessonId"] as? String)?.takeIf { it.isNotBlank() },
        )

    private fun pendingRow(map: Map<*, *>): PendingOperation =
        PendingOperation(
            ticketId = map.string("ticketId"),
            atMs = map.long("atMs"),
            currency = map.string("currency"),
            heldAmount = map.long("heldAmount"),
        )

    private fun Any?.asMap(what: String = "reply"): Map<*, *> =
        this as? Map<*, *> ?: throw IllegalArgumentException("OwnHistoryWireParser: $what is not an object")

    private fun Map<*, *>.string(field: String): String =
        (this[field] as? String)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("OwnHistoryWireParser: $field is required")

    private fun Map<*, *>.long(field: String): Long =
        when (val raw = this[field]) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull() ?: throw IllegalArgumentException("OwnHistoryWireParser: $field is not a number")
            else -> throw IllegalArgumentException("OwnHistoryWireParser: $field is required")
        }
}
