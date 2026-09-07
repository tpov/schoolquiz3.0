package com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote

import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.KotlinxSerializationQuestionContentParser
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
import com.tpov.schoolquiz.shared.core.question_schema.RedactedQuestionContent
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancelResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancellationReceipt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryTicket
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaSettlementReceipt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketCancelRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketContentContext
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketHeader
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketListRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketSnapshot
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Strict public projection of actual callable data (the SDK has already unwrapped HTTP "result").
 * SHA-256 is required from the platform; hashing uses the server's canonical JSON arrays, not a
 * platform-dependent concatenation. Unknown stored fields never become client authority.
 */
class ArenaTicketWireParser(
    private val sha256: (String) -> String,
    private val questions: QuestionContentParser = KotlinxSerializationQuestionContentParser(),
) {
    fun prepare(
        wire: String,
        request: ArenaPrepareRequest,
    ): ArenaPrepareResponse =
        checked {
            val root = root(wire)
            if (root.string("status") == "RETRY_REGEN") {
                valid(root.string("requestId") == request.requestId)
                ArenaPrepareResponse.RetryRegeneration(request.requestId)
            } else {
                val ticket = ticket(root, request.ownerUid)
                val header = ticket.snapshot.header
                valid(header.requestId == request.requestId && header.difficulty == request.difficulty)
                when (request) {
                    is ArenaPrepareRequest.Random -> valid(header.mode == ArenaEntryMode.RANDOM)
                    is ArenaPrepareRequest.Selected ->
                        valid(
                            header.mode == ArenaEntryMode.SELECTED && header.lessonId == request.lessonId &&
                                header.priceNolics <= request.maxPriceNolics,
                        )
                }
                ArenaPrepareResponse.Ticket(ticket)
            }
        }

    fun get(
        wire: String,
        request: ArenaTicketGetRequest,
    ): ArenaEntryTicket =
        checked {
            ticket(root(wire), request.ownerUid).also {
                valid(it.snapshot.header.ticketId == request.ticketId)
            }
        }

    fun list(
        wire: String,
        request: ArenaTicketListRequest,
    ): ArenaTicketPage =
        checked {
            val root = root(wire)
            val rows = root.array("tickets")
            valid(rows.size <= request.pageSize)
            var previous = request.afterTicketId.orEmpty()
            val entries =
                rows.map {
                    header(it.objectValue(), request.ownerUid).also { item ->
                        valid(item.status == ArenaTicketStatus.RESERVED && item.ticketId > previous)
                        previous = item.ticketId
                    }
                }
            val next = root.nullableString("nextTicketId")
            if (next != null) {
                valid(entries.size == request.pageSize && next == entries.last().ticketId)
            }
            ArenaTicketPage(entries.toList(), next)
        }

    fun cancel(
        wire: String,
        request: ArenaTicketCancelRequest,
    ): ArenaCancelResponse =
        checked {
            val root = root(wire)
            val header = request.ticket
            valid(root.string("ticketId") == header.ticketId)
            when (root.string("status")) {
                "RETRY_REGEN" -> {
                    valid(root.string("requestId") == request.requestId)
                    ArenaCancelResponse.RetryRegeneration(header.ticketId, request.requestId)
                }
                "CANCELLED" ->
                    ArenaCancelResponse.Cancelled(
                        header.ticketId,
                        cancellation(root.required("terminal").objectValue(), header),
                    )
                "SETTLED" ->
                    ArenaCancelResponse.AlreadySettled(
                        header.ticketId,
                        settlement(root.required("terminal").objectValue(), header),
                    )
                else -> malformed()
            }
        }

    private fun ticket(
        root: JsonObject,
        ownerUid: String,
    ): ArenaEntryTicket {
        valid(root.string("status") == "ISSUED")
        val raw = root.required("ticket").objectValue()
        val header = header(raw, ownerUid)
        val snapshot =
            ArenaTicketSnapshot(
                header = header,
                authorUid = raw.documentId("authorUid"),
                ratingVersion = raw.integer("ratingVersion"),
                contentContext = context(raw.required("contentContext")),
                questions = publicQuestions(raw.array("questions"), header),
            )
        val terminal = raw.required("terminal")
        return when (header.status) {
            ArenaTicketStatus.RESERVED -> {
                valid(terminal == JsonNull)
                ArenaEntryTicket.Reserved(snapshot)
            }
            ArenaTicketStatus.CANCELLED ->
                ArenaEntryTicket.Cancelled(
                    snapshot,
                    cancellation(terminal.objectValue(), header),
                )
            ArenaTicketStatus.SETTLED ->
                ArenaEntryTicket.Settled(
                    snapshot,
                    settlement(terminal.objectValue(), header),
                )
        }
    }

    private fun header(
        raw: JsonObject,
        ownerUid: String,
    ): ArenaTicketHeader {
        valid(raw.integer("schemaVersion") == 1L && raw.documentId("uid") == ownerUid)
        val requestId = raw.documentId("requestId")
        val ticketId = raw.hexId("ticketId")
        val attemptId = raw.hexId("attemptId")
        valid(ticketId == digest("arena-ticket", ownerUid, requestId))
        valid(attemptId == digest("arena-attempt", ticketId))
        val mode = ArenaEntryMode.valueOf(raw.string("mode"))
        val difficulty = Difficulty.valueOf(raw.string("difficulty"))
        val price = raw.integer("priceNolics", max = MAX_PRICE_NOLICS).toInt()
        valid(if (mode == ArenaEntryMode.RANDOM) price == 0 else price >= 1 && price <= MAX_PRICE_NOLICS)
        val issuedAtMs = raw.integer("issuedAtMs", max = MAX_DATE_MS)
        val dayId = raw.string("dayId")
        valid(dayId == utcDayId(issuedAtMs))
        return ArenaTicketHeader(
            schemaVersion = 1,
            ticketId = ticketId,
            attemptId = attemptId,
            ownerUid = ownerUid,
            requestId = requestId,
            mode = mode,
            difficulty = difficulty,
            lessonId = raw.documentId("lessonId"),
            lessonVersion = raw.integer("lessonVersion", min = 1),
            status = ArenaTicketStatus.valueOf(raw.string("status")),
            priceNolics = price,
            lifeCost = raw.integer("lifeCost", min = 1),
            issuedAtMs = issuedAtMs,
            dayId = dayId,
        )
    }

    private fun context(raw: JsonElement): ArenaTicketContentContext? {
        if (raw == JsonNull) return null
        val value = raw.objectValue()
        return ArenaTicketContentContext(
            value.documentId("catalogId"),
            value.documentId("questId"),
            value.documentId("sectionId"),
            value.documentId("themeId"),
        )
    }

    private fun publicQuestions(
        rows: JsonArray,
        header: ArenaTicketHeader,
    ): List<ArenaTicketQuestion> {
        valid(rows.size in 1..MAX_QUESTIONS)
        val seen = mutableSetOf<String>()
        return rows.mapIndexed { order, element ->
            val row = element.objectValue()
            val id = row.documentId("questionId")
            val version = row.integer("questionVersion", min = 1)
            valid(seen.add(id))
            val question = row.required("publicQuestion").objectValue()
            valid(question.string("id") == id && question.integer("version", min = 1) == version)
            valid(question.string("lessonId") == header.lessonId)
            valid(question.string("difficulty") == header.difficulty.name)
            valid(question.integer("order") == order.toLong())
            val payload = question.string("payload")
            valid(payload.isNotEmpty() && payload.encodeToByteArray().size <= MAX_PUBLIC_BYTES)
            val content = Json.parseToJsonElement(payload).objectValue()
            if (header.difficulty == Difficulty.HARD) validateArenaHardQuestionShape(payload, content)
            if ("id" in content) valid(content.string("id") == id)
            if ("difficulty" in content) valid(content.string("difficulty") == header.difficulty.name)
            val display =
                questions.parseForDisplay(
                    payload,
                    fallbackId = id,
                    fallbackText = content.string("text"),
                    fallbackDifficulty = header.difficulty,
                ).getOrElse { malformed() }
            valid(display.id == null || display.id == id)
            valid(display.difficultyOrNull == null || display.difficultyOrNull == header.difficulty)
            valid(display.text.isNotBlank())
            if (header.difficulty == Difficulty.EASY) {
                valid(display is QuestionContent)
            } else {
                valid(display is RedactedQuestionContent || display is QuestionContent.Survey)
            }
            if (display is RedactedQuestionContent) validateRedacted(display)
            ArenaTicketQuestion(id, version, order, question.string("language"), payload, display)
        }.toList()
    }

    private fun validateRedacted(display: RedactedQuestionContent) {
        when (display) {
            is RedactedQuestionContent.SingleChoice -> validRows(display.options, 2..MAX_CHOICE_ROWS)
            is RedactedQuestionContent.MultipleChoice -> validRows(display.options, 2..MAX_CHOICE_ROWS)
            is RedactedQuestionContent.Ordering -> validRows(display.items, 2..MAX_CHOICE_ROWS)
            is RedactedQuestionContent.FillBlank -> {
                valid(display.blanks.size in 1..MAX_BLANKS && display.blanks.all { it.isNotBlank() })
                valid(display.blanks.distinct().size == display.blanks.size)
                valid(display.candidates.size == SHORT_CANDIDATE_POOL || display.candidates.size == LONG_CANDIDATE_POOL)
                validRows(display.candidates, SHORT_CANDIDATE_POOL..LONG_CANDIDATE_POOL)
            }
        }
    }

    private fun validRows(
        rows: List<RedactedQuestionContent.Row>,
        sizes: IntRange,
    ) {
        valid(rows.size in sizes && rows.all { it.id.isNotBlank() })
        valid(rows.map { it.id }.distinct().size == rows.size)
    }

    private fun cancellation(
        raw: JsonObject,
        ticket: ArenaTicketHeader,
    ): ArenaCancellationReceipt {
        val receiptId = raw.string("receiptId")
        valid(receiptId == "arena-cancel:${ticket.ticketId}")
        val time = raw.integer("cancelledAtMs", min = ticket.issuedAtMs)
        val nolics = raw.integer("releasedNolics", max = MAX_PRICE_NOLICS).toInt()
        val life = raw.integer("releasedLifePoints")
        valid(nolics == ticket.priceNolics && life == ticket.lifeCost)
        return ArenaCancellationReceipt(receiptId, time, nolics, life)
    }

    private fun settlement(
        raw: JsonObject,
        ticket: ArenaTicketHeader,
    ): ArenaSettlementReceipt {
        val receiptId = raw.string("receiptId")
        val attemptId = raw.hexId("attemptId")
        valid(receiptId == "arena-settle:${ticket.ticketId}" && attemptId == ticket.attemptId)
        val time = raw.integer("settledAtMs", min = ticket.issuedAtMs)
        val score = raw.number("percentScore")
        valid(score in 0.0..MAX_PERCENT_SCORE)
        val nolics = raw.integer("consumedNolics", max = MAX_PRICE_NOLICS).toInt()
        val life = raw.integer("consumedLifePoints")
        val author = raw.integer("authorCreditNolics")
        val sink = raw.integer("sinkNolics")
        val reserveDelta = raw.integer("royaltyReserveDeltaNolics", min = -MAX_SAFE_INTEGER)
        valid(nolics == ticket.priceNolics && life == ticket.lifeCost)
        // Three JS-safe integers still sum safely within Kotlin Long; no Double rounding.
        valid(author + sink + reserveDelta == nolics.toLong())
        return ArenaSettlementReceipt(receiptId, attemptId, time, score, nolics, life, author, sink, reserveDelta)
    }

    private fun digest(vararg items: String): String = sha256(JsonArray(items.map(::JsonPrimitive)).toString())

    @Suppress("MagicNumber") // ISO-8601 year/month/day field widths, matching the JavaScript issuer.
    private fun utcDayId(time: Long): String {
        val date = Instant.fromEpochMilliseconds(time).toLocalDateTime(TimeZone.UTC).date
        val digits = date.year.toString()
        val year =
            if (date.year > 9_999) {
                "+${digits.padStart(6, '0')}"
            } else {
                digits.padStart(4, '0')
            }
        val month = date.monthNumber.toString().padStart(2, '0')
        val day = date.dayOfMonth.toString().padStart(2, '0')
        // Match the issuer's Date.toISOString().slice(0, 10), including expanded-year dates.
        return "$year-$month-$day".take(10)
    }

    private fun root(wire: String): JsonObject {
        valid(wire.encodeToByteArray().size <= MAX_PUBLIC_BYTES)
        return Json.parseToJsonElement(wire).objectValue()
    }

    private inline fun <T> checked(block: () -> T): T =
        try {
            block()
        } catch (_: IllegalArgumentException) {
            malformed()
        }

    private fun JsonObject.required(key: String): JsonElement = get(key) ?: malformed()

    private fun JsonObject.string(key: String): String {
        val value = required(key) as? JsonPrimitive ?: malformed()
        valid(value.isString)
        return value.content
    }

    private fun JsonObject.nullableString(key: String): String? = if (required(key) == JsonNull) null else string(key)

    private fun JsonObject.documentId(key: String): String =
        string(key).also {
            valid(it.isNotBlank() && '/' !in it && it != "." && it != "..")
            valid(it.encodeToByteArray().size <= MAX_DOCUMENT_ID_BYTES)
        }

    private fun JsonObject.hexId(key: String): String =
        string(key).also {
            valid(it.length == SHA256_HEX_LENGTH && it.all { char -> char in '0'..'9' || char in 'a'..'f' })
        }

    private fun JsonObject.array(key: String): JsonArray = required(key) as? JsonArray ?: malformed()

    private fun JsonObject.integer(
        key: String,
        min: Long = 0,
        max: Long = MAX_SAFE_INTEGER,
    ): Long {
        val value = required(key) as? JsonPrimitive ?: malformed()
        valid(!value.isString)
        return (value.longOrNull ?: malformed()).also { valid(it in min..max) }
    }

    private fun JsonObject.number(key: String): Double {
        val value = required(key) as? JsonPrimitive ?: malformed()
        valid(!value.isString)
        return (value.doubleOrNull ?: malformed()).also { valid(it.isFinite()) }
    }

    private companion object {
        const val MAX_PRICE_NOLICS = 100L
        const val MAX_PERCENT_SCORE = 100.0
        const val MAX_QUESTIONS = 20
        const val MAX_CHOICE_ROWS = 8
        const val MAX_BLANKS = 3
        const val SHORT_CANDIDATE_POOL = 5
        const val LONG_CANDIDATE_POOL = 10
        const val MAX_DOCUMENT_ID_BYTES = 1_500
        const val SHA256_HEX_LENGTH = 64
        const val MAX_PUBLIC_BYTES = 500_000
        const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
        const val MAX_DATE_MS = 8_640_000_000_000_000L
    }
}

private fun JsonElement.objectValue(): JsonObject = this as? JsonObject ?: malformed()

private fun valid(condition: Boolean) {
    if (!condition) malformed()
}

private fun malformed(): Nothing = throw ArenaRemoteFailure(ArenaRemoteFailureKind.MALFORMED_RESPONSE)
