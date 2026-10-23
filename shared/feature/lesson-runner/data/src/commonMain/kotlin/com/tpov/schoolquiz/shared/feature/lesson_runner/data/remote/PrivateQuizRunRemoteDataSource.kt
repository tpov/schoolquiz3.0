package com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote

/**
 * Транспорт прогона приватного квиза (AD-29, история 8.4).
 *
 * Ответы приезжают сырыми (то, что отдал callable), разбор — в [PrivateQuizRunWireParser].
 * Отказ двери — [PrivateQuizDoorRefusal] с машиночитаемым кодом из деталей отказа; чужой
 * человеческий текст контрактом не является.
 */
interface PrivateQuizRunRemoteDataSource {
    suspend fun start(
        ownerUid: String,
        catalogId: String,
        questId: String,
        startId: String,
    ): Any?

    suspend fun fetchRun(
        ownerUid: String,
        catalogId: String,
        questId: String,
        startId: String,
    ): Any?

    suspend fun readResult(
        ownerUid: String,
        catalogId: String,
        questId: String,
        startId: String,
    ): Any?
}

/** Отказ двери или именованный отказ прогона; [reason] — код сервера из деталей отказа. */
class PrivateQuizDoorRefusal(
    val reason: String?,
    cause: Throwable? = null,
) : Exception(reason ?: "Private quiz door refused without a reason", cause)
