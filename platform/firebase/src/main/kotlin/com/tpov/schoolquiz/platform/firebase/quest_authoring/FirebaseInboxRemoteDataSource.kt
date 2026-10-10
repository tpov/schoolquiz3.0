package com.tpov.schoolquiz.platform.firebase.quest_authoring

import com.google.firebase.functions.FirebaseFunctions
import com.tpov.schoolquiz.platform.firebase.network.toSyncError
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.mapper.InboxWireParser
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.InboxRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxAnchor
import com.tpov.schoolquiz.shared.feature.quest_authoring.domain.model.InboxPage
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

/**
 * Транспорт общего inbox (AD-20, история 4.9).
 *
 * Имя коллбла — ровно регистрация в `functions/index.js`. Владелец выводится сервером из Auth,
 * поэтому ключа владельца в полезной нагрузке нет; транспортные отказы едут как [SyncFailure].
 */
class FirebaseInboxRemoteDataSource(
    private val functions: FirebaseFunctions,
) : InboxRemoteDataSource {
    override suspend fun listInboxItems(
        pageSize: Int,
        after: InboxAnchor?,
    ): InboxPage {
        val payload =
            buildMap<String, Any?> {
                put(PAGE_SIZE, pageSize)
                after?.let { put(AFTER, mapOf(AT_MS to it.atMs, ID to it.id)) }
            }
        return try {
            val reply =
                functions
                    .getHttpsCallable(CALLABLE)
                    .withAppTimeout()
                    .call(payload)
                    .await()
                    .data
            InboxWireParser.inboxPage(reply)
        } catch (error: CancellationException) {
            throw error
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            throw SyncFailure(error.toSyncError(), error)
        }
    }
}

private const val CALLABLE = "listInboxItems"
private const val PAGE_SIZE = "pageSize"
private const val AFTER = "after"
private const val AT_MS = "atMs"
private const val ID = "id"
