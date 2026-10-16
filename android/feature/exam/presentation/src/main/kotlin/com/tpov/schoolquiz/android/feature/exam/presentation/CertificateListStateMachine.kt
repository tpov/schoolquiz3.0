package com.tpov.schoolquiz.android.feature.exam.presentation

import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamCertificateProfileView
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamCertificateRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamVerifyStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamVerifyView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Состояние поверхности сертификатов (CAP-22).
 *
 * Профиль показывает последний ЗАРАБОТАННЫЙ выпуск — по порядку заработка, даже с более низким
 * проходным процентом, — с явным состоянием подписи. Верификация работает вне входа и читает
 * неизменяемый снимок; статус — политика самого факта, не профиля.
 */
sealed interface CertificateView {

    /** Список ещё не прочитан. */
    data object Loading : CertificateView

    /** Сертификатов нет: тема тесты ничего не выпускают, финал не сдавался. */
    data object None : CertificateView

    /** Последний заработанный выпуск и явное состояние подписи. */
    data class Earned(
        val issuanceId: String,
        val courseId: String,
        val percent: Int,
        val passMark: Int,
        val issuedAtMs: Long,
        val expiresAtMs: Long,
        val signingPending: Boolean,
    ) : CertificateView

    /** Результат публичной верификации. */
    data class Verified(
        val issuanceId: String,
        val status: ExamVerifyStatus,
        val snapshotJson: String?,
    ) : CertificateView
}

class CertificateListStateMachine(
    private val remote: ExamCertificateRemoteDataSource,
    val scope: kotlinx.coroutines.CoroutineScope,
) {
    private val state = MutableStateFlow<CertificateView>(CertificateView.Loading)
    val view: StateFlow<CertificateView> = state

    fun loadMine() {
        scope.launch {
            val outcome = remote.fetchMyCertificates().getOrElse {
                state.value = CertificateView.None
                return@launch
            }
            state.value = outcome.toView()
        }
    }

    /** Верификация вне входа: вызов не требует и не использует сессию. */
    fun verify(issuanceId: String) {
        scope.launch {
            val outcome = remote.publicVerify(issuanceId).getOrElse {
                state.value = CertificateView.Verified(
                    issuanceId = issuanceId,
                    status = ExamVerifyStatus.NOT_FOUND,
                    snapshotJson = null,
                )
                return@launch
            }
            state.value = outcome.toView()
        }
    }

    private fun ExamCertificateProfileView.toView(): CertificateView =
        certificate?.let { cert ->
            CertificateView.Earned(
                issuanceId = cert.issuanceId,
                courseId = cert.courseId,
                percent = cert.percent,
                passMark = cert.passMark,
                issuedAtMs = cert.issuedAtMs,
                expiresAtMs = cert.expiresAtMs,
                signingPending = cert.signingState == "PENDING",
            )
        } ?: CertificateView.None

    private fun ExamVerifyView.toView(): CertificateView = CertificateView.Verified(
        issuanceId = issuanceId,
        status = status,
        snapshotJson = snapshotJson,
    )
}
