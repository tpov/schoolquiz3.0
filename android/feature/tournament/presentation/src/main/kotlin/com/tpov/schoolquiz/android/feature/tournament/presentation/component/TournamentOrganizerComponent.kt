package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrepareReply
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrizeRow
import kotlinx.coroutines.flow.StateFlow

/**
 * Маршрут организатора (история 5.10): подготовка банка reviewed-курса -> ввод призового списка ->
 * согласие с замороженными условиями -> CREATE -> эскроу-читалка. Согласие замораживает условия
 * (строки призов и фонд) до подтверждения; CREATE едет под одним operationId иeventId при любой
 * повторной попытке — серверная идемпотентность не даёт задвоить резерв.
 */
interface TournamentOrganizerComponent {
    val state: StateFlow<OrganizerUiState>

    /** Ввод идентификатора источника (reviewed-курс). */
    fun onSourceIdChange(value: String)

    /** Подготовка банка источника под этот аккаунт. */
    fun onPrepareClick()

    /** Открытие согласия: условия замораживаются в диалоге. */
    fun onShowConsentClick()

    /** Закрытие согласия без следа. */
    fun onDismissConsent(dialogId: String)

    /** Подтверждение согласия: CREATE под замороженными условиями. */
    fun onConfirmCreate(dialogId: String)

    /** Повтор CREATE тем же operationId/eventId — никогда новой личностью операции. */
    fun onRetryCreateClick()

    /** Новый цикл создания после успеха: созданное событие и личность операции уходят. */
    fun onNewCreateClick()

    /** Перечитывание эскроу созданного события. */
    fun onRefreshClick()
}

data class OrganizerUiState(
    val session: OutboxSession? = null,
    val sourceId: String = "",
    val prepare: TournamentPrepareReply? = null,
    val prizeRows: List<TournamentPrizeRow> = CANONICAL_PRIZE_ROWS,
    val consent: OrganizerConsent? = null,
    val created: TournamentEventView? = null,
    val busy: Boolean = false,
    val notice: TournamentNotice = TournamentNotice.NotLoaded,
    /** Последний CREATE кончился отказом: повтор той же личностью операции показан игроку. */
    val canRetryCreate: Boolean = false,
) {
    /** Сумма призового фонда — депозит CREATE по умолчанию: покрывает обязательства ровно. */
    val fundBoxes: Long get() = prizeRows.sumOf { it.amountBoxes }

    val hasPrepare: Boolean get() = prepare != null
}

/** Замороженные условия согласия: не связаны с последующим обновлением, не хранятся как авторитет. */
data class OrganizerConsent(
    val dialogId: String,
    val sourceId: String,
    val snapshotId: String,
    val startsAtMs: Long,
    val prizeRows: List<TournamentPrizeRow>,
    val fundBoxes: Long,
)

/** Принятая призовая схема v1 (история 5.4): {5,3,1} с двумя третьими местами. */
val CANONICAL_PRIZE_ROWS: List<TournamentPrizeRow> =
    listOf(
        TournamentPrizeRow(place = 1, amountBoxes = 5),
        TournamentPrizeRow(place = 2, amountBoxes = 3),
        TournamentPrizeRow(place = 3, amountBoxes = 1),
        TournamentPrizeRow(place = 3, amountBoxes = 1),
    )
