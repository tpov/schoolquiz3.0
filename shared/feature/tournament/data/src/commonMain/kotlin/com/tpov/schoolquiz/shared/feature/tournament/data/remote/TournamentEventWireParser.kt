package com.tpov.schoolquiz.shared.feature.tournament.data.remote

import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEntrantRef
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEscrowView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventPage
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventRead
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMatchRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPendingMatchRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPlacementRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrizeRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrizeView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentProgressView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentQualificationView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentRankingRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentRankingView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentResolvedMatchRow
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSourceRef
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Строгий разбор событий соревнования — зеркала `safeEvent`/`safeProgress`/матчей/рейтинга
 * контроллера (`functions/course-tournament-controller.js`).
 *
 * Разрешён только allowlist ключей: дрейф проекции (лишний ключ), отсутствующее обязательное
 * поле и неверный тип — одно и то же [MalformedWire]. Призы читаются в обеих хранимых формах
 * (L2): версия 0 — проекция legacy, версия 1 — канонический список; строки обеих — {place,
 * amountBoxes}; никакая приватная форма (first/second/third, сид, отпечаток) клиентом не читается.
 * Рейтинг (история 5.10) приезжает только чтением события: строки замороженного документа в
 * redacted-allowlist {uid, place, ratingPercent, averagePercent} — проценты необязательны,
 * их отсутствие честно, а у страниц просмотра ключа быть не может.
 */
class TournamentEventWireParser {
    /** Ответ `fetchCourseTournamentEvent`: {event, serverNowMs}; матчи только в BRACKET/FINISHED. */
    fun parseEventRead(wire: String): TournamentEventRead =
        fail {
            val root = wireRoot(wire)
            root.exact("event", "serverNowMs")
            TournamentEventRead(
                event = event(root.requiredObject("event"), withMatches = true),
                serverNowMs = root.requiredWhole("serverNowMs"),
            )
        }

    /** Ответ `fetchCourseTournamentEvents`: одна страница тех же видов с курсором или без. */
    fun parseEventPage(wire: String): TournamentEventPage =
        fail {
            val root = wireRoot(wire)
            root.exact("events", "hasMore", "nextEventId", "serverNowMs")
            val rows = root.requiredRows("events").map { event(it.jsonObject, withMatches = false) }
            val hasMore = root.requiredBit("hasMore")
            val next = root.nullableText("nextEventId")
            // Курсор существует тогда и только тогда, когда страница неполная, и зовёт последнюю строку.
            if (hasMore != (next != null)) malformedWire()
            next?.let { if (rows.lastOrNull()?.eventId != it) malformedWire() }
            TournamentEventPage(
                events = rows,
                hasMore = hasMore,
                nextEventId = next,
                serverNowMs = root.requiredWhole("serverNowMs"),
            )
        }

    /** Вид события: allowlist `safeEvent`; [withMatches] разрешает серверные матчи чтения сетки
     * и опубликованный рейтинг — оба члена добавляет только чтение события. */
    fun event(
        raw: JsonObject,
        withMatches: Boolean,
    ): TournamentEventView =
        fail {
            if (withMatches) {
                raw.within(
                    "eventId", "organizerUid", "status", "cancelReason", "source", "qualification",
                    "prizes", "escrow", "progress", "matches", "qualificationRanking",
                )
            } else {
                raw.within(
                    "eventId", "organizerUid", "status", "cancelReason", "source", "qualification",
                    "prizes", "escrow", "progress",
                )
            }
            TournamentEventView(
                eventId = raw.nonBlank("eventId"),
                organizerUid = raw.nonBlank("organizerUid"),
                status = raw.nonBlank("status"),
                cancelReason = raw.text("cancelReason"),
                source = source(raw.requiredObject("source")),
                qualification = qualification(raw.requiredObject("qualification")),
                prizes = prizes(raw.requiredObject("prizes")),
                escrow = escrow(raw.requiredObject("escrow")),
                progress = raw.obj("progress")?.let(::progress),
                matches =
                    if (withMatches) {
                        raw.rows("matches")?.map { matchRow(it.jsonObject) } ?: emptyList()
                    } else {
                        if (raw.element("matches") != null) malformedWire()
                        emptyList()
                    },
                ranking =
                    if (withMatches) {
                        raw.obj("qualificationRanking")?.let(::ranking)
                    } else {
                        if (raw.element("qualificationRanking") != null) malformedWire()
                        null
                    },
            )
        }

    private fun source(raw: JsonObject): TournamentSourceRef {
        raw.exact("kind", "id")
        return TournamentSourceRef(kind = raw.nonBlank("kind"), id = raw.nonBlank("id"))
    }

    private fun qualification(raw: JsonObject): TournamentQualificationView {
        raw.exact("startsAtMs", "endsAtMs", "roundCount", "questionsPerRound", "attemptsPerRound")
        return TournamentQualificationView(
            startsAtMs = raw.requiredWhole("startsAtMs"),
            endsAtMs = raw.requiredWhole("endsAtMs"),
            roundCount = raw.requiredInt("roundCount"),
            questionsPerRound = raw.requiredInt("questionsPerRound"),
            attemptsPerRound = raw.requiredInt("attemptsPerRound"),
        )
    }

