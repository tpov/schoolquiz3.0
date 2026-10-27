package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.feature.economy.data.remote.GiftClaimRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.domain.logic.GiftBoxStreak
import com.tpov.schoolquiz.shared.feature.economy.domain.logic.OfflineAccrualClaim
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimException
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimFailureReason
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimStatus
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftClaimRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftClaimRequest
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Дисциплина прямого вызываемого: намерение лежит до запроса, исход любого рода его разрешает,
 * нерешённое — оставляет лежать для повтора тем же телом.
 */
class GiftClaimRepositoryTest {
    private val intents = ClaimTestIntents()
    private val uid = MutableStateFlow<String?>("A")
    private var online = true
    private var ids = 0
    private var refreshes = 0
    private val sent = mutableListOf<String>()

    /** Ответ заглушки; `error` имитирует обрыв — решения сервера не было. */
    private var answer: (String) -> GiftClaimOutcome = ::claimed
    private var error: Exception? = null

    private fun repository() =
        GiftClaimRepositoryImpl(
            remote =
                object : GiftClaimRemoteDataSource {
                    override suspend fun submitGiftBoxOperation(payloadJson: String): GiftClaimOutcome {
                        sent += payloadJson
                        error?.let { throw it }
                        return answer(payloadJson)
                    }
                },
            profileRepository =
                object : ProfileRepository {
                    override fun observeCurrentProfile(): Flow<UserProfile> = MutableStateFlow(profile)

                    override suspend fun currentProfile() = profile

                    override suspend fun ensureCurrentProfile(): Result<UserProfile> {
                        refreshes++
                        return Result.success(profile)
                    }

                    override suspend fun updateNickname(nickname: String) = Result.success(profile)
                },
            intents = intents,
            network =
                object : NetworkMonitor {
                    override fun observeOnline(): Flow<Boolean> = MutableStateFlow(online)

                    override suspend fun isOnline() = online
                },
            currentUidFlow = { uid },
            newOperationId = { "operation-id-${++ids}".padEnd(16, '0') },
            clock = { 1L },
        )

    private val profile = UserProfile.offline().copy(uid = "A", boxCount = 3)

    private fun claimed(payload: String) =
        GiftClaimOutcome(
            GiftClaimKind.PREMIUM_DAILY,
            GiftClaimStatus.CLAIMED,
            replayed = false,
            boxCount = 4,
            boxStreakDays = 2,
            nextBoxAtMs = 3,
            nextBonusBoxAtMs = 25,
            earnedBoxes = 1,
            premiumUntilMs = 26,
        )

    @Test fun offlineRefusalCreatesNoIntentAndSendsNothing() =
        runTest {
            online = false
            val failure = repository().submit(GiftClaimRequest(GiftClaimKind.PREMIUM_DAILY)).exceptionOrNull()
            assertEquals(GiftClaimFailureReason.OFFLINE, (failure as GiftClaimException).reason)
            assertEquals(0, sent.size)
            assertNull(intents.active("A", GiftClaimKind.PREMIUM_DAILY))
        }

    @Test fun typedStatusesAreOutcomesThatResolveTheIntent() =
        runTest {
            for (status in listOf(GiftClaimStatus.NO_CHANGE, GiftClaimStatus.DAILY_LIMIT, GiftClaimStatus.PREMIUM_REQUIRED)) {
                answer = {
                    claimed(it).copy(kind = GiftClaimKind.PREMIUM_DAILY, status = status, earnedBoxes = 0)
                }
                val outcome = repository().submit(GiftClaimRequest(GiftClaimKind.PREMIUM_DAILY)).getOrThrow()
                assertEquals(status, outcome.status)
                assertNull(intents.active("A", GiftClaimKind.PREMIUM_DAILY), "$status обязан разрешить намерение")
            }
        }

    @Test fun staleBaseResolvesTheIntentAndReSyncsTheProfileInsteadOfRetrying() =
        runTest {
            answer = { claimed(it).copy(status = GiftClaimStatus.STALE_BASE, earnedBoxes = 0) }
            val outcome = repository().submit(GiftClaimRequest(GiftClaimKind.PREMIUM_DAILY)).getOrThrow()
            assertEquals(GiftClaimStatus.STALE_BASE, outcome.status)
            assertEquals(1, refreshes, "слово сервера выигрывает: профиль перечитан")
            assertNull(intents.active("A", GiftClaimKind.PREMIUM_DAILY))
            assertEquals(1, sent.size, "заявка не повторяется: base уехал устаревший")
        }

    @Test fun claimedOutcomeBringsTheBoxCountHomeInTheSamePass() =
        runTest {
            repository().submit(GiftClaimRequest(GiftClaimKind.PREMIUM_DAILY)).getOrThrow()
            assertEquals(1, refreshes)
            assertNull(intents.active("A", GiftClaimKind.PREMIUM_DAILY))
        }

