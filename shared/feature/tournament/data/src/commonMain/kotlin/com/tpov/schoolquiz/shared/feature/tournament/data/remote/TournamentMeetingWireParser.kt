package com.tpov.schoolquiz.shared.feature.tournament.data.remote

import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingPage
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingParticipant
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingSlot
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentMeetingView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentReadySignal
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Строгий разбор страницы встречи — зеркало `readMeeting` (история 5.7): оценённая встреча
 * (`safeMeeting`), участники, постраничные предложения и подтверждения, готовность, курсоры.
 *
 * Курсоры круглым ходом возвращаются в следующий запрос; проверка связности страницы (курсор
 * существует тогда и только тогда, когда страница неполная, и зовёт её последнюю строку) —
 * та же, что у страниц событий. Всё содержимое — серверные факты, ключёванные автором; приватных
 * форм, ключей и отпечатков в проекции нет по построению, лишние ключи — [MalformedWire].
 */
class TournamentMeetingWireParser {
    fun parseMeetingPage(wire: String): TournamentMeetingPage =
        fail {
            val root = wireRoot(wire)
            root.exact(
                "eventId", "meeting", "schedulingDeadlineMs", "stageStartsAtMs", "stageEndsAtMs",
                "participants", "offers", "hasMore", "nextOfferId", "confirmations",
                "hasMoreConfirmations", "nextConfirmationId", "ready", "serverNowMs",
            )
            val offers = root.requiredRows("offers").map { slot(it.jsonObject, idField = "offerId") }
            val confirmations =
                root.requiredRows(
                    "confirmations",
                ).map { slot(it.jsonObject, idField = "confirmationId") }
            val hasMoreOffers = root.requiredBit("hasMore")
            val nextOffer = root.nullableText("nextOfferId")
            if (hasMoreOffers != (nextOffer != null)) malformedWire()
            nextOffer?.let { if (offers.lastOrNull()?.id != it) malformedWire() }
            val hasMoreConfirmations = root.requiredBit("hasMoreConfirmations")
            val nextConfirmation = root.nullableText("nextConfirmationId")
            if (hasMoreConfirmations != (nextConfirmation != null)) malformedWire()
            nextConfirmation?.let { if (confirmations.lastOrNull()?.id != it) malformedWire() }
            TournamentMeetingPage(
                eventId = root.nonBlank("eventId"),
                meeting = meeting(root.requiredObject("meeting")),
                schedulingDeadlineMs = root.requiredWhole("schedulingDeadlineMs"),
                stageStartsAtMs = root.requiredWhole("stageStartsAtMs"),
                stageEndsAtMs = root.requiredWhole("stageEndsAtMs"),
                participants = root.requiredRows("participants").map { participant(it.jsonObject) },
                offers = offers,
                hasMoreOffers = hasMoreOffers,
                nextOfferId = nextOffer,
                confirmations = confirmations,
                hasMoreConfirmations = hasMoreConfirmations,
                nextConfirmationId = nextConfirmation,
                ready = root.requiredRows("ready").map { readySignal(it.jsonObject) },
                serverNowMs = root.requiredWhole("serverNowMs"),
            )
        }

    /** Оценённая встреча (`safeMeeting`): идентичность и статус обязательны, часы — по мере хода. */
    fun meeting(raw: JsonObject): TournamentMeetingView =
        fail {
            raw.within(
                "matchId", "status", "slotStartsAtMs", "agreedAtMs", "windowEndMs", "startedAtMs",
                "endsAtMs", "resultDue", "winnerUid", "loserUid", "technicalForfeit",
            )
            TournamentMeetingView(
                matchId = raw.nonBlank("matchId"),
                status = raw.nonBlank("status"),
                slotStartsAtMs = raw.whole("slotStartsAtMs"),
                agreedAtMs = raw.whole("agreedAtMs"),
                windowEndMs = raw.whole("windowEndMs"),
                startedAtMs = raw.whole("startedAtMs"),
                endsAtMs = raw.whole("endsAtMs"),
                resultDue = raw.whole("resultDue"),
                winnerUid = raw.text("winnerUid"),
                loserUid = raw.text("loserUid"),
                technicalForfeit = raw.bit("technicalForfeit"),
            )
        }

    private fun participant(raw: JsonObject): TournamentMeetingParticipant {
        raw.within("uid", "seed")
        return TournamentMeetingParticipant(uid = raw.nonBlank("uid"), seed = raw.whole("seed"))
    }

    private fun slot(
        raw: JsonObject,
        idField: String,
    ): TournamentMeetingSlot {
        raw.exact(idField, "uid", "slotStartsAtMs", "acceptedAtMs")
        return TournamentMeetingSlot(
            id = raw.nonBlank(idField),
            uid = raw.nonBlank("uid"),
            slotStartsAtMs = raw.requiredWhole("slotStartsAtMs"),
            acceptedAtMs = raw.requiredWhole("acceptedAtMs"),
        )
    }

    private fun readySignal(raw: JsonObject): TournamentReadySignal {
        raw.exact("uid", "acceptedAtMs")
        return TournamentReadySignal(uid = raw.nonBlank("uid"), acceptedAtMs = raw.requiredWhole("acceptedAtMs"))
    }

    private inline fun <T> fail(block: () -> T): T =
        runCatching { block() }.getOrElse { failure ->
            if (failure is MalformedWire || failure is SerializationException || failure is IllegalArgumentException) {
                throw MalformedWire().initCause(failure)
            }
            throw failure
        }
}
