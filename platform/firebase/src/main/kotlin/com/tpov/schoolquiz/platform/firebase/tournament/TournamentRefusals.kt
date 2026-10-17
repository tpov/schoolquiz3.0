package com.tpov.schoolquiz.platform.firebase.tournament

import com.tpov.schoolquiz.platform.firebase.network.syncErrorForCode
import com.tpov.schoolquiz.shared.core.network.SyncError

/**
 * Таблица решений по структурным причинам соревнования: `details.reason` -> ветвь [SyncError].
 *
 * Сервер всегда называет причину именем (`callCourseTournament` кладёт свой `reason` в детали
 * HttpsError), поэтому решение читает причину структурно и никогда — текст сообщения (AD-15).
 * Отображение повторяет таблицу сервера и общую таблицу `syncErrorForCode`: причина, которую
 * сервер сам отправляет в «unavailable», повторяема; отказ поимённо — окончателен; причина, которой
 * клиент не знает (будущий сервер), — типовое «неизвестно», никогда не падение.
 */
internal fun tournamentSyncError(
    code: String,
    details: Any?,
    message: String?,
    causedByIo: Boolean,
): SyncError {
    val reason = (details as? Map<*, *>)?.get("reason") as? String ?: return syncErrorForCode(code, message, details, causedByIo)
    return when (reason) {
        // Прямые отказы: повтор ничего не изменит.
        "UNAUTHENTICATED",
        "PERMISSION_DENIED",
        "INVALID_ARGUMENT",
        "NOT_FOUND",
        "ALREADY_EXISTS",
        "CONFLICTING_REPLAY",
        "PREPARATION_REUSE",
        "STALE_PREPARATION",
        // Источник не достоин соревнования и бюджет документа исчерпан — приговоры подготовки
        // и CREATE, повтором не лечится: сервер называет их поимённо (`tournament-source-bank.js`,
        // `tournament-service.js`), молчаливое «неизвестно» сделал бы повтор безнадёжным.
        "SOURCE_NOT_ELIGIBLE",
        "RESOURCE_EXHAUSTED",
        "NO_ADMISSION_BASIS",
        "OUTSIDE_QUALIFICATION_WINDOW",
        "QUESTION_ALREADY_CLOSED",
        "ANSWER_PENDING",
        "INVALID_SOURCE",
        "INVALID_BANK",
        "INVALID_RANKING",
        "INVALID_RESULTS",
        "INSUFFICIENT_QUESTIONS",
        "INSUFFICIENT_BOXES",
        "FAILED_PRECONDITION",
        -> SyncError.Refused(reason)

        // Сервер занят/ремонтируется или снапшот требует починки: повтор того же operationId
        // безопасен и предписан сервером; сетевого отказа здесь нет, поэтому не NoNetwork.
        "UNAVAILABLE",
        "SCORING_UNAVAILABLE",
        "INCONSISTENT_SESSION",
        "INCONSISTENT_ESCROW",
        "INCONSISTENT_ADMISSION",
        "INCONSISTENT_PREPARATION",
        -> SyncError.Unknown()

        // Банк не влезает в частный документ — приговор источнику, повтор бесполезен.
        "SOURCE_TOO_LARGE" -> SyncError.Refused(reason)

        // Синтез сервера для не-статусного сбоя; транспортный код говорит остальное.
        "SERVER_FAILURE" -> syncErrorForCode(code, message, details, causedByIo)

        // Неизвестная причина будущего сервера — типовое «неизвестно» (повторяемо), не крах.
        else -> SyncError.Unknown()
    }
}
