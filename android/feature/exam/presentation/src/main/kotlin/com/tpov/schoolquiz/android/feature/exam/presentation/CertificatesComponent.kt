package com.tpov.schoolquiz.android.feature.exam.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamCertificateRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Поверхность сертификатов (CAP-22): последний заработанный выпуск с явным ожиданием подписи и
 * вход публичной верификации вне входа. Экран не умеет выпускать, отзывать или продлевать: всё,
 * кроме чтения, — владелец (Q-EX-5).
 */
interface CertificatesComponent {
    val view: Value<CertificateView>

    fun onLoadMine()

    fun onVerifyRequested(issuanceId: String)
}

class DefaultCertificatesComponent(
    componentContext: ComponentContext,
    remote: ExamCertificateRemoteDataSource,
    mainContext: CoroutineContext = Dispatchers.Main.immediate,
) : CertificatesComponent {

    private val instance =
        componentContext.instanceKeeper.getOrCreate("certificates_instance") {
            CertificatesInstance(remote, mainContext)
        }

    override val view: Value<CertificateView> =
        MutableValue(instance.machine.view.value).also { value ->
            instance.launchCollection { collected -> value.value = collected }
        }

    override fun onLoadMine() = instance.machine.loadMine()

    override fun onVerifyRequested(issuanceId: String) = instance.machine.verify(issuanceId)

    private class CertificatesInstance(
        remote: ExamCertificateRemoteDataSource,
        mainContext: CoroutineContext,
    ) : InstanceKeeper.Instance {
        val machine = CertificateListStateMachine(remote, CoroutineScope(SupervisorJob() + mainContext))

        /** Сбор потока машины в область холдера, без раскрытия области наружу. */
        fun launchCollection(onEach: (CertificateView) -> Unit) {
            machine.scope.launch {
                machine.view.collect { onEach(it) }
            }
        }

        override fun onDestroy() {
            machine.scope.cancel()
        }
    }
}

/** Фабрика для DI. */
fun interface CertificatesComponentFactory {
    fun create(context: ComponentContext): CertificatesComponent
}
