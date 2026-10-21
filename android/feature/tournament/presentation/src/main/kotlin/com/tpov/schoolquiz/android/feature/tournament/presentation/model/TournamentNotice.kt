package com.tpov.schoolquiz.android.feature.tournament.presentation.model

import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.syncErrorOrNull
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentRemoteFailure
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentRemoteFailureKind

/**
 * Честное состояние обращения к соревнованию (AD-21) — один словарь на все четыре маршрута:
 * загрузка/пустота решаются состоянием экрана, а здесь живёт исход обращения и что с ним делать.
 *
 * Ветвь решается структурной причиной ([TournamentRemoteFailure], [SyncError]) — никогда текстом
 * сообщения. Отказ сервера поимённо ([SyncError.Refused]) приезжает причиной из таблицы
 * `TournamentRefusals`; клиент знает не все имена будущего сервера — неизвестное остаётся
 * честным «неизвестно», а не выдуманным отказом.
 */
sealed interface TournamentNotice {
    /** Экран ещё ничего не спрашивал. */
    data object NotLoaded : TournamentNotice

    /** Последнее обращение кончилось хорошо; можно работать дальше. */
    data object Ready : TournamentNotice

    /** Аккаунта нет — отправлять некем; вход решает выше. */
    data object AccountUnavailable : TournamentNotice

    /** Аккаунт сменился под запросом: ответ чужой смены не признан. */
    data object AccountChanged : TournamentNotice

    /** Сети нет: повтор безопасен и нужен. */
    data object Offline : TournamentNotice

    /** Предусловие ещё не выполнено (другая мутация ещё летит) — ждать, не повторять силой. */
    data object Pending : TournamentNotice

    /** Сервер посмотрел и отказал поимённо; повтором не лечится. */
    data class Refused(
        val reason: TournamentRefusal,
        /** Имя причины как его назвал сервер — для показа и журнала, не для ветвления. */
        val serverReason: String,
    ) : TournamentNotice

    /** Неизвестно: скорее временно, повтор того же запроса предписан. */
    data object Retry : TournamentNotice
}

/** Закрытый набор именованных отказов соревнования, которые различает клиент. */
enum class TournamentRefusal {
    /** Квалификационное окно события закрыто или ещё не открыто (часы сервера). */
    OUTSIDE_QUALIFICATION_WINDOW,

    /** Основания допуска нет: ни записи о скачивании, ни токена ссылки. */
    NO_ADMISSION_BASIS,

    /** Призовой фонд не покрыт депозитом — приговор CREATE. */
    INSUFFICIENT_BOXES,

    /** Источник не достоин соревнования (не reviewed/не опубликован). */
    SOURCE_NOT_ELIGIBLE,

    /** Повтор того же ключа с другим телом — правка замороженного намерения. */
    CONFLICTING_REPLAY,

    /** Прочий именованный отказ: повтор бессмыслен, причина показывается как есть. */
    OTHER,

    /**
     * Граница согласования матча: слот или готовность вне окна, которое ведёт сервер
     * (предложение до C, подтверждение до C и не позже слота, готовность в своём часу).
     */
    MEETING_BOUNDARY,
}

/**
 * Исход неудачи -> honest notice. Ветвление — только структурное: род отказа и имя причины из
 * закрытой таблицы; тексты сервера здесь не разбираются и не ветвят.
 *
 * [agreementSurface] различает операции, для которых сервер называет `FAILED_PRECONDITION`:
 * на поверхности согласования матча (PROPOSE/CONFIRM/READY/READ встречи) это граница встречи
 * (5.7), всюду остальное — старт квалификации, чтения сетки и сессии — тот же род отказа
 * честнее показывать как OTHER с именем сервера: «Qualification is closed» и окно квалификации
 * приезжают тем же именем и к границе встречи отношения не имеют.
 */
fun failureNotice(
    failure: Throwable,
    agreementSurface: Boolean = false,
): TournamentNotice =
    when (failure) {
        is TournamentRemoteFailure ->
            when (failure.kind) {
                TournamentRemoteFailureKind.ACCOUNT_UNAVAILABLE -> TournamentNotice.AccountUnavailable
                TournamentRemoteFailureKind.ACCOUNT_CHANGED -> TournamentNotice.AccountChanged
                TournamentRemoteFailureKind.MALFORMED_RESPONSE -> TournamentNotice.Retry
            }
        else ->
            when (val sync = failure.syncErrorOrNull()) {
                SyncError.NoNetwork -> TournamentNotice.Offline
                SyncError.PreconditionNotMet -> TournamentNotice.Pending
                is SyncError.Refused ->
                    TournamentNotice.Refused(
                        reason = refusalOf(sync.reason, agreementSurface),
                        serverReason = sync.reason,
                    )
                SyncError.TermsConsentRequired,
                is SyncError.SettlementAlreadyRecorded,
                is SyncError.VersionConflict,
                is SyncError.Unknown,
                null,
                -> TournamentNotice.Retry
            }
    }

/** Имя причины -> ветвь клиента; неизвестное имя — OTHER, никогда краш. */
internal fun refusalOf(
    reason: String,
    agreementSurface: Boolean = false,
): TournamentRefusal =
    when (reason) {
        "OUTSIDE_QUALIFICATION_WINDOW" -> TournamentRefusal.OUTSIDE_QUALIFICATION_WINDOW
        "NO_ADMISSION_BASIS" -> TournamentRefusal.NO_ADMISSION_BASIS
        "INSUFFICIENT_BOXES" -> TournamentRefusal.INSUFFICIENT_BOXES
        "SOURCE_NOT_ELIGIBLE" -> TournamentRefusal.SOURCE_NOT_ELIGIBLE
        "CONFLICTING_REPLAY" -> TournamentRefusal.CONFLICTING_REPLAY
        // Граница согласования называется FAILED_PRECONDITION только на поверхности согласования
        // (5.7); тот же род отказа в других операциях — OTHER, а не встречная ветвь.
        "FAILED_PRECONDITION" -> if (agreementSurface) TournamentRefusal.MEETING_BOUNDARY else TournamentRefusal.OTHER
        else -> TournamentRefusal.OTHER
    }
