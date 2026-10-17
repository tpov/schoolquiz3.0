package com.tpov.schoolquiz.shared.feature.tournament.domain.model

//
// Типизированные виды сессии соревнования — зеркала серверных проекций `safeSession`,
// `safeQuestion`, `safeResponse` и квитанции ready-ACK (ONLINE-O1/O2 в проекции контроллера
// `functions/course-tournament-controller.js`).
//
// Окно перехода — только факты времени и идентичности: никогда вопрос или ответ. Край
// [TournamentWindowView.expiresAtMs] — авторитетный срок окна; `windowSeconds` — грубая
// захваченная константа и для отсчёта не используется.

/** Окно перехода next-question: идентичность и захваченные часы, вопрос не входит. */
data class TournamentWindowView(
    val windowId: String,
    val completedQuestionIndex: Int,
    val nextQuestionIndex: Int,
    val windowSeconds: Int,
    val openedAtMs: Long,
    val expiresAtMs: Long,
    val state: String,
)

/**
 * Вид сессии (`safeSession`): идентичность, статус, прогресс и часы. [roundNumber] и
 * [matchId] приезжают только у квалификации и матча соответственно; [window] — только у
 * живой границы (история окна — серверная).
 */
data class TournamentSessionView(
    val sessionId: String,
    val eventId: String,
    val kind: String,
    val roundNumber: Int?,
    val matchId: String?,
    val status: String,
    val questionIndex: Int,
    val startedAtMs: Long,
    val endsAtMs: Long,
    val questionStartedAtMs: Long,
    val questionEndsAtMs: Long,
    val window: TournamentWindowView?,
)

/** Одна строка вариантов публичного вопроса: id и текст, ничего больше. */
data class TournamentPublicQuestionRow(
    val id: String,
    val text: String,
)

/**
 * Публичная (редактированная) нагрузка вопроса из `safeQuestion`: тип, текст и варианты.
 * Приватных материалов — ключа, маппинга, ответа — в проекции нет по построению.
 */
data class TournamentPublicQuestionPayload(
    val type: String,
    val id: String?,
    val difficulty: String?,
    val text: String,
    val imageUrl: String?,
    val blanks: List<String>?,
    val protectedTextSegments: List<String>?,
    val options: List<TournamentPublicQuestionRow>?,
    val items: List<TournamentPublicQuestionRow>?,
    val candidates: List<TournamentPublicQuestionRow>?,
)

/** Текущий публичный вопрос сессии: идентичность вопроса и его публичная нагрузка. */
data class TournamentPublicQuestion(
    val questionId: String?,
    val type: String?,
    val difficulty: String?,
    val contentVersion: Long?,
    val maxDurationMs: Long?,
    val publicPayload: TournamentPublicQuestionPayload,
)

/** Закрытый вопрос в ответе операции: только идентичность, никаких цифр оценки. */
data class TournamentLastClosed(
    val questionIndex: Int,
    val questionId: String,
    val kind: String,
)

/**
 * Квитанция ready-ACK из `safeResponse` (ONLINE-O2 §4): серверные факты выдачи — час выдачи,
 * авторитетный край ответа и час подтверждения. Ключ квитанции — тройка
 * (сессия, вопрос, окно); повтор ключа возвращает ту же квитанцию.
 */
data class TournamentReadyAckReceipt(
    val schemaVersion: Long,
    val questionIndex: Int,
    val windowId: String,
    val questionId: String,
    val issuedAtMs: Long,
    val answerDeadlineMs: Long,
    val ackedAtMs: Long,
)

/** Состоявшийся результат сессии: проценты и часы — без цифр ответа и материалов форм. */
data class TournamentSessionResult(
    val percentScore: Double,
    val elapsedMs: Long,
    val completedAtMs: Long,
)

/**
 * Ответ операции/чтения сессии — зеркало `safeResponse`. Члены приходят по мере вызова:
 * у операции — статус и (по виду операции) событие, сессия, встреча, вопрос; у чтения
 * сессии — окно, вопрос, результат, а [schemaVersion]/[operationId]/[status] у чтения
 * отсутствуют вовсе (сервер выводит в проекцию только определённые поля). Токен ссылки
 * организатора (`admissionLink`) клиентским типом не переносится: разбор допускает ключ, но
 * значения его не декодирует ни в какой тип и ни в какой журнал.
 */
data class TournamentOperationView(
    val schemaVersion: Long?,
    val operationId: String?,
    val status: String?,
    val serverNowMs: Long?,
    val deadlineReached: Boolean?,
    val questionDeadlineReached: Boolean?,
    val deliveredNowMs: Long?,
    val window: TournamentWindowView?,
    val event: TournamentEventView?,
    val session: TournamentSessionView?,
    val sessions: List<TournamentSessionView>?,
    val meeting: TournamentMeetingView?,
    val currentQuestion: TournamentPublicQuestion?,
    val lastClosed: TournamentLastClosed?,
    val readyAck: TournamentReadyAckReceipt?,
    val result: TournamentSessionResult?,
)

/** Ответ чтения сессии (`fetchCourseTournamentSession`) — те же члены `safeResponse`. */
typealias TournamentSessionRead = TournamentOperationView

/** Ответ `recordCourseTournamentDownload`: create-only запись; повтор приезжает с флагом. */
data class TournamentDownloadRecord(
    val courseId: String,
    val downloadedAtMs: Long,
    val replayed: Boolean,
)

/** Ответ `prepareCourseTournamentBank`: снимок банка и его состав. */
data class TournamentPrepareReply(
    val snapshotId: String,
    val timingVersion: String,
    val counts: TournamentPrepareCounts,
)

/** Состав банка: сколько лёгких, сложных и всего вопросов отобрано. */
data class TournamentPrepareCounts(
    val easy: Int,
    val hard: Int,
    val total: Int,
)

/** Запрос чтения сессии; [ownerUid] — для забора смены аккаунта на границе. */
data class TournamentSessionReadRequest(
    val ownerUid: String,
    val sessionId: String,
)
