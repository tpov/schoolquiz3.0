package com.tpov.schoolquiz.platform.firebase.lesson_runner

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.PrivateQuizDoorRefusal
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.PrivateQuizRunRemoteDataSource
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

/**
 * Транспорт прогона приватного квиза (AD-29, история 8.4).
 *
 * Имена коллблов — ровно регистрации в `functions/index.js` (сторож —
 * [PrivateQuizCallableNamesTest]). Отказ двери приезжает `FAILED_PRECONDITION`/`NOT_FOUND`
 * с машиночитаемым кодом в деталях; он и есть типизированный исход, человеческий текст
 * разбору не подставляется.
 */
class FirebasePrivateQuizRunRemoteDataSource(
    private val functions: FirebaseFunctions,
) : PrivateQuizRunRemoteDataSource {

    override suspend fun start(
        ownerUid: String,
        catalogId: String,
        questId: String,
        startId: String,
    ): Any? =
        call(
            PrivateQuizCallableNames.START,
            mapOf(
                OWNER_UID to ownerUid,
                CATALOG_ID to catalogId,
                QUEST_ID to questId,
                START_ID to startId,
            ),
        )

    override suspend fun fetchRun(
        ownerUid: String,
        catalogId: String,
        questId: String,
        startId: String,
    ): Any? =
        call(
            PrivateQuizCallableNames.FETCH_RUN,
            basePayload(ownerUid, catalogId, questId, startId),
        )

    override suspend fun readResult(
        ownerUid: String,
        catalogId: String,
        questId: String,
        startId: String,
    ): Any? =
        call(
            PrivateQuizCallableNames.READ_RESULT,
            basePayload(ownerUid, catalogId, questId, startId),
        )

    private fun basePayload(
        ownerUid: String,
        catalogId: String,
        questId: String,
        startId: String,
    ): Map<String, String> =
        mapOf(
            OWNER_UID to ownerUid,
            CATALOG_ID to catalogId,
            QUEST_ID to questId,
            START_ID to startId,
        )

    private suspend fun call(
        name: String,
        payload: Map<String, String>,
    ): Any? =
        try {
            functions
                .getHttpsCallable(name)
                .withAppTimeout()
                .call(payload)
                .await()
                .data
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFunctionsException) {
            // Машиночитаемый код двери живёт в деталях отказа; пустота — немой отказ транспорта.
            throw PrivateQuizDoorRefusal(reasonFrom(e.details), e)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            throw PrivateQuizDoorRefusal(reason = null, cause = e)
        }

    private fun reasonFrom(details: Any?): String? =
        (details as? Map<*, *>)?.get("reason") as? String
}

/** Имена callable-поверхности приватного квиза — ровно регистрации в `functions/index.js`. */
internal object PrivateQuizCallableNames {
    const val START = "startPrivateQuizAttempt"
    const val FETCH_RUN = "fetchPrivateQuizRun"
    const val READ_RESULT = "readPrivateQuizResult"
}

private const val OWNER_UID = "ownerUid"
private const val CATALOG_ID = "catalogId"
private const val QUEST_ID = "questId"
private const val START_ID = "startId"
