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
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.QuestionWindowRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.QuestionWindowWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionDeliveryVerdict
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.QuestionWindowView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/**
 * Два явных вызова окна перехода (ONLINE-O2 §3; история 3.2). Ни подготовки входа, ни денег, ни
 * автоматических повторов, ни локальных записей: durable-половина перехода живёт в outbox и
 * чекпоинте рановера. Личность на сервере поставляет Auth (`requireAuthUid` приёмника), телом
 * клиент только называет переход; сессионные заборы отвергают протухшие ответы, включая A→B→A.
 */
class FirebaseQuestionWindowRemoteDataSource internal constructor(
    private val call: suspend (String, Map<String, Any?>) -> Any?,
    private val sessions: OutboxSessionProvider,
    private val networkMonitor: NetworkMonitor,
    private val dispatcher: CoroutineDispatcher,
    private val parser: QuestionWindowWireParser = QuestionWindowWireParser(),
) : QuestionWindowRemoteDataSource {
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
                // Только структурные метаданные SDK: приватные message/cause наружу не выходят.
                throw QuestionWindowCallableException(e.code.name, e.details)
            }
        },
        sessions = sessions,
        networkMonitor = networkMonitor,
        dispatcher = dispatcher,
    )

    override suspend fun beginWindow(request: QuestionWindowRequest): Result<QuestionWindowView> = exchange {
        val reply =
            call(
                BEGIN,
                buildMap {
                    put("attemptId", request.attemptId)
                    put("lessonId", request.lessonId)
                    put("lessonVersion", request.lessonVersion)
                    put("completedQuestionIndex", request.completedQuestionIndex)
                    put("nextQuestionId", request.nextQuestionId)
                    put("difficulty", if (request.isHard) "HARD" else "EASY")
                },
            )
        parser.parseWindowView(wire(reply))
    }

    override suspend fun fetchNext(
        attemptId: String,
        completedQuestionIndex: Int,
    ): Result<QuestionDeliveryVerdict> = exchange {
        val reply =
            call(
                FETCH,
                buildMap {
                    put("attemptId", attemptId)
                    put("completedQuestionIndex", completedQuestionIndex)
                },
            )
        parser.parseDeliveryVerdict(wire(reply))
    }

    /** Один забор сессии на границе и перепроверки на каждом возврате; отмена — без маскировки. */
    private suspend fun <T> exchange(block: suspend () -> T): Result<T> {
        coroutineContext.ensureActive()
        val session = sessions.current() ?: return failed(QuestionWindowFailureKind.ACCOUNT_UNAVAILABLE)
        return withContext(dispatcher) {
            try {
                requireSession(session)
                val online = networkMonitor.isOnline()
                coroutineContext.ensureActive()
                requireSession(session)
                if (!online) return@withContext Result.failure(SyncFailure(SyncError.NoNetwork))
                val value = block()
                coroutineContext.ensureActive()
                requireSession(session)
                Result.success(value)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                coroutineContext.ensureActive()
                if (sessions.current() != session) {
                    failed(QuestionWindowFailureKind.ACCOUNT_CHANGED)
                } else {
                    failure(e)
                }
            }
        }
    }

    private fun requireSession(expected: OutboxSession) {
        if (sessions.current() != expected) {
            throw QuestionWindowFailure(QuestionWindowFailureKind.ACCOUNT_CHANGED)
        }
    }

    private fun wire(reply: Any?): String =
        try {
            mutationResponseJson(reply)
        } catch (_: IllegalArgumentException) {
            throw QuestionWindowFailure(QuestionWindowFailureKind.MALFORMED_RESPONSE)
        } catch (_: IllegalStateException) {
            throw QuestionWindowFailure(QuestionWindowFailureKind.MALFORMED_RESPONSE)
        }

    private fun <T> failure(error: Exception): Result<T> = when (error) {
        is QuestionWindowFailure -> Result.failure(error)
        is QuestionWindowWireParser.MalformedWindowWire -> failed(QuestionWindowFailureKind.MALFORMED_RESPONSE)
        is QuestionWindowCallableException -> failed(callableFailure(error))
        is IOException -> Result.failure(SyncFailure(SyncError.NoNetwork))
        else -> failed(QuestionWindowFailureKind.SERVER_FAILURE)
    }

    private fun callableFailure(error: QuestionWindowCallableException): QuestionWindowFailureKind =
        when (error.code) {
            // Приёмник 2.6 отказывает поимённо: unauthenticated и invalid-argument; детали без reason.
            "UNAUTHENTICATED", "PERMISSION_DENIED" -> QuestionWindowFailureKind.ACCOUNT_UNAVAILABLE
            "INVALID_ARGUMENT" -> QuestionWindowFailureKind.INVALID_REQUEST
            else -> QuestionWindowFailureKind.SERVER_FAILURE
        }

    private fun <T> failed(kind: QuestionWindowFailureKind): Result<T> =
        Result.failure(QuestionWindowFailure(kind))

    internal class QuestionWindowCallableException(
        val code: String,
        val details: Any?,
    ) : Exception(code)

    internal companion object {
        const val BEGIN = "beginLessonQuestionWindow"
        const val FETCH = "fetchNextLessonQuestion"
    }
}
