package com.tpov.schoolquiz.android.feature.tournament.presentation.model

/**
 * Серверные слова статусов сессии и окна одним словарём (история 5.10): компоненты и экраны
 * сравнивают эти имена через константы, а не по месту строкой — расхождение словаря сближений
 * тогда и только тогда одна правка.
 */
const val SESSION_STARTED = "STARTED"

/** Сессия подведена сервером. */
const val SESSION_SETTLED = "SETTLED"

/** Вопрос/сессия завершены. */
const val SESSION_COMPLETED = "COMPLETED"

/** Сервер засчитал вопрос как просроченный. */
const val SESSION_QUESTION_TIMED_OUT = "QUESTION_TIMED_OUT"

/** Окно следующего вопроса открыто; его край — [com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentWindowView.expiresAtMs]. */
const val WINDOW_OPEN = "OPEN"

/** Окно доставлено в сессию ответом операции (deliverTournamentQuestionWindow) — серверное
 * состояние delivery, предусловие READY_ACK (`tournament-session.js` boundary.state). */
const val WINDOW_DELIVERED = "DELIVERED"

/** Статусы встречи матча (история 5.11) тем же словарём: стадия согласования и её концы. */
const val MEETING_WAITING_FOR_AGREEMENT = "WAITING_FOR_AGREEMENT"

/** Слот согласован, ждём его начала. */
const val MEETING_WAITING_FOR_WINDOW = "WAITING_FOR_WINDOW"

/** Слот наступил, ждём готовности участников. */
const val MEETING_WAITING_FOR_READY = "WAITING_FOR_READY"

/** Техническое поражение: участник не подтвердил готовность. */
const val MEETING_FORFEIT = "FORFEIT"

/** Оба пропустили слот. */
const val MEETING_DOUBLE_OUT = "DOUBLE_OUT"
