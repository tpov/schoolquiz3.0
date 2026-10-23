package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.PrivateQuizRunnerRequest
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.event.RunnerEvent
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContentParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizEntry
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.PrivateQuizEntryRefusal
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UserAnswerDraft
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.PrivateQuizRunRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.use_case.StartPrivateQuizAttemptUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.datetime.Clock

interface PrivateQuizRunnerComponent {
    val state: StateFlow<PrivateQuizRunUiState>

    /** Название квиза — заголовок экрана, из списка, а не из результата. */
    val title: String

    /** Навигационные события машины: «назад» после выхода из захода. */
    val events: Flow<RunnerEvent>

    fun onDraftChanged(draft: UserAnswerDraft)

    fun onAnswer()

    fun onTimeout()

    fun onRefreshResult()

    /** Выход из захода: попытка остаётся начатой, закроет её серверный тайм-аут (AD-29). */
    fun onBack()
}

/**
 * Экран прогона приватного квиза (AD-29, история 8.4): дверь → один заход → подача очередью →
 * результат по текущей настройке showResult.
 *
 * Машина живёт в InstanceKeeper: поворот и фон её не убивают (техническое восстановление в
 * пределах исходных дедлайнов); смерть процесса закрывает попытку серверным тайм-аутом —
 * продолжения не существует, и это слово сервера, а не UI (AD-31).
 *
 * Вход только под подписанным аккаунтом: гостевого/парольного пути не существует (AD-29), и
 * без сессии дверь отвечает именованным отказом AUTH_REQUIRED.
 */
class DefaultPrivateQuizRunnerComponent(
    componentContext: ComponentContext,
    private val request: PrivateQuizRunnerRequest,
    startPrivateQuizAttempt: StartPrivateQuizAttemptUseCase,
    repository: PrivateQuizRunRepository,
    currentUid: suspend () -> String?,
    parser: QuestionContentParser,
    mainContext: CoroutineDispatcher = Dispatchers.Main.immediate,
) : PrivateQuizRunnerComponent, ComponentContext by componentContext {
    private val componentScope = CoroutineScope(SupervisorJob() + mainContext)
    private val channel = Channel<RunnerEvent>(Channel.BUFFERED)

    /** Ключ намерения этой сессии; повтор входа на том же экране едет с ним же (AD-6). */
    private var currentStartId: String = ""

    private val machine =
        instanceKeeper.getOrCreate(MACHINE_KEY) {
            PrivateQuizRunMachine(
                request = request,
                startDoor = { existingStartId ->
                    val entry =
                        if (currentUid().orEmpty().isBlank()) {
                            PrivateQuizEntry.Refused(PrivateQuizEntryRefusal.AUTH_REQUIRED)
                        } else {
                            startPrivateQuizAttempt(
                                ownerUid = request.ownerUid,
                                catalogId = request.catalogId,
                                questId = request.questId,
                                existingStartId = existingStartId,
                            )
                        }
                    if (entry is PrivateQuizEntry.Ready) currentStartId = entry.run.startId
                    entry
                },
                submit = { submission ->
                    repository.submitAttempt(
                        ownerUid = currentUid().orEmpty(),
                        catalogId = request.catalogId,
                        questId = request.questId,
                        startId = currentStartId,
                        submission = submission,
                    )
                },
                readResult = {
                    repository.readOwnResult(
                        ownerUid = currentUid().orEmpty(),
                        catalogId = request.catalogId,
                        questId = request.questId,
                        startId = currentStartId,
                    )
                },
                parser = parser,
                clock = Clock.System,
                scope = componentScope,
            )
        }

    init {
        lifecycle.doOnDestroy {
            componentScope.cancel()
            channel.close()
        }
        machine.openDoor(existingStartId = currentStartId.ifBlank { null })
    }

    override val state: StateFlow<PrivateQuizRunUiState> = machine.state

    override val events: Flow<RunnerEvent> = channel.receiveAsFlow()

    override val title: String = request.title

    override fun onDraftChanged(draft: UserAnswerDraft) = machine.onDraftChanged(draft)

    override fun onAnswer() = machine.onAnswer()

    override fun onTimeout() = machine.onTimeout()

    override fun onRefreshResult() = machine.onRefreshResult()

    override fun onBack() {
        // Попытка остаётся начатой: сервер закроет её тайм-аутом в срок попытки (AD-29);
        // двойной расход лимита невозможен — новый вход станет новой дверью, а не повтором.
        channel.trySend(RunnerEvent.NavigateBack)
        Unit
    }

    private companion object {
        private val MACHINE_KEY = Any()
    }
}
