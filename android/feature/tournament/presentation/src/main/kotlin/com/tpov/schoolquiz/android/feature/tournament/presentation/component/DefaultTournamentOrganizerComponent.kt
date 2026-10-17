package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.failureNotice
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.tournament.data.repository.TournamentCompetitionRepository
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventReadRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentOperateRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentPrepareRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentSourceRef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Состояние организатора живёт компонентом (история 5.10): подготовка и CREATE — прямые вызовы
 * 5.9-слоя; согласие замораживает условия в диалоге; CREATE при повторе едет тем же
 * operationId/eventId, поэтому резерв фонда никогда не задваивается — идемпотентность сервера,
 * а не дисциплина вызывающего.
 */
class DefaultTournamentOrganizerComponent(
    context: ComponentContext,
    private val repository: TournamentCompetitionRepository,
    private val sessions: OutboxSessionProvider,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val ids: () -> String = { UUID.randomUUID().toString() },
    private val nowMs: () -> Long = System::currentTimeMillis,
) : TournamentOrganizerComponent,
    ComponentContext by context {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutable = MutableStateFlow(OrganizerUiState(session = sessions.current()))
    override val state: StateFlow<OrganizerUiState> = mutable.asStateFlow()

    /** Личность попытки CREATE: рождается на согласии и не меняется ни при одной повторной попытке. */
    private var createOperationId: String? = null
    private var createEventId: String? = null
    private var pendingConsent: OrganizerConsent? = null

    init {
        lifecycle.doOnDestroy { scope.cancel() }
    }

    override fun onSourceIdChange(value: String) {
        mutable.update { it.copy(sourceId = value, prepare = null, notice = TournamentNotice.NotLoaded) }
    }

    override fun onPrepareClick() {
        val sourceId = mutable.value.sourceId.trim()
        if (sourceId.isEmpty() || mutable.value.busy) return
        act { session ->
            mutable.update { it.copy(notice = TournamentNotice.Ready) }
            val reply =
                repository
                    .prepare(
                        TournamentPrepareRequest(
                            ownerUid = session.ownerUid,
                            requestId = ids(),
                            source = TournamentSourceRef(kind = SOURCE_COURSE, id = sourceId),
                        ),
                    ).getOrThrow()
            mutable.update { it.copy(prepare = reply, notice = TournamentNotice.Ready) }
        }
    }

    override fun onShowConsentClick() {
        val state = mutable.value
        val prepare = state.prepare ?: return
        if (state.busy || state.created != null) return
        // Новое согласие может нести другое тело (строки призов, фонд): старой личности
        // операции у него быть не должно — иначе повтор поехал бы с другим телом под тем же
        // operationId и сервер честно ответил бы CONFLICTING_REPLAY.
        createOperationId = null
        createEventId = null
        val consent =
            OrganizerConsent(
                dialogId = ids(),
                sourceId = state.sourceId.trim(),
                snapshotId = prepare.snapshotId,
                // Событие открывается сразу: окно квалификации ведут серверные часы, а не эти.
                startsAtMs = nowMs(),
                prizeRows = state.prizeRows,
                fundBoxes = state.fundBoxes,
            )
        pendingConsent = consent
        mutable.update { it.copy(consent = consent, canRetryCreate = false) }
    }

    override fun onDismissConsent(dialogId: String) {
        if (mutable.value.consent?.dialogId == dialogId) {
            pendingConsent = null
            mutable.update { it.copy(consent = null) }
        }
    }

    override fun onConfirmCreate(dialogId: String) {
        val consent = mutable.value.consent ?: return
        if (consent.dialogId != dialogId) return
        mutable.update { it.copy(consent = null) }
        create(consent)
    }

    override fun onRetryCreateClick() {
        val consent = pendingConsent ?: return
        create(consent)
    }

    private fun create(consent: OrganizerConsent) {
        val operationId = createOperationId ?: ids().also { createOperationId = it }
        val eventId = createEventId ?: createEventId().also { createEventId = it }
        act { session ->
            mutable.update { it.copy(notice = TournamentNotice.Ready, canRetryCreate = false) }
            val view =
                try {
                    repository
                        .operate(
                            TournamentOperateRequest.Create(
                                ownerUid = session.ownerUid,
                                operationId = operationId,
                                eventId = eventId,
                                source = TournamentSourceRef(kind = SOURCE_COURSE, id = consent.sourceId),
                                snapshotId = consent.snapshotId,
                                startsAtMs = consent.startsAtMs,
                                prizeRows = consent.prizeRows,
                                fundedBoxes = consent.fundBoxes,
                            ),
                        ).getOrThrow()
                } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                    throw cancelled
                } catch (
                    @Suppress("TooGenericExceptionCaught") failure: Exception,
                ) {
                    // Отказ CREATE повторяем той же личностью операции: повтор показан игроку.
                    if (sessions.current() == session) {
                        mutable.update { it.copy(canRetryCreate = true) }
                    }
                    throw failure
                }
            val event: TournamentEventView = requireNotNull(view.event) { "CREATE response without its event view" }
            mutable.update {
                it.copy(created = event, notice = TournamentNotice.Ready, prepare = null)
            }
        }
    }

    /** Новый цикл создания: замороженное согласие и личность прошлой операции не наследуются. */
    override fun onNewCreateClick() {
        createOperationId = null
        createEventId = null
        pendingConsent = null
        mutable.update {
            it.copy(
                created = null,
                consent = null,
                canRetryCreate = false,
                notice = TournamentNotice.Ready,
            )
        }
    }

    override fun onRefreshClick() {
        val eventId = createEventId ?: mutable.value.created?.eventId ?: return
        act { session ->
            val read = repository.readEvent(TournamentEventReadRequest(session.ownerUid, eventId)).getOrThrow()
            mutable.update { it.copy(created = read.event, notice = TournamentNotice.Ready) }
        }
    }

    /** Один шаг за раз; смена аккаунта под шагом не признаёт ответ чужой смены. */
    private fun act(work: suspend (OutboxSession) -> Unit) {
        if (mutable.value.busy) return
        val session =
            sessions.current() ?: run {
                mutable.update { it.copy(notice = TournamentNotice.AccountUnavailable) }
                return
            }
        mutable.update { it.copy(busy = true, session = session) }
        scope.launch {
            try {
                work(session)
            } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught") failure: Exception,
            ) {
                if (sessions.current() == session) {
                    mutable.update { it.copy(notice = failureNotice(failure)) }
                } else {
                    mutable.update { it.copy(notice = TournamentNotice.AccountChanged) }
                }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        const val SOURCE_COURSE = "COURSE"

        /** Создаваемый eventId: [A-Za-z0-9_-]{16,128}, узнаётся сервером как клиентская личность. */
        fun createEventId(): String = "event_${UUID.randomUUID().toString().replace("-", "")}"
    }
}