    @Test fun undecidedCallKeepsTheIntentAndResumeResendsTheSameBodyVerbatim() =
        runTest {
            error = IllegalStateException("timeout")
            val repo = repository()
            val failure = repo.submit(GiftClaimRequest(GiftClaimKind.PREMIUM_DAILY)).exceptionOrNull()
            assertEquals(GiftClaimFailureReason.TRANSPORT, (failure as GiftClaimException).reason)
            assertEquals(0, refreshes)
            val stored = checkNotNull(intents.active("A", GiftClaimKind.PREMIUM_DAILY))

            error = null
            val resumed = repo.resumePending(GiftClaimKind.PREMIUM_DAILY)!!.getOrThrow()
            assertEquals(GiftClaimStatus.CLAIMED, resumed.status)
            assertEquals(listOf(stored.payloadJson, stored.payloadJson), sent, "повтор едет тем же телом")
            assertEquals(1, ids, "новый operationId при повторе не рождался")
            assertNull(intents.active("A", GiftClaimKind.PREMIUM_DAILY))
        }

    @Test fun secondTapWhileUndecidedReusesTheFirstIntentInsteadOfSendingAnother() =
        runTest {
            error = IllegalStateException("timeout")
            val repo = repository()
            repo.submit(GiftClaimRequest(GiftClaimKind.PREMIUM_DAILY))
            repo.submit(GiftClaimRequest(GiftClaimKind.PREMIUM_DAILY))
            assertEquals(1, ids, "вторая заявка не создала вторую операцию")
            assertEquals(2, sent.size)
            assertEquals(sent[0], sent[1])
        }

    @Test fun replayedEnvelopeRendersLikeAFreshAnswer() =
        runTest {
            answer = { claimed(it).copy(replayed = true) }
            val outcome = repository().submit(GiftClaimRequest(GiftClaimKind.PREMIUM_DAILY)).getOrThrow()
            assertEquals(true, outcome.replayed)
            assertEquals(GiftClaimStatus.CLAIMED, outcome.status)
            assertNull(intents.active("A", GiftClaimKind.PREMIUM_DAILY))
        }

    @Test fun conflictingReplayReleasesTheIntentTerminallyInsteadOfRetryingForever() =
        runTest {
            // Инверсия retro e7 F4: fence скрепил operationId с другим телом/владельцем — решения
            // для ЭТОГО тела не существует никогда, и вечный ретрай запирал входной проход.
            error = GiftClaimException(GiftClaimFailureReason.CONFLICTING_REPLAY, GiftClaimKind.PREMIUM_DAILY)
            val failure = repository().submit(GiftClaimRequest(GiftClaimKind.PREMIUM_DAILY)).exceptionOrNull()
            assertEquals(GiftClaimFailureReason.CONFLICTING_REPLAY, (failure as GiftClaimException).reason)
            assertEquals(0, refreshes)
            assertNull(intents.active("A", GiftClaimKind.PREMIUM_DAILY), "отравленное намерение отпущено терминально")
        }

    @Test fun corruptedBodyIsQuarantinedByANamedPreCallRefusal() =
        runTest {
            // REQUEST_INVALID — отказ ДО вызова: запрос не покидал устройство, решения сервера
            // быть не может — карантин тела, а не TRANSPORT-цикл (retro e7 F5b).
            error = GiftClaimException(GiftClaimFailureReason.REQUEST_INVALID, GiftClaimKind.PREMIUM_DAILY)
            val failure = repository().submit(GiftClaimRequest(GiftClaimKind.PREMIUM_DAILY)).exceptionOrNull()
            assertEquals(GiftClaimFailureReason.REQUEST_INVALID, (failure as GiftClaimException).reason)
            assertEquals(0, refreshes)
            assertNull(intents.active("A", GiftClaimKind.PREMIUM_DAILY), "испорченная строка не отравляет каждый вход")
        }

    @Test fun responseEnvelopeInvalidKeepsTheIntentForAVerbatimReplay() =
        runTest {
            // Денежная инварианта, вторая сторона направления: сервер ОТВЕТИЛ, но конверт не
            // разобрался — решение могло быть принято, намерение обязано жить (retro e7 F5b).
            error = GiftClaimException(GiftClaimFailureReason.ENVELOPE_INVALID, GiftClaimKind.PREMIUM_DAILY)
            val repo = repository()
            val failure = repo.submit(GiftClaimRequest(GiftClaimKind.PREMIUM_DAILY)).exceptionOrNull()
            assertEquals(GiftClaimFailureReason.ENVELOPE_INVALID, (failure as GiftClaimException).reason)
            assertEquals(0, refreshes)
            assertNotNull(intents.active("A", GiftClaimKind.PREMIUM_DAILY))

            // Повтор уедет тем же телом — когда конверт починят, решение найдётся тем же ключом.
            error = null
            val resumed = repo.resumePending(GiftClaimKind.PREMIUM_DAILY)!!.getOrThrow()
            assertEquals(GiftClaimStatus.CLAIMED, resumed.status)
            assertNull(intents.active("A", GiftClaimKind.PREMIUM_DAILY))
        }

