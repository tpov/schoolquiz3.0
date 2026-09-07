package com.tpov.schoolquiz.platform.firebase.arena

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.tpov.schoolquiz.platform.firebase.network.mutationResponseJson
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaLessonCatalogRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaLessonCatalogWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLessonCatalogRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/**
 * A single authenticated read; never prepares an entry, reserves money or retries automatically.
 * Local owner/generation fences reject stale reads, including A -> B -> A. The only wire fields
 * are pageSize and optional opaque cursor; Auth, not the request body, supplies server identity.
 */
class FirebaseArenaLessonCatalogRemoteDataSource internal constructor(
    private val call: suspend (String, Map<String, Any?>) -> Any?,
    private val sessions: OutboxSessionProvider,
    private val networkMonitor: NetworkMonitor,
    private val dispatcher: CoroutineDispatcher,
    private val parser: ArenaLessonCatalogWireParser = ArenaLessonCatalogWireParser(),
) : ArenaLessonCatalogRemoteDataSource {
    constructor(
        functions: FirebaseFunctions,
        sessions: OutboxSessionProvider,
        networkMonitor: NetworkMonitor,
        dispatcher: CoroutineDispatcher,
    ) : this(
        call = { name, payload ->
            try {
                functions.getHttpsCallable(name).withAppTimeout().call(payload).await().data
            } catch (
                @Suppress("SwallowedException") e: FirebaseFunctionsException,
            ) {
                // Keep only structured SDK metadata; private SDK message/cause must not escape this boundary.
                throw ArenaCatalogCallableException(e.code.name, e.details)
            }
        },
        sessions = sessions,
        networkMonitor = networkMonitor,
        dispatcher = dispatcher,
    )

    override suspend fun list(request: ArenaLessonCatalogRequest): Result<ArenaLessonCatalogPage> =
        withContext(dispatcher) {
            val session =
                sessions.current()
                    ?: return@withContext failed(ArenaLessonCatalogFailureKind.ACCOUNT_UNAVAILABLE)
            if (session.ownerUid != request.ownerUid) {
                return@withContext failed(ArenaLessonCatalogFailureKind.ACCOUNT_CHANGED)
            }
            try {
                val online = networkMonitor.isOnline()
                coroutineContext.ensureActive()
                requireSession(session)
                if (!online) return@withContext Result.failure(SyncFailure(SyncError.NoNetwork))
                val payload =
                    buildMap<String, Any?> {
                        put("pageSize", request.pageSize)
                        request.cursor?.let { put("cursor", it.token) }
                    }
                val reply = call(LIST, payload)
                coroutineContext.ensureActive()
                requireSession(session)
                val page = parser.parse(wire(reply), request)
                requireSession(session)
                Result.success(page)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                // SDK/network boundary only. Cancellation is propagated, raw private exceptions
                // are not retained as UI-facing messages or causes, and stale errors are fenced.
                coroutineContext.ensureActive()
                if (sessions.current() != session) {
                    failed(ArenaLessonCatalogFailureKind.ACCOUNT_CHANGED)
                } else {
                    failure(e)
                }
            }
        }

    private fun requireSession(expected: OutboxSession) {
        if (sessions.current() != expected) {
            throw ArenaLessonCatalogFailure(
                ArenaLessonCatalogFailureKind.ACCOUNT_CHANGED,
            )
        }
    }

    private fun wire(reply: Any?): String =
        try {
            mutationResponseJson(reply)
        } catch (_: IllegalArgumentException) {
            throw ArenaLessonCatalogFailure(ArenaLessonCatalogFailureKind.MALFORMED_RESPONSE)
        } catch (_: IllegalStateException) {
            throw ArenaLessonCatalogFailure(ArenaLessonCatalogFailureKind.MALFORMED_RESPONSE)
        }

    private fun failure(error: Exception): Result<ArenaLessonCatalogPage> =
        when (error) {
            is ArenaLessonCatalogFailure -> Result.failure(error)
            is ArenaCatalogCallableException -> failed(callableFailure(error))
            is IOException -> Result.failure(SyncFailure(SyncError.NoNetwork))
            else -> failed(ArenaLessonCatalogFailureKind.SERVER_FAILURE)
        }

    private fun callableFailure(error: ArenaCatalogCallableException): ArenaLessonCatalogFailureKind {
        val reason = (error.details as? Map<*, *>)?.get("reason") as? String
        return when (error.code to reason) {
            "UNAUTHENTICATED" to "UNAUTHENTICATED" -> ArenaLessonCatalogFailureKind.ACCOUNT_UNAVAILABLE
            "INVALID_ARGUMENT" to "INVALID_ARGUMENT" -> ArenaLessonCatalogFailureKind.INVALID_REQUEST
            "FAILED_PRECONDITION" to "CATALOG_CHANGED" -> ArenaLessonCatalogFailureKind.CATALOG_CHANGED
            "RESOURCE_EXHAUSTED" to "CATALOG_TOO_LARGE" -> ArenaLessonCatalogFailureKind.CATALOG_TOO_LARGE
            "UNAVAILABLE" to "CATALOG_INVALID" -> ArenaLessonCatalogFailureKind.CATALOG_INVALID
            else -> ArenaLessonCatalogFailureKind.SERVER_FAILURE
        }
    }

    private fun failed(kind: ArenaLessonCatalogFailureKind): Result<ArenaLessonCatalogPage> =
        Result.failure(ArenaLessonCatalogFailure(kind))

    internal companion object {
        const val LIST = "listArenaLessons"
    }
}

/** Structured SDK metadata only; message text is never parsed or retained. */
internal class ArenaCatalogCallableException(val code: String, val details: Any?) : Exception()
