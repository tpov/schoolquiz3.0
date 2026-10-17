package com.tpov.schoolquiz.android.feature.tournament.presentation.component

import com.tpov.schoolquiz.android.feature.tournament.presentation.model.TournamentNotice
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentDownloadRecord
import com.tpov.schoolquiz.shared.feature.tournament.domain.model.TournamentEventView
import kotlinx.coroutines.flow.StateFlow

/**
 * Маршрут допуска (история 5.10): серверно-засвидетельствованная запись «этот аккаунт скачал этот
 * курс» с честным флагом повтора и честные состояния окна события по серверным часам. Токен ссылки
 * предъявляется старту квалификации (маршрут участия) — здесь только вход в тот маршрут.
 */
interface TournamentAdmissionComponent {
    val state: StateFlow<AdmissionUiState>

    /** Открытие события для допуска: событие и его серверное «сейчас» прибывают из детали. */
    fun onOpenEvent(
        event: TournamentEventView,
        serverNowMs: Long,
    )

    /** Закрытие маршрута допуска. */
    fun onClose()

    /** Запись скачивания под тем же operationId при повторе; флаг повтора приезжает сервером. */
    fun onRecordDownloadClick()

    /** Переход к участию: событие допущено к старту, старт живёт в маршруте участия. */
    fun onContinueClick()
}

data class AdmissionUiState(
    val session: OutboxSession? = null,
    val event: TournamentEventView? = null,
    val serverNowMs: Long? = null,
    val download: TournamentDownloadRecord? = null,
    val busy: Boolean = false,
    val notice: TournamentNotice = TournamentNotice.NotLoaded,
) {
    /** Честная метка окна по серверным фактам события — не решение о допуске, а его витрина. */
    val outsideWindow: Boolean
        get() {
            val event = event ?: return false
            val now = serverNowMs ?: return false
            return now < event.qualification.startsAtMs || now >= event.qualification.endsAtMs
        }

    /** Запись о скачивании относится к этому событию: только курс-источник события. */
    val downloadFitsEvent: Boolean
        get() {
            val event = event ?: return false
            return event.source.kind == "COURSE"
        }
}