    @Test fun limitAndExpiryRefreshTheProfileSoTheButtonReadsTheFreshServerWord() =
        runTest {
            // DAILY_LIMIT/PREMIUM_REQUIRED — терминальные исходы слота/срока: без перечитывания
            // кнопка жила Available и каждый тап минтовал новый operationId (retro e7 F5a).
            for (status in listOf(GiftClaimStatus.DAILY_LIMIT, GiftClaimStatus.PREMIUM_REQUIRED)) {
                answer = { claimed(it).copy(kind = GiftClaimKind.PREMIUM_DAILY, status = status, earnedBoxes = 0) }
                repository().submit(GiftClaimRequest(GiftClaimKind.PREMIUM_DAILY)).getOrThrow()
                assertNull(intents.active("A", GiftClaimKind.PREMIUM_DAILY), "$status разрешает намерение")
            }
            assertEquals(2, refreshes, "оба отказа слота перечитывают слово сервера")
        }

    @Test fun accountChangeBetweenSendAndAnswerRefusesWithoutRefreshOrResolve() =
        runTest {
            answer = {
                claimed(it)
                    .also { uid.value = "B" }
            }
            val failure = repository().submit(GiftClaimRequest(GiftClaimKind.PREMIUM_DAILY)).exceptionOrNull()
            assertEquals(GiftClaimFailureReason.NO_ACCOUNT, (failure as GiftClaimException).reason)
            assertEquals(0, refreshes, "подарок прежнего владельца не перечитывает профиль нового")
            assertNotNull(intents.active("A", GiftClaimKind.PREMIUM_DAILY))
        }

    @Test fun offlineStreakBodyCarriesBaseVerbatimAndClampedClaim() =
        runTest {
            val request =
                GiftClaimRequest(
                    GiftClaimKind.OFFLINE_STREAK,
                    base = GiftBoxStreak(2, 10, 1_000L),
                    claim = OfflineAccrualClaim(12, 2),
                )
            answer = { claimed(it).copy(kind = GiftClaimKind.OFFLINE_STREAK, status = GiftClaimStatus.CLAIMED) }
            repository().submit(request).getOrThrow()

            val body = Json.parseToJsonElement(sent.single()).jsonObject
            assertEquals("OFFLINE_STREAK", body.getValue("kind").jsonPrimitive.content)
            assertEquals("A", body.getValue("ownerUid").jsonPrimitive.content)
            assertEquals("10", body.getValue("base").jsonObject.getValue("boxStreakDays").jsonPrimitive.content)
            assertEquals("1000", body.getValue("base").jsonObject.getValue("nextBoxAtMs").jsonPrimitive.content)
            assertEquals("12", body.getValue("claimed").jsonObject.getValue("boxStreakDays").jsonPrimitive.content)
            assertEquals("2", body.getValue("claimed").jsonObject.getValue("boxesEarned").jsonPrimitive.content)
        }

    @Test fun visitBodyCarriesOnlyTheIdentityTriple() =
        runTest {
            answer = { claimed(it).copy(kind = GiftClaimKind.VISIT) }
            repository().submit(GiftClaimRequest(GiftClaimKind.VISIT)).getOrThrow()

            val body = Json.parseToJsonElement(sent.single()).jsonObject
            assertEquals(setOf("kind", "ownerUid", "operationId"), body.keys)
            assertEquals("VISIT", body.getValue("kind").jsonPrimitive.content)
        }

    @Test fun observePendingFollowsTheLiveIntentOfTheCurrentOwner() =
        runTest {
            val repo = repository()
            assertEquals(false, repo.observePending(GiftClaimKind.PREMIUM_DAILY).first())

            error = IllegalStateException("timeout")
            repo.submit(GiftClaimRequest(GiftClaimKind.PREMIUM_DAILY))
            assertEquals(true, repo.observePending(GiftClaimKind.PREMIUM_DAILY).first())
            assertEquals(false, repo.observePending(GiftClaimKind.VISIT).first())

            error = null
            repo.resumePending(GiftClaimKind.PREMIUM_DAILY)!!.getOrThrow()
            assertEquals(false, repo.observePending(GiftClaimKind.PREMIUM_DAILY).first())
        }

    private fun assertNotNull(value: Any?) = assertTrue(value != null, "ожидано живое намерение")
}

private class ClaimTestIntents : GiftClaimIntentStore {
    private val rows = MutableStateFlow<Map<Pair<String, GiftClaimKind>, GiftClaimIntent>>(emptyMap())

    override suspend fun active(ownerUid: String, kind: GiftClaimKind) = rows.value[ownerUid to kind]

    override fun observeActive(ownerUid: String, kind: GiftClaimKind): Flow<GiftClaimIntent?> =
        rows.map { it[ownerUid to kind] }

    override suspend fun reserve(
        ownerUid: String,
        kind: GiftClaimKind,
        operationId: String,
        payloadJson: String,
        nowMs: Long,
    ): GiftClaimIntent {
        active(ownerUid, kind)?.let { return it }
        val intent = GiftClaimIntent(ownerUid, kind, operationId, payloadJson, nowMs)
        rows.value += (ownerUid to kind) to intent
        return intent
    }

    override suspend fun resolve(intent: GiftClaimIntent): Boolean {
        val current = active(intent.ownerUid, intent.kind) ?: return false
        if (current.operationId != intent.operationId) return false
        rows.value -= intent.ownerUid to intent.kind
        return true
    }
}
