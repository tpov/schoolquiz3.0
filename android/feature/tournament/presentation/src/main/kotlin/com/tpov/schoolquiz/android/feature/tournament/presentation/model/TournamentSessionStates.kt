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
