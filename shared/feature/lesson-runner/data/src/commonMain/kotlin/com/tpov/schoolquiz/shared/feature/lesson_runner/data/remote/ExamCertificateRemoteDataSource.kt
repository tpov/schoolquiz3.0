package com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote

/**
 * Сертификаты — транспорт профиля и подписанный-out верификации (CAP-22).
 *
 * Профиль читает свой проекцию: последний ЗАРАБОТАННЫЙ выпуск по порядку заработка и явное
 * состояние подписи. Верификация — отдельный вызов БЕЗ входа: неизменяемый публичный снимок и
 * статус по собственным датам и ключам факта. Тема тесты сертификатов не выпускают.
 */
interface ExamCertificateRemoteDataSource {

    /** Проекция профиля: последний заработанный выпуск или его отсутствие. */
    suspend fun fetchMyCertificates(): Result<ExamCertificateProfileView>

    /** Публичная верификация без входа: снимок и статус политики. */
    suspend fun publicVerify(issuanceId: String): Result<ExamVerifyView>
}

/** Проекция профиля; `null` — сертификатов ещё нет (тема тесты ничего не выпускают). */
data class ExamCertificateProfileView(
    val certificate: ExamCertificateView?,
)

/** Отображаемый выпуск: данные факта и явное состояние подписи. */
data class ExamCertificateView(
    val issuanceId: String,
    val courseId: String,
    val percent: Int,
    val passMark: Int,
    val issuedAtMs: Long,
    val expiresAtMs: Long,
    val signingState: String,
)

/** Статусы публичной верификации — зеркала `functions/exam-certificates.js`. */
enum class ExamVerifyStatus {
    VALID,
    EXPIRED,
    PENDING,
    UNKNOWN_KEY,
    NOT_FOUND,
}

/** Ответ верификации: статус по собственным датам и ключам факта; снимок при найденном. */
data class ExamVerifyView(
    val found: Boolean,
    val status: ExamVerifyStatus,
    val issuanceId: String,
    val snapshotJson: String? = null,
)
