package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.android.feature.tournament.presentation.model.failureNotice
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.tournament.data.repository.TournamentCompetitionRepository
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentDownloadRequest
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView
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

/** Допуск поверх 5.9-слоя: create-only запись скачивания (повтор — с флагом сервера, а не с
 * вторым эффектом), окно события по серверному «сейчас», отказы — честные состояния. */
class DefaultTournamentAdmissionComponent(
    context: ComponentContext,
    private val repository: TournamentCompetitionRepository,
    private val sessions: OutboxSessionProvider,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) : TournamentAdmissionComponent,
    ComponentContext by context {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutable = MutableStateFlow(AdmissionUiState(session = sessions.current()))
    override val state: StateFlow<AdmissionUiState> = mutable.asStateFlow()

    /** Личность записи скачивания: рождается на первом нажатии и не меняется при повторе. */
    private var downloadOperationId: String? = null
    private var downloadCourseId: String? = null

    init {
        lifecycle.doOnDestroy { scope.cancel() }
    }

    override fun onOpenEvent(
        event: TournamentEventView,
        serverNowMs: Long,
    ) {
        downloadOperationId = null
        downloadCourseId = event.source.takeIf { it.kind == "COURSE" }?.id
        mutable.update {
            AdmissionUiState(
                session = sessions.current(),
                event = event,
                serverNowMs = serverNowMs,
                notice = TournamentNotice.Ready,
            )
        }
    }

    override fun onClose() {
        mutable.update { AdmissionUiState(session = sessions.current()) }
    }

    override fun onRecordDownloadClick() {
        val state = mutable.value
        val courseId = downloadCourseId ?: state.event?.source?.takeIf { it.kind == "COURSE" }?.id ?: return
        if (state.busy) return
        val operationId = downloadOperationId ?: ids().also { downloadOperationId = it }
        act { session ->
            mutable.update { it.copy(notice = TournamentNotice.Ready) }
            val record =
                repository
                    .recordDownload(
                        TournamentDownloadRequest(
                            ownerUid = session.ownerUid,
                            operationId = operationId,
                            courseId = courseId,
                        ),
                    ).getOrThrow()
            // Запись применяется, только пока открыто то самое событие: ответ, летевший по
            // прежнему курсу, чужому событию не достаётся — у него своя запись скачивания.
            val openCourseId = mutable.value.event?.source?.takeIf { it.kind == "COURSE" }?.id
            if (openCourseId == courseId) {
                mutable.update { it.copy(download = record, notice = TournamentNotice.Ready) }
            }
        }
    }

    override fun onContinueClick() {
        // Решение о переходе принимает экран; компонент только фиксирует, что событие открыто.
        mutable.update { it.copy(notice = TournamentNotice.Ready) }
    }

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
                mutable.update {
                    it.copy(
                        notice =
                            if (sessions.current() == session) {
                                failureNotice(
                                    failure,
                                )
                            } else {
                                TournamentNotice.AccountChanged
                            },
                    )
                }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }
}