    /** Единая призовая проекция: {listVersion, rows} плюс ответственность, когда сервер её хранит. */
    private fun prizes(raw: JsonObject): TournamentPrizeView {
        raw.within("listVersion", "rows", "maximumLiabilityBoxes")
        val listVersion = raw.requiredInt("listVersion")
        if (listVersion != 0 && listVersion != 1) malformedWire()
        val rows =
            raw.requiredRows("rows").map { row ->
                row.jsonObject.exact("place", "amountBoxes")
                TournamentPrizeRow(
                    place = row.jsonObject.requiredInt("place"),
                    amountBoxes = row.jsonObject.requiredWhole("amountBoxes"),
                )
            }
        if (rows.size > MAX_PRIZE_ROWS) malformedWire()
        rows.forEach { if (it.place < 1 || it.place > MAX_PRIZE_ROWS) malformedWire() }
        return TournamentPrizeView(
            listVersion = listVersion,
            rows = rows,
            maximumLiabilityBoxes = raw.whole("maximumLiabilityBoxes"),
        )
    }

    private fun escrow(raw: JsonObject): TournamentEscrowView {
        raw.exact("reservedBoxes", "remainingBoxes", "paidBoxes", "returnedBoxes")
        return TournamentEscrowView(
            reservedBoxes = raw.requiredWhole("reservedBoxes"),
            remainingBoxes = raw.requiredWhole("remainingBoxes"),
            paidBoxes = raw.requiredWhole("paidBoxes"),
            returnedBoxes = raw.requiredWhole("returnedBoxes"),
        )
    }

    /** Прогресс сетки: статус, решённые, ждущие, места. */
    private fun progress(raw: JsonObject): TournamentProgressView {
        raw.within("status", "resolved", "pending", "placements")
        return TournamentProgressView(
            status = raw.nonBlank("status"),
            resolved = raw.rows("resolved")?.map { resolved(it.jsonObject) } ?: emptyList(),
            pending = raw.rows("pending")?.map { pending(it.jsonObject) } ?: emptyList(),
            placements = raw.rows("placements")?.map { placement(it.jsonObject) } ?: emptyList(),
        )
    }

    private fun resolved(raw: JsonObject): TournamentResolvedMatchRow {
        raw.within("matchId", "status", "winnerUid", "loserUid", "technicalForfeit")
        return TournamentResolvedMatchRow(
            matchId = raw.nonBlank("matchId"),
            status = raw.nonBlank("status"),
            winnerUid = raw.text("winnerUid"),
            loserUid = raw.text("loserUid"),
            technicalForfeit = raw.bit("technicalForfeit"),
        )
    }

    private fun pending(raw: JsonObject): TournamentPendingMatchRow {
        raw.exact("matchId", "entrants")
        return TournamentPendingMatchRow(
            matchId = raw.nonBlank("matchId"),
            entrants = raw.requiredRows("entrants").map { entrant(it.jsonObject) },
        )
    }

    private fun entrant(raw: JsonObject): TournamentEntrantRef {
        raw.within("uid", "seed")
        return TournamentEntrantRef(uid = raw.nonBlank("uid"), seed = raw.whole("seed"))
    }

    private fun placement(raw: JsonObject): TournamentPlacementRow {
        raw.within("uid", "rank", "slot", "technicalForfeit")
        return TournamentPlacementRow(
            uid = raw.nonBlank("uid"),
            rank = raw.requiredWhole("rank"),
            slot = raw.requiredWhole("slot"),
            technicalForfeit = raw.bit("technicalForfeit"),
        )
    }

    /** Матч чтения сетки: стадия, часы и авторитетный край планирования. */
    private fun matchRow(raw: JsonObject): TournamentMatchRow {
        raw.exact("matchId", "stage", "stageStartsAtMs", "stageEndsAtMs", "maxDurationMs", "schedulingDeadlineMs")
        return TournamentMatchRow(
            matchId = raw.nonBlank("matchId"),
            stage = raw.requiredWhole("stage"),
            stageStartsAtMs = raw.requiredWhole("stageStartsAtMs"),
            stageEndsAtMs = raw.requiredWhole("stageEndsAtMs"),
            maxDurationMs = raw.requiredWhole("maxDurationMs"),
            schedulingDeadlineMs = raw.requiredWhole("schedulingDeadlineMs"),
        )
    }

    /**
     * Опубликованный рейтинг: объём когорты и строки замороженного документа. [totalRanked] и
     * `rows` сервер выводит всегда; ключи строки — подмножество allowlist'а (проценты приезжают,
     * только если замороженный документ их хранит), лишний ключ — дрейф, не витрина.
     */
    private fun ranking(raw: JsonObject): TournamentRankingView {
        raw.exact("totalRanked", "rows")
        val rows =
            raw.requiredRows("rows").map { row ->
                row.jsonObject.within("uid", "place", "ratingPercent", "averagePercent")
                TournamentRankingRow(
                    uid = row.jsonObject.nonBlank("uid"),
                    place = row.jsonObject.requiredInt("place"),
                    ratingPercent = row.jsonObject.fraction("ratingPercent"),
                    averagePercent = row.jsonObject.fraction("averagePercent"),
                )
            }
        if (rows.size > MAX_RANKING_ROWS) malformedWire()
        rows.forEach { if (it.place < 1) malformedWire() }
        val totalRanked = raw.requiredWhole("totalRanked")
        // Объём когорты покрывает строки: меньше оценённых игроков, чем строк, не бывает —
        // это не проекция сервера, а витрина.
        if (totalRanked < rows.size) malformedWire()
        return TournamentRankingView(totalRanked = totalRanked, rows = rows)
    }

    private inline fun <T> fail(block: () -> T): T =
        runCatching { block() }.getOrElse { failure ->
            if (failure is MalformedWire || failure is SerializationException || failure is IllegalArgumentException) {
                throw MalformedWire().initCause(failure)
            }
            throw failure
        }

    private companion object {
        const val MAX_PRIZE_ROWS = 15

        /** Верх сетки — серверная константа qualification top16; больше строк заморозка не несёт. */
        const val MAX_RANKING_ROWS = 16
    }
}
