package com.tpov.schoolquiz.platform.firebase.exam

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.tpov.schoolquiz.platform.firebase.network.mutationResponseJson
import com.tpov.schoolquiz.platform.firebase.network.withAppTimeout
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamCertificateProfileView
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamCertificateRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamCertificateView
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamVerifyStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamVerifyView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Транспорт сертификатов: чтение профиля (вход обязателен) и публичная верификация (вход не
 * нужен — сервер не проверяет Auth на `publicVerifyCertificate`).
 */
class FirebaseExamCertificateRemoteDataSource(
    functions: FirebaseFunctions,
    private val dispatcher: CoroutineDispatcher,
) : ExamCertificateRemoteDataSource {

    private val call: suspend (String, Map<String, Any?>) -> Any? = { name, payload ->
        try {
            functions.getHttpsCallable(name).withAppTimeout().call(payload).await().data
        } catch (
            @Suppress("SwallowedException") e: FirebaseFunctionsException,
        ) {
            // Только структурные метаданные SDK: приватные message/cause наружу не выходят.
            throw ExamCallable(e.code.name)
        }
    }

    override suspend fun fetchMyCertificates(): Result<ExamCertificateProfileView> = runCatching {
        withContext(dispatcher) {
            val reply = asObject(call(FETCH_MINE, emptyMap()))
            reply.exact("certificate")
            val certificate = reply.obj("certificate")?.let { cert ->
                cert.exact(
                    "issuanceId", "courseId", "percent", "passMark", "issuedAtMs",
                    "expiresAtMs", "signingState",
                )
                ExamCertificateView(
                    issuanceId = cert.text("issuanceId") ?: malformed(),
                    courseId = cert.text("courseId") ?: malformed(),
                    percent = cert.int("percent") ?: malformed(),
                    passMark = cert.int("passMark") ?: malformed(),
                    issuedAtMs = cert.whole("issuedAtMs") ?: malformed(),
                    expiresAtMs = cert.whole("expiresAtMs") ?: malformed(),
                    signingState = cert.text("signingState") ?: malformed(),
                )
            }
            ExamCertificateProfileView(certificate = certificate)
        }
    }

    override suspend fun publicVerify(issuanceId: String): Result<ExamVerifyView> = runCatching {
        withContext(dispatcher) {
            val reply = asObject(call(PUBLIC_VERIFY, buildMap { put("issuanceId", issuanceId) }))
            reply.exact("found", "status", "issuanceId", "snapshot")
            ExamVerifyView(
                found = reply.boolean("found") ?: malformed(),
                status = when (reply.text("status")) {
                    "valid" -> ExamVerifyStatus.VALID
                    "expired" -> ExamVerifyStatus.EXPIRED
                    "pending_signing" -> ExamVerifyStatus.PENDING
                    "unknown_key" -> ExamVerifyStatus.UNKNOWN_KEY
                    "not_found" -> ExamVerifyStatus.NOT_FOUND
                    else -> malformed()
                },
                issuanceId = reply.text("issuanceId") ?: malformed(),
                snapshotJson = reply.obj("snapshot")?.toString(),
            )
        }
    }

    private fun asObject(reply: Any?): JsonObject =
        try {
            mutationResponseJson(reply) as? JsonObject ?: malformed()
        } catch (_: IllegalArgumentException) {
            malformed()
        } catch (_: IllegalStateException) {
            malformed()
        }

    private fun JsonObject.exact(vararg keys: String) {
        if (this.keys != keys.toSet()) malformed()
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull

    private fun JsonObject.whole(key: String): Long? =
        (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull

    private fun malformed(): Nothing = throw MalformedCertificateWire()

    class MalformedCertificateWire : Exception()

    class ExamCallable(val code: String) : Exception(code)

    internal companion object {
        const val FETCH_MINE = "fetchMyCertificates"
        const val PUBLIC_VERIFY = "publicVerifyCertificate"
    }
}

private val json = Json
