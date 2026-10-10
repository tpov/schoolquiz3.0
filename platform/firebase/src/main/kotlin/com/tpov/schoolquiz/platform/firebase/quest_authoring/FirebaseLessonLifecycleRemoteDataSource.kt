package com.tpov.schoolquiz.platform.firebase.quest_authoring

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.network.toSyncError
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.LessonLifecycleWireParser
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.LessonLifecycleRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonHistoryPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonLifecyclePage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonRoyaltyPage
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionCommand
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.LessonTransitionDecision
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

/**
 * Транспорт проекций TZ06B и кураторского перехода.
 *
 * Имена коллблов — ровно реестр `functions/index.js` (сторож — [LifecycleCallableNamesTest]).
 * Владелец выводится сервером из Auth, поэтому ключа владельца в полезной нагрузке нет.
 * Транспортные отказы едут как [SyncFailure]; отказ внутри квитанции перехода — не отказ
 * транспорта, а честное решение сервера, он доезжает как [LessonTransitionDecision.Rejected].
 */
class FirebaseLessonLifecycleRemoteDataSource(
    private val functions: FirebaseFunctions,
) : LessonLifecycleRemoteDataSource {
    override suspend fun listOwned(
        afterLessonId: String?,
        pageSize: Int,
    ): LessonLifecyclePage =
        call(LifecycleCallableNames.LIST_OWNED, pagePayload(afterLessonId, pageSize), LessonLifecycleWireParser::lifecyclePage)

    override suspend fun listCurator(
        afterLessonId: String?,
        pageSize: Int,
    ): LessonLifecyclePage =
        call(LifecycleCallableNames.LIST_CURATOR, pagePayload(afterLessonId, pageSize), LessonLifecycleWireParser::lifecyclePage)

    override suspend fun listHistory(
        lessonId: String,
        cursor: String?,
        pageSize: Int,
    ): LessonHistoryPage {
        val payload =
            buildMap<String, Any?> {
                put(LESSON_ID, lessonId)
                put(PAGE_SIZE, pageSize)
                cursor?.let { put(CURSOR, it) }
            }
        return call(LifecycleCallableNames.LIST_HISTORY, payload, LessonLifecycleWireParser::historyPage)
    }

    override suspend fun listRoyalty(
        cursor: String?,
        pageSize: Int,
    ): LessonRoyaltyPage {
        val payload =
            buildMap<String, Any?> {
                put(PAGE_SIZE, pageSize)
                cursor?.let { put(CURSOR, it) }
            }
        return call(LifecycleCallableNames.LIST_ROYALTY, payload, LessonLifecycleWireParser::royaltyPage)
    }

    override suspend fun transition(
        command: LessonTransitionCommand,
        mutationId: String,
    ): LessonTransitionDecision {
        val payload =
            mapOf(
                MUTATION_ID to mutationId,
                LESSON_ID to command.lessonId,
                TARGET_SHELF to command.targetShelf.wire,
                REASON to command.reason,
            )
        return call(LifecycleCallableNames.SET_LESSON_PLACEMENT, payload) {
            LessonLifecycleWireParser.transitionDecision(it, command)
        }
    }

    private suspend fun <T> call(
        name: String,
        payload: Map<String, Any?>,
        decode: (Any?) -> T,
    ): T =
        try {
            val reply =
                functions
                    .getHttpsCallable(name)
                    .withAppTimeout()
                    .call(payload)
                    .await()
                    .data
            decode(reply)
        } catch (error: CancellationException) {
            throw error
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            throw SyncFailure(error.toSyncError(), error)
        }

    private fun pagePayload(
        afterLessonId: String?,
        pageSize: Int,
    ): Map<String, Any?> =
        buildMap {
            put(PAGE_SIZE, pageSize)
            afterLessonId?.let { put(AFTER_LESSON_ID, it) }
        }
}

/** Имена callable-поверхности TZ06B/TZ06C — ровно регистрации в `functions/index.js`. */
internal object LifecycleCallableNames {
    const val LIST_OWNED = "listOwnedLessonLifecycle"
    const val LIST_CURATOR = "listCuratorLessonLifecycle"
    const val LIST_HISTORY = "listLessonLifecycleHistory"
    const val LIST_ROYALTY = "listMyArenaRoyalty"
    const val SET_LESSON_PLACEMENT = "setLessonPlacement"
}

private const val AFTER_LESSON_ID = "afterLessonId"
private const val PAGE_SIZE = "pageSize"
private const val LESSON_ID = "lessonId"
private const val CURSOR = "cursor"
private const val MUTATION_ID = "mutationId"
private const val TARGET_SHELF = "targetShelf"
private const val REASON = "reason"
