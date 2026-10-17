package com.tpov.schoolquiz.shared.feature.tournament.domain.model

//
// Типизированные виды события соревнования — зеркала серверной проекции `safeEvent`
// (`functions/course-tournament-controller.js`).
//
// Модели несут только то, что сервер сам выводит в свой allowlist: никаких сидов, форм,
// отпечатков, банков или токена ссылки организатора в клиентских типах нет по построению —
// редакция достигается формой, а не дисциплиной вызывающего. Неизвестные наборы ключей разбор
// отвергает (fail-closed), поэтому дрейф проекции никогда не читается молча.

/** Источник соревнования: какой Reviewed-курс или аренный урок взят за основу (только kind и id). */
data class TournamentSourceRef(
    val kind: String,
    val id: String,
)

/** Квалификационная фаза: серверные часы и форма раундов. [endsAtMs] — авторитетный край. */
data class TournamentQualificationView(
    val startsAtMs: Long,
    val endsAtMs: Long,
    val roundCount: Int,
    val questionsPerRound: Int,
    val attemptsPerRound: Int,
)

/** Одна строка призового списка в единой проекции (L2): место и коробки. */
data class TournamentPrizeRow(
    val place: Int,
    val amountBoxes: Long,
)

/**
 * Единая призовая проекция (L2): и legacy-DTO, и замороженный список читаются в одну форму.
 *
 * [listVersion] — 0 (проекция legacy {first, second, third}, всегда четыре строки) или 1
 * (канонический список). [maximumLiabilityBoxes] пуст только для версии 0 без сохранённой
 * ответственности — сервер тогда просто не хранит число.
 */
data class TournamentPrizeView(
    val listVersion: Int,
    val rows: List<TournamentPrizeRow>,
    val maximumLiabilityBoxes: Long?,
)

/** Эскроу события: зарезервировано, осталось, выплачено, возвращено. */
data class TournamentEscrowView(
    val reservedBoxes: Long,
    val remainingBoxes: Long,
    val paidBoxes: Long,
    val returnedBoxes: Long,
)

/** Решённый матч в прогрессе сетки: исход по имени сервера, без счёта и материалов форм. */
data class TournamentResolvedMatchRow(
    val matchId: String,
    val status: String,
    val winnerUid: String?,
    val loserUid: String?,
    val technicalForfeit: Boolean?,
)

/** Участник пары ждущего матча: uid и его посев. */
data class TournamentEntrantRef(
    val uid: String,
    val seed: Long?,
)

/** Ждущий матч прогресса: пара и их посевы, без часов пары — они приезжают match-строкой. */
data class TournamentPendingMatchRow(
    val matchId: String,
    val entrants: List<TournamentEntrantRef>,
)

/** Итоговое место участника. */
data class TournamentPlacementRow(
    val uid: String,
    val rank: Long,
    val slot: Long,
    val technicalForfeit: Boolean?,
)

/** Прогресс сетки: статус и три списка — решённые, ждущие, места. */
data class TournamentProgressView(
    val status: String,
    val resolved: List<TournamentResolvedMatchRow>,
    val pending: List<TournamentPendingMatchRow>,
    val placements: List<TournamentPlacementRow>,
)

/** Один матч сетки в чтении события: стадия, часы и авторитетный край планирования. */
data class TournamentMatchRow(
    val matchId: String,
    val stage: Long,
    val stageStartsAtMs: Long,
    val stageEndsAtMs: Long,
    val maxDurationMs: Long,
    val schedulingDeadlineMs: Long,
)

/**
 * Вид события — зеркало `safeEvent`: идентификаторы, статус, источник, квалификация, призы
 * (единая проекция), эскроу; прогресс и матчи добавляются чтением события в BRACKET/FINISHED.
 * [cancelReason] — только у отменённых событий, пусто в остальных статусах.
 */
data class TournamentEventView(
    val eventId: String,
    val organizerUid: String,
    val status: String,
    val cancelReason: String?,
    val source: TournamentSourceRef,
    val qualification: TournamentQualificationView,
    val prizes: TournamentPrizeView,
    val escrow: TournamentEscrowView,
    val progress: TournamentProgressView?,
    val matches: List<TournamentMatchRow>,
)

/** Ответ `fetchCourseTournamentEvent`: вид события плюс серверное «сейчас». */
data class TournamentEventRead(
    val event: TournamentEventView,
    val serverNowMs: Long,
)

/**
 * Страница авторизованного просмотра событий (`fetchCourseTournamentEvents`, история 5.3):
 * один ограниченный лист тех же `safeEvent`-видов. Знание eventId допуском не является.
 */
data class TournamentEventPage(
    val events: List<TournamentEventView>,
    val hasMore: Boolean,
    val nextEventId: String?,
    val serverNowMs: Long,
)

/** Запрос чтения одного события; [ownerUid] — для забора смены аккаунта на границе. */
data class TournamentEventReadRequest(
    val ownerUid: String,
    val eventId: String,
)

/** Запрос страницы событий; [afterEventId] — курсор предыдущей страницы. */
data class TournamentEventListRequest(
    val ownerUid: String,
    val afterEventId: String? = null,
    val pageSize: Int = 25,
)
