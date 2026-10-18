@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.platform.firebase.exam

import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ExamVerifyStatus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Транспорт сертификатов против ТЕХ ЖЕ форм ответа, что собирает
 * `functions/index.js#publicVerifyCertificate` — дословно, все три ветви: подписанный валидный
 * снимок, явное ожидание подписи (snapshot: null), отсутствие выпуска (snapshot: null). Расхождение
 * формы ловится здесь, а не MALFORMED_RESPONSE'ом на устройстве игрока.
 */
class FirebaseExamCertificateRemoteDataSourceTest {

    @Test
    fun aValidSnapshotParsesVerbatim() =
        runTest {
            val f = Fake(this)
            f.reply =
                mapOf(
                    "found" to true,
                    "status" to "valid",
                    "issuanceId" to "issuance-1",
                    "snapshot" to
                        mapOf(
                            "claimsVersion" to 1,
                            "subject" to "player",
                            "courseId" to "course",
                            "sittingId" to "s1",
                            "resultVersion" to 1,
                            "percent" to 100,
                            "passMark" to 60,
                            "issuedAtMs" to 1_760_000_000_000L,
                            "expiresAtMs" to 1_791_536_000_000L,
                            "keyId" to "k1",
                            "bankSnapshotDigest" to "digest",
                            "signature" to "sig",
                            "algorithm" to "TEST-SIG",
                        ),
                )
            val view = f.remote.publicVerify("issuance-1").getOrThrow()
            assertEquals(ExamVerifyStatus.VALID, view.status)
            assertEquals("issuance-1", view.issuanceId)
            assertTrue("снимок приезжает целиком", view.snapshotJson!!.contains("\"signature\""))
            assertEquals(
                listOf(
                    FirebaseExamCertificateRemoteDataSource.PUBLIC_VERIFY to mapOf("issuanceId" to "issuance-1"),
                ),
                f.calls.toList(),
            )
        }

    @Test
    fun aPendingSigningReplyCarriesTheSameShapeWithANullSnapshot() =
        runTest {
            val f = Fake(this)
            // Дословная ветвь PENDING сервера: found, status, issuanceId, snapshot = null.
            f.reply =
                mapOf(
                    "found" to true,
                    "status" to "pending_signing",
                    "issuanceId" to "issuance-2",
                    "snapshot" to null,
                )
            val view = f.remote.publicVerify("issuance-2").getOrThrow()
            assertEquals(ExamVerifyStatus.PENDING, view.status)
            assertNull("ожидание подписи — не снимок и не NOT_FOUND", view.snapshotJson)
        }

    @Test
    fun anAbsentIssuanceReplyCarriesTheSameShapeWithANullSnapshot() =
        runTest {
            val f = Fake(this)
            // Дословная ветвь NOT_FOUND сервера.
            f.reply =
                mapOf(
                    "found" to false,
                    "status" to "not_found",
                    "issuanceId" to "issuance-3",
                    "snapshot" to null,
                )
            val view = f.remote.publicVerify("issuance-3").getOrThrow()
            assertEquals(ExamVerifyStatus.NOT_FOUND, view.status)
            assertNull(view.snapshotJson)
        }

    @Test
    fun callableNamesRemainBoundToTheActualServerExports() {
        assertEquals("fetchMyCertificates", FirebaseExamCertificateRemoteDataSource.FETCH_MINE)
        assertEquals("publicVerifyCertificate", FirebaseExamCertificateRemoteDataSource.PUBLIC_VERIFY)
    }

    private class Fake(scope: TestScope) {
        var reply: Any? = emptyMap<String, Any?>()
        val calls = mutableListOf<Pair<String, Map<String, Any?>>>()
        val remote: FirebaseExamCertificateRemoteDataSource =
            FirebaseExamCertificateRemoteDataSource(
                call = { name, payload ->
                    calls += name to payload
                    reply
                },
                dispatcher = StandardTestDispatcher(scope.testScheduler),
            )
    }
}
