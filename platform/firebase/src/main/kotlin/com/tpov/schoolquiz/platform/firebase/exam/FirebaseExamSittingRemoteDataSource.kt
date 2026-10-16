package com.tpov.schoolquiz.platform.firebase.exam

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.tpov.schoolquiz.platform.firebase.network.mutationResponseJson
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamFinishVerdict
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamSittingRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamTransportFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamTransportFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamSittingWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamTermsWire
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamAckOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamFetchOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamStartOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ExamSubmitOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/**
 * Шесть экзаменационных вызовов (протокол §Calls). Списание делает сервер в транзакции старта;
 * повтор того же operationId возвращает сохранённый результат, поэтому клиентских повторов и
 * локальных записей здесь нет. Личность поставляет Auth; epoch аккаунта в теле отбивает A→B→A.
 * Автоматических повторов нет: решение о повторе принимает режимный адаптер по типу отказа.
 */
class FirebaseExamSittingRemoteDataSource internal constructor(
    private val call: suspend (String, Map<String, Any?>) -> Any?,
    private val sessions: OutboxSessionProvider,
    private val networkMonitor: NetworkMonitor,
    private val dispatcher: CoroutineDispatcher,
    private val parser: ExamSittingWireParser = ExamSittingWireParser(),
) : ExamSittingRemoteDataSource {
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
                throw ExamCallableException(e.code.name, e.details)
            }
        },
        sessions = sessions,
        networkMonitor = networkMonitor,
        dispatcher = dispatcher,
    )

    override suspend fun startThemeTest(
        operationId: String,
        accountEpoch: Long,
        themeId: String,
        terms: ExamTermsWire,
    ): Result<ExamStartOutcome> = exchange {
        val reply = call(
            START_THEME,
            buildMap {
                put("operationId", operationId)
                put("accountEpoch", accountEpoch)
                put("themeId", themeId)
                put("shownTerms", terms.shown)
                put("acceptedTermsRevision", terms.acceptedRevision)
            },
        )
        parser.parseStartOutcome(wire(reply))
    }

    override suspend fun startFinalExam(
        operationId: String,
        accountEpoch: Long,
        courseId: String,
        terms: ExamTermsWire,
    ): Result<ExamStartOutcome> = exchange {
        val reply = call(
            START_FINAL,
            buildMap {
                put("operationId", operationId)
                put("accountEpoch", accountEpoch)
                put("courseId", courseId)
                put("shownTerms", terms.shown)
                put("acceptedTermsRevision", terms.acceptedRevision)
            },
        )
        parser.parseStartOutcome(wire(reply))
    }

    override suspend fun acknowledgeQuestion(
        sittingId: String,
        questionIndex: Int,
        operationId: String,
    ): Result<ExamAckOutcome> = exchange {
        val reply = call(
            ACKNOWLEDGE,
            buildMap {
                put("sittingId", sittingId)
                put("questionIndex", questionIndex)
                put("operationId", operationId)
            },
        )
        parser.parseAckOutcome(wire(reply))
    }

    override suspend fun submitAnswer(
        sittingId: String,
        questionIndex: Int,
        revision: Long,
        answerPayload: String,
        operationId: String,
    ): Result<ExamSubmitOutcome> = exchange {
        val reply = call(
            SUBMIT,
            buildMap {
                put("sittingId", sittingId)
                put("questionIndex", questionIndex)
                put("revision", revision)
                // Сервер требует ОБЪЕКТ ответа (не массив, не строка): черновой JSON разбирается
                // и уезжает декодированным — строка была бы отказом INVALID_ARGUMENT.
                put("answer", decodedAnswerObject(answerPayload))
                put("operationId", operationId)
            },
        )
        parser.parseSubmitOutcome(wire(reply))
    }

    /** Черновик ответа (JSON) → простые значения для транспорта вызова. */
    private fun decodedAnswerObject(answerPayload: String): Map<String, Any?> {
        val decoded = try {
            Json.parseToJsonElement(answerPayload)
        } catch (_: IllegalArgumentException) {
            throw ExamFailure(ExamFailureKind.INVALID_REQUEST)
        } catch (_: Exception) {
            // Любой испорченный черновик — типовой отказ запроса, не краш транспорта.
            throw ExamFailure(ExamFailureKind.INVALID_REQUEST)
        }
        val plain = jsonElementToPlain(decoded)
        @Suppress("UNCHECKED_CAST")
        return plain as? Map<String, Any?>
            ?: throw ExamFailure(ExamFailureKind.INVALID_REQUEST)
    }

    private fun jsonElementToPlain(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive ->
            if (element.isString) {
                element.content
            } else {
                val raw = element.content
                when {
                    raw == "true" -> true
                    raw == "false" -> false
                    else -> raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: raw
                }
            }
        is JsonArray -> element.map { jsonElementToPlain(it) }
        is JsonObject -> element.entries.associate { (key, value) -> key to jsonElementToPlain(value) }
    }

    override suspend fun finishSitting(sittingId: String): Result<ExamFinishVerdict> = exchange {
        val reply = call(
            FINISH,
            buildMap {
                put("sittingId", sittingId)
            },
        )
        parser.parseFinishOutcome(wire(reply))
    }

    override suspend fun fetchSittingResult(sittingId: String): Result<ExamFetchOutcome> = exchange {
        val reply = call(
            FETCH_RESULT,
            buildMap {
                put("sittingId", sittingId)
            },
        )
        parser.parseFetchOutcome(wire(reply))
    }

    /** Один забор сессии на границе и перепроверки на каждом возврате; отмена — без маскировки. */
    private suspend fun <T> exchange(block: suspend () -> T): Result<T> {
        coroutineContext.ensureActive()
        val session = sessions.current() ?: return failed(ExamFailureKind.ACCOUNT_UNAVAILABLE)
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
                    failed(ExamFailureKind.ACCOUNT_CHANGED)
                } else {
                    failure(e)
                }
            }
        }
    }

    private fun requireSession(expected: OutboxSession) {
        if (sessions.current() != expected) {
            throw ExamFailure(ExamFailureKind.ACCOUNT_CHANGED)
        }
    }

    private fun wire(reply: Any?): String =
        try {
            mutationResponseJson(reply)
        } catch (_: IllegalArgumentException) {
            throw ExamFailure(ExamFailureKind.MALFORMED_RESPONSE)
        } catch (_: IllegalStateException) {
            throw ExamFailure(ExamFailureKind.MALFORMED_RESPONSE)
        }

    private fun <T> failure(error: Exception): Result<T> = when (error) {
        is ExamFailure -> Result.failure(error)
        is ExamSittingWireParser.MalformedExamWire -> failed(ExamFailureKind.MALFORMED_RESPONSE)
        is ExamCallableException -> failed(callableFailure(error))
        is IOException -> Result.failure(SyncFailure(SyncError.NoNetwork))
        else -> failed(ExamFailureKind.SERVER_FAILURE)
    }

    private fun callableFailure(error: ExamCallableException): ExamFailureKind =
        when (error.code) {
            "UNAUTHENTICATED", "PERMISSION_DENIED" -> ExamFailureKind.ACCOUNT_UNAVAILABLE
            "INVALID_ARGUMENT" -> ExamFailureKind.INVALID_REQUEST
            else -> ExamFailureKind.SERVER_FAILURE
        }

    private fun <T> failed(kind: ExamFailureKind): Result<T> =
        Result.failure(ExamFailure(kind))

    // (таксономия отказов — в общем слое источника, см. ExamTransportFailure)

    internal class ExamCallableException(
        val code: String,
        val details: Any?,
    ) : Exception(code)

    internal companion object {
        const val START_THEME = "startThemeTest"
        const val START_FINAL = "startFinalExam"
        const val ACKNOWLEDGE = "acknowledgeQuestion"
        const val SUBMIT = "submitAnswer"
        const val FINISH = "finishSitting"
        const val FETCH_RESULT = "fetchSittingResult"
    }
}

/** Совместимость имён: таксономия отказов границы переехала в общий слой источника. */
typealias ExamFailureKind = ExamTransportFailureKind
typealias ExamFailure = ExamTransportFailure
