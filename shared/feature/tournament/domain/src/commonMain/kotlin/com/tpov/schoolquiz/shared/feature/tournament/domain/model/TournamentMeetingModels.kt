package com.tpov.schoolquiz.shared.feature.tournament.domain.model

//
// Типизированные виды встречи пары — зеркало серверной проекции `safeMeeting` и страничного
// чтения `readMeeting` (история 5.7, UX-O3/AD-17): предложения, подтверждения, готовность,
// участники и границы стадии. Всё — серверные факты, ключёванные автором; никаких частных форм,
// ключей или отпечатков в проекции нет по построению.

/**
 * Оценённая встреча (`safeMeeting`): статус по словарю сервера и, по мере договорённости,
 * слот, согласие, край окна готовности, часы старта/финала, исход.
 */
data class TournamentMeetingView(
    val matchId: String,
    val status: String,
    val slotStartsAtMs: Long?,
    val agreedAtMs: Long?,
    val windowEndMs: Long?,
    val startedAtMs: Long?,
    val endsAtMs: Long?,
    val resultDue: Long?,
    val winnerUid: String?,
    val loserUid: String?,
    val technicalForfeit: Boolean?,
)

/** Участник пары: uid и посев. */
data class TournamentMeetingParticipant(
    val uid: String,
    val seed: Long?,
)

/** Одна строка предложения или подтверждения слота: кто, когда предложил/принял. */
data class TournamentMeetingSlot(
    val id: String,
    val uid: String,
    val slotStartsAtMs: Long,
    val acceptedAtMs: Long,
)

/** Сигнал готовности участника: серверное время приёма, не клиентское. */
data class TournamentReadySignal(
    val uid: String,
    val acceptedAtMs: Long,
)

/**
 * Страница чтения встречи (`fetchCourseTournamentMeeting`): оценённая встреча, границы стадии,
 * участники, постраничные предложения и подтверждения с курсорами следующей страницы, сигналы
 * готовности. Курсоры [TournamentMeetingPage.nextOfferId] /
 * [TournamentMeetingPage.nextConfirmationId] круглым ходом возвращаются в следующем запросе.
 */
data class TournamentMeetingPage(
    val eventId: String,
    val meeting: TournamentMeetingView,
    val schedulingDeadlineMs: Long,
    val stageStartsAtMs: Long,
    val stageEndsAtMs: Long,
    val participants: List<TournamentMeetingParticipant>,
    val offers: List<TournamentMeetingSlot>,
    val hasMoreOffers: Boolean,
    val nextOfferId: String?,
    val confirmations: List<TournamentMeetingSlot>,
    val hasMoreConfirmations: Boolean,
    val nextConfirmationId: String?,
    val ready: List<TournamentReadySignal>,
    val serverNowMs: Long,
)

/** Запрос страницы встречи; курсоры — ровно те, что вернула предыдущая страница. */
data class TournamentMeetingReadRequest(
    val ownerUid: String,
    val eventId: String,
    val matchId: String,
    val afterOfferId: String? = null,
    val afterConfirmationId: String? = null,
    val pageSize: Int = 25,
)
