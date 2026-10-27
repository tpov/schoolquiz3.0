package com.tpov.schoolquiz.shared.feature.tournament.data.remote

import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentAdmissionLink
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentDownloadRecord
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentLastClosed
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentOperationView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrepareCounts
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrepareReply
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPublicQuestion
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPublicQuestionPayload
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPublicQuestionRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentReadyAckReceipt
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionResult
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSessionView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentWindowView
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Строгий разбор сессий соревнования — зеркала `safeSession`, `safeQuestion`, `safeResponse`,
 * квитанции ready-ACK и записи скачивания контроллера (`functions/course-tournament-controller.js`).
 *
 * Край окна — [TournamentWindowView.expiresAtMs]; `windowSeconds` — грубая захваченная
 * константа, для отсчёта не используется. Токен ссылки организатора (`admissionLink`)
 * декодируется только в CREATE-квитанции (L4): типизированный непустой токен — единственный
 * легальный клиентский путь к раздаче допуска; ни в какой журнал он не пишется. Лишний ключ,
 * отсутствующее обязательное поле и неверный тип — одно и то же [MalformedWire].
 */
class TournamentSessionWireParser(
    private val events: TournamentEventWireParser,
    private val meetings: TournamentMeetingWireParser,
) {
    /**
     * Ответ операции/чтения сессии — `safeResponse`: статус и (по виду вызова) событие, сессии,
     * встреча, вопрос, окно, закрытый вопрос, квитанция ready-ACK, результат.
     */
    fun parseOperationView(wire: String): TournamentOperationView =
        fail {
            val root = wireRoot(wire)
            root.within(
                "schemaVersion", "operationId", "status", "serverNowMs", "deadlineReached",
                "questionDeadlineReached", "deliveredNowMs", "window", "event", "session", "sessions",
                "meeting", "currentQuestion", "lastClosed", "readyAck", "result", "admissionLink",
            )
            // У чтения сессии schemaVersion/status/operationId сервер не выводит вовсе; у операции —
            // выводит. Ключ, который есть, обязан быть корректным: версии — только 1, статус —
            // обязанный и непустой: пустой или null статус «присутствует» и потому испорчен.
            val schemaVersion = root.whole("schemaVersion")
            if (schemaVersion != null && schemaVersion != SCHEMA_VERSION) malformedWire()
            // organizer-only CREATE response: the token is decoded (L4) — it is the one legal
            // client-side path to the shareable admission link; blank or non-text fails closed.
            TournamentOperationView(
                admissionLink =
                    root.obj("admissionLink")?.let { link ->
                        link.exact("token")
                        TournamentAdmissionLink(token = link.nonBlank("token"))
                    },
                schemaVersion = schemaVersion,
                operationId = root.text("operationId"),
                status = if (root.element("status") == null) null else root.nonBlank("status"),
                serverNowMs = root.whole("serverNowMs"),
                deadlineReached = root.bit("deadlineReached"),
                questionDeadlineReached = root.bit("questionDeadlineReached"),
                deliveredNowMs = root.whole("deliveredNowMs"),
                window = root.obj("window")?.let(::window),
                event = root.obj("event")?.let { events.event(it, withMatches = false) },
                session = root.obj("session")?.let(::session),
                sessions = root.rows("sessions")?.map { session(it.jsonObject) },
                meeting = root.obj("meeting")?.let { meetings.meeting(it) },
                currentQuestion = root.element("currentQuestion")?.let(::questionOrNull),
                lastClosed = root.obj("lastClosed")?.let(::lastClosed),
                readyAck = root.obj("readyAck")?.let(::readyAck),
                result = root.obj("result")?.let(::result),
            )
        }

    /** Ответ `recordCourseTournamentDownload`: первая запись без флага, повтор — `replayed: true`. */
    fun parseDownloadRecord(wire: String): TournamentDownloadRecord =
        fail {
            val root = wireRoot(wire)
            val replayed = root.element("replayed") != null
            if (replayed) {
                root.exact("courseId", "downloadedAtMs", "replayed")
                if (root.requiredBit("replayed") != true) malformedWire()
            } else {
                root.exact("courseId", "downloadedAtMs")
            }
            TournamentDownloadRecord(
                courseId = root.nonBlank("courseId"),
                downloadedAtMs = root.requiredWhole("downloadedAtMs"),
                replayed = replayed,
            )
        }

    /** Ответ `prepareCourseTournamentBank`: снимок банка, его версия и состав. */
    fun parsePrepareReply(wire: String): TournamentPrepareReply =
        fail {
            val root = wireRoot(wire)
            root.exact("snapshotId", "counts", "timingVersion")
            val counts = root.requiredObject("counts")
            counts.exact("easy", "hard", "total")
            TournamentPrepareReply(
                snapshotId = root.nonBlank("snapshotId"),
                timingVersion = root.nonBlank("timingVersion"),
                counts =
                    TournamentPrepareCounts(
                        easy = counts.requiredInt("easy"),
                        hard = counts.requiredInt("hard"),
                        total = counts.requiredInt("total"),
                    ),
            )
        }

    /** Вид сессии (`safeSession`): идентичность, часы, прогресс; форма/отпечаток сюда не доезжают. */
    fun session(raw: JsonObject): TournamentSessionView =
        fail {
            raw.within(
                "sessionId", "eventId", "kind", "roundNumber", "matchId", "status", "questionIndex",
                "startedAtMs", "endsAtMs", "questionStartedAtMs", "questionEndsAtMs", "window",
            )
            TournamentSessionView(
                sessionId = raw.nonBlank("sessionId"),
                eventId = raw.nonBlank("eventId"),
                kind = raw.nonBlank("kind"),
                roundNumber = raw.int("roundNumber"),
                matchId = raw.text("matchId"),
                status = raw.nonBlank("status"),
                questionIndex = raw.requiredInt("questionIndex"),
                startedAtMs = raw.requiredWhole("startedAtMs"),
                endsAtMs = raw.requiredWhole("endsAtMs"),
                questionStartedAtMs = raw.requiredWhole("questionStartedAtMs"),
                questionEndsAtMs = raw.requiredWhole("questionEndsAtMs"),
                window = raw.obj("window")?.let(::window),
            )
        }

    /** Окно перехода: только идентичность и захваченные часы; вопрос/ответ не входят. */
    fun window(raw: JsonObject): TournamentWindowView =
        fail {
            raw.exact(
                "windowId",
                "completedQuestionIndex",
                "nextQuestionIndex",
                "windowSeconds",
                "openedAtMs",
                "expiresAtMs",
                "state",
            )
            TournamentWindowView(
                windowId = raw.nonBlank("windowId"),
                completedQuestionIndex = raw.requiredInt("completedQuestionIndex"),
                nextQuestionIndex = raw.requiredInt("nextQuestionIndex"),
                windowSeconds = raw.requiredInt("windowSeconds"),
                openedAtMs = raw.requiredWhole("openedAtMs"),
                expiresAtMs = raw.requiredWhole("expiresAtMs"),
                state = raw.nonBlank("state"),
            )
        }

    /** Квитанция ready-ACK: серверные факты выдачи; ключ — тройка (сессия, вопрос, окно). */
    private fun readyAck(raw: JsonObject): TournamentReadyAckReceipt {
        raw.exact(
            "schemaVersion",
            "questionIndex",
            "windowId",
            "questionId",
            "issuedAtMs",
            "answerDeadlineMs",
            "ackedAtMs",
        )
        if (raw.requiredWhole("schemaVersion") != SCHEMA_VERSION) malformedWire()
        return TournamentReadyAckReceipt(
            schemaVersion = SCHEMA_VERSION,
            questionIndex = raw.requiredInt("questionIndex"),
            windowId = raw.nonBlank("windowId"),
            questionId = raw.nonBlank("questionId"),
            issuedAtMs = raw.requiredWhole("issuedAtMs"),
            answerDeadlineMs = raw.requiredWhole("answerDeadlineMs"),
            ackedAtMs = raw.requiredWhole("ackedAtMs"),
        )
    }

    private fun result(raw: JsonObject): TournamentSessionResult {
        raw.exact("percentScore", "elapsedMs", "completedAtMs")
        val percent = raw.requiredFraction("percentScore")
        if (percent < MIN_PERCENT || percent > MAX_PERCENT) malformedWire()
        return TournamentSessionResult(
            percentScore = percent,
            elapsedMs = raw.requiredWhole("elapsedMs"),
            completedAtMs = raw.requiredWhole("completedAtMs"),
        )
    }

    private fun lastClosed(raw: JsonObject): TournamentLastClosed {
        raw.exact("questionIndex", "questionId", "kind")
        return TournamentLastClosed(
            questionIndex = raw.requiredInt("questionIndex"),
            questionId = raw.nonBlank("questionId"),
            kind = raw.nonBlank("kind"),
        )
    }

    /** Вопрос или честная пустота: null-вопрос — «сейчас вопроса нет», а не «неизвестно». */
    private fun questionOrNull(raw: Any?): TournamentPublicQuestion? {
        if (raw is JsonNull) return null
        return question(raw as? JsonObject ?: malformedWire())
    }

    private fun question(raw: JsonObject): TournamentPublicQuestion {
        raw.within("questionId", "type", "difficulty", "contentVersion", "maxDurationMs", "publicPayload")
        return TournamentPublicQuestion(
            questionId = raw.text("questionId"),
            type = raw.text("type"),
            difficulty = raw.text("difficulty"),
            contentVersion = raw.whole("contentVersion"),
            maxDurationMs = raw.whole("maxDurationMs"),
            publicPayload = publicPayload(raw.requiredObject("publicPayload")),
        )
    }

    /** Публичная (редактированная) нагрузка: тип и текст обязательны; ключи — allowlist'у сервера. */
    private fun publicPayload(raw: JsonObject): TournamentPublicQuestionPayload {
        raw.within(
            "type", "id", "difficulty", "text", "imageUrl", "blanks", "protectedTextSegments",
            "options", "items", "candidates",
        )
        return TournamentPublicQuestionPayload(
            type = raw.nonBlank("type"),
            id = raw.text("id"),
            difficulty = raw.text("difficulty"),
            text = raw.nonBlank("text"),
            imageUrl = raw.text("imageUrl"),
            blanks = raw.rows("blanks")?.map { textRow(it) },
            protectedTextSegments = raw.rows("protectedTextSegments")?.map { textRow(it) },
            options = raw.rows("options")?.map { row(it.jsonObject) },
            items = raw.rows("items")?.map { row(it.jsonObject) },
            candidates = raw.rows("candidates")?.map { row(it.jsonObject) },
        )
    }

    private fun textRow(raw: Any?): String =
        (raw as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() } ?: malformedWire()

    private fun row(raw: JsonObject): TournamentPublicQuestionRow {
        raw.exact("id", "text")
        return TournamentPublicQuestionRow(id = raw.nonBlank("id"), text = raw.nonBlank("text"))
    }

    private inline fun <T> fail(block: () -> T): T =
        runCatching { block() }.getOrElse { failure ->
            if (failure is MalformedWire || failure is SerializationException || failure is IllegalArgumentException) {
                throw MalformedWire().initCause(failure)
            }
            throw failure
        }

    private companion object {
        const val SCHEMA_VERSION = 1L
        const val MIN_PERCENT = 0.0
        const val MAX_PERCENT = 100.0
    }
}
