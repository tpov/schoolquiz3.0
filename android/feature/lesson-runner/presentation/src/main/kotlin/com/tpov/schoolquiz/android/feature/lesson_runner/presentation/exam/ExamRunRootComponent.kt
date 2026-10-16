package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.exam

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamSittingRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.coroutines.CoroutineContext

/**
 * Корневой компонент экзаменационного прогона (CAP-21/22): витрина допуска → сидинг → результат.
 *
 * Decompose-компонент без Koin в экранах: зависимости приходят сервисами через фабрику DI.
 * Логика живёт в [ExamRunStateMachine] (JVM-проверяемая), компонент только переживает ротацию
 * через InstanceKeeper и ведёт область корутин.
 */
interface ExamRunRootComponent {

    val view: Value<ExamRunView>

    fun onAcceptTerms(shown: Boolean, acceptedRevision: Long)

    fun onStartClicked()

    fun onAnswerSubmitted(answerJson: String)

    fun onQuestionRendered()

    fun onFinishRequested()

    fun onResultPolled()

    fun onRenewIntent()
}

class ExamRunRequest(
    val mode: ExamStartMode,
    val assessmentId: String,
    val entryPrice: Int,
    val requiredTermsRevision: Long,
) {
    enum class ExamStartMode { THEME_TEST, FINAL_EXAM }
}

internal class DefaultExamRunRootComponent(
    context: ComponentContext,
    request: ExamRunRequest,
    remote: ExamSittingRemoteDataSource,
    mainContext: CoroutineContext = Dispatchers.Main.immediate,
) : ExamRunRootComponent {

    // Экземпляр переживает ротацию и закрывает свою область в onDestroy — как у арены.
    private val instance = context.instanceKeeper.getOrCreate("exam_run_instance") {
        ExamRunInstance(
            machine = ExamRunStateMachine(remote, newOperationId = { UUID.randomUUID().toString() }),
            mainContext = mainContext,
        )
    }

    private val machine get() = instance.machine

    init {
        machine.showEntry(
            mode = when (request.mode) {
                ExamRunRequest.ExamStartMode.THEME_TEST ->
                    com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamMode.THEME_TEST
                ExamRunRequest.ExamStartMode.FINAL_EXAM ->
                    com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamMode.FINAL_EXAM
            },
            assessmentId = request.assessmentId,
            entryPrice = request.entryPrice,
            requiredTermsRevision = request.requiredTermsRevision,
        )
    }

    // Один источник истины: StateFlow машины проецируется в Decompose Value для UI.
    override val view: Value<ExamRunView> = MutableValue(machine.view.value).also { value ->
        instance.scope.launch {
            machine.view.collect { value.value = it }
        }
    }

    override fun onAcceptTerms(shown: Boolean, acceptedRevision: Long) {
        machine.acceptTerms(shown, acceptedRevision)
    }

    override fun onStartClicked() {
        val entry = machine.view.value as? ExamRunView.Entry ?: return
        instance.scope.launch {
            machine.start(entry.mode, entry.assessmentId)
        }
    }

    override fun onAnswerSubmitted(answerJson: String) {
        instance.scope.launch {
            machine.submitAnswer(answerJson)
        }
    }

    override fun onQuestionRendered() {
        instance.scope.launch {
            machine.acknowledgeCurrent()
        }
    }

    override fun onFinishRequested() {
        instance.scope.launch {
            machine.requestFinish()
        }
    }

    override fun onResultPolled() {
        instance.scope.launch {
            machine.fetchResult()
        }
    }

    override fun onRenewIntent() {
        machine.renewStartIntent()
    }
}

/** Холдер ротации: машина и её область корутин переживают пересоздание компонента. */
internal class ExamRunInstance(
    val machine: ExamRunStateMachine,
    mainContext: CoroutineContext,
) : InstanceKeeper.Instance {
    val scope = CoroutineScope(SupervisorJob() + mainContext)

    override fun onDestroy() {
        scope.cancel()
    }
}

/** Фабрика для DI: приложение получает её одним Koin-single'ом. */
fun interface ExamRunComponentFactory {
    fun create(
        context: ComponentContext,
        request: ExamRunRequest,
    ): ExamRunRootComponent

    companion object {
        fun default(remote: ExamSittingRemoteDataSource): ExamRunComponentFactory =
            ExamRunComponentFactory { context, request ->
                DefaultExamRunRootComponent(context, request, remote)
            }
    }
}
