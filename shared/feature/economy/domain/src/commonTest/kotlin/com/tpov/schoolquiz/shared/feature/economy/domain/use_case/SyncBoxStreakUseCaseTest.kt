package com.tpov.schoolquiz.shared.feature.economy.domain.use_case

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.feature.economy.domain.logic.GiftBoxStreak
import com.tpov.schoolquiz.shared.feature.economy.domain.logic.OfflineAccrualClaim
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimKind
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftClaimStatus
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftBoxLocalStreakStore
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftClaimRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftClaimRequest
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.LocalBoxStreak
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Порядок входного прохода: офлайн-заявка раньше визита, визит раньше перечитывания.
 *
 * Именно этот порядок не даёт 48-часовому сбросу сервера съесть честно насчитанные офлайн-дни:
 * якорь продлевается заявкой до того, как визит назовёт прошедшее время «пропущенными сутками».
 */
class SyncBoxStreakUseCaseTest {
    private val DAY = GiftBoxStreak.DAY_MS
    private val uid = MutableStateFlow<String?>("A")
    private var online = true
    private val now = MutableStateFlow(10_000L * DAY)

    private val claims = FakeGiftClaimRepository()
    private val localStreak = FakeLocalStreakStore()
    private val profile = MutableStateFlow(UserProfile.offline().copy(uid = "A", boxCount = 0, boxStreakDays = 0))

    private fun useCase() =
        SyncBoxStreakUseCase(
            claims = claims,
            profileRepository = profileRepo(),
            localStreak = localStreak,
            network =
                object : NetworkMonitor {
                    override fun observeOnline(): Flow<Boolean> = MutableStateFlow(online)

                    override suspend fun isOnline() = online
                },
            currentUidFlow = { uid },
            clock = { now.value },
        )

    private fun profileRepo() =
        object : ProfileRepository {
            override fun observeCurrentProfile(): Flow<UserProfile> = profile

            override suspend fun currentProfile() = profile.value

            override suspend fun ensureCurrentProfile(): Result<UserProfile> {
                claims.refreshes++
                // Перечитывание сдвигает слово сервера — так же, как это сделал бы сервер.
                profile.value = profile.value.copy(boxStreakDays = 1, nextBoxAtMs = now.value + DAY)
                return Result.success(profile.value)
            }

            override suspend fun updateNickname(nickname: String) = Result.success(profile.value)
        }

    @Test fun firstEntryOfAFreshAccountClaimsOneDayWithoutBoxesAndVisitsIdempotently() =
        runTest {
            val report = useCase().invoke().getOrThrow()

            assertEquals(
                listOf(GiftClaimKind.OFFLINE_STREAK, GiftClaimKind.VISIT),
                claims.submits.map { it.kind },
                "первое слово устанавливает сегодня: заявка раньше, визит в тех же сутках",
            )
            val claim = claims.submits.first()
            assertEquals(OfflineAccrualClaim(1, 0), claim.claim)
            assertEquals(GiftBoxStreak(0, 0, 0), claim.base, "base — слово сервера дословно")
            assertEquals(1, claims.refreshes, "проход кончается перечитанным словом сервера")
            assertEquals(GiftBoxStreak(0, 1, now.value + DAY), localStreak.saved.getValue("A").base)
        }

    @Test fun offlineEntryOnlyCountsTheVisitLocally() =
        runTest {
            online = false
            val report = useCase().invoke().getOrThrow()

            assertTrue(report.offline)
            assertEquals(0, claims.submits.size)
            val saved = localStreak.saved.getValue("A")
            assertEquals(GiftBoxStreak(0, 1, now.value + DAY), saved.counted)
            assertEquals(GiftBoxStreak(0, 0, 0), saved.base, "местная база — последнее слово сервера")
        }

    @Test fun reconnectClaimsOfflineDaysBeforeAnyVisitAndBeforeTheRefresh() =
        runTest {
            // Слово сервера: серия 9, якорь истёк час назад. Пока связь была, устройство
            // насчитало один визит «сегодня» (плюс день серии).
            profile.value = UserProfile.offline().copy(uid = "A", boxCount = 0, boxStreakDays = 9, nextBoxAtMs = now.value - 1)
            localStreak.saved["A"] =
                LocalBoxStreak(
                    GiftBoxStreak(0, 9, now.value - 1),
                    GiftBoxStreak(1, 10, now.value + DAY - 1),
                    now.value - DAY / 2,
                )

            useCase().invoke().getOrThrow()

            val order = claims.submits.map { it.kind }
            assertEquals(GiftClaimKind.OFFLINE_STREAK, order.first(), "офлайн-заявка едет первой")
            assertEquals(GiftClaimKind.VISIT, order.last())
            val claim = claims.submits.first()
            assertEquals(OfflineAccrualClaim(10, 1), claim.claim, "десятый день — первая коробка")
            assertEquals(GiftBoxStreak(0, 9, now.value - 1), claim.base, "base не переставляется ни на бит")
        }

    @Test fun aLateReconnectStillClaimsTheHonestlyCountedOfflineDays() =
        runTest {
            // Возвращение позже чем через 48ч после последнего офлайн-визита: модель счёта
            // обрывает серию за пропуск, но заявка считается от счёта ДО сегодняшнего визита —
            // честно насчитанные дни серверу по-прежнему не слышны, и они уезжают заявкой.
            val lastOfflineVisit = now.value - 5 * DAY
            profile.value = UserProfile.offline().copy(uid = "A", boxCount = 0, boxStreakDays = 9, nextBoxAtMs = now.value - 9 * DAY)
            localStreak.saved["A"] =
                LocalBoxStreak(
                    GiftBoxStreak(0, 9, now.value - 9 * DAY),
                    GiftBoxStreak(1, 10, lastOfflineVisit + DAY),
                    lastOfflineVisit,
                )

            useCase().invoke().getOrThrow()

            val claim = claims.submits.first()
            assertEquals(GiftClaimKind.OFFLINE_STREAK, claim.kind, "поздний реконнект не отменяет офлайн-заявку")
            assertEquals(OfflineAccrualClaim(10, 1), claim.claim, "десятый день с коробкой — от счёта до визита")
            assertEquals(GiftBoxStreak(0, 9, now.value - 9 * DAY), claim.base)
        }

    @Test fun anUndecidedOfflineClaimBlocksTheVisitSoTheResetCannotEatTheDays() =
        runTest {
            profile.value = UserProfile.offline().copy(uid = "A", boxCount = 0, boxStreakDays = 9, nextBoxAtMs = now.value - 1)
            localStreak.saved["A"] =
                LocalBoxStreak(
                    GiftBoxStreak(0, 9, now.value - 1),
                    GiftBoxStreak(1, 10, now.value + DAY - 1),
                    now.value - 1,
                )
            claims.failNext = true

            val report = useCase().invoke().getOrThrow()

            assertEquals(listOf(GiftClaimKind.OFFLINE_STREAK), claims.submits.map { it.kind })
            assertNull(report.visit, "без решения по заявке визит не посылается")
        }

    @Test fun secondEntryInTheSameWindowSendsOnlyTheIdempotentVisit() =
        runTest {
            // После первого прохода счёт пересеян свежим словом; повторный вход в те же сутки
            // заявлять нечего — серия не выросла, коробок нет.
            val word = GiftBoxStreak(1, 10, now.value + DAY / 2)
            profile.value = UserProfile.offline().copy(uid = "A", boxCount = word.boxCount, boxStreakDays = word.streakDays, nextBoxAtMs = word.nextBoxAtMs)
            localStreak.saved["A"] = LocalBoxStreak(word, word, now.value)

            useCase().invoke().getOrThrow()

            assertEquals(listOf(GiftClaimKind.VISIT), claims.submits.map { it.kind })
        }

    @Test fun staleBaseDropsTheLocalClaimAndReseedsFromTheFreshWord() =
        runTest {
            profile.value = UserProfile.offline().copy(uid = "A", boxCount = 2, boxStreakDays = 9, nextBoxAtMs = now.value - 1)
            localStreak.saved["A"] =
                LocalBoxStreak(
                    GiftBoxStreak(2, 9, now.value - 1),
                    GiftBoxStreak(2, 10, now.value + DAY - 1),
                    now.value - 1,
                )
            claims.nextStatus = GiftClaimStatus.STALE_BASE

            useCase().invoke().getOrThrow()

            val saved = localStreak.saved.getValue("A")
            assertEquals(GiftBoxStreak(2, 1, now.value + DAY), saved.base, "свежее слово — новая база счёта")
            assertEquals(GiftBoxStreak(2, 1, now.value + DAY), saved.counted)
            assertEquals(
                listOf(GiftClaimKind.OFFLINE_STREAK, GiftClaimKind.VISIT),
                claims.submits.map { it.kind },
                "брошенная заявка не повторяется, обычный визит продолжается",
            )
        }

    @Test fun processDeathResumesThePendingIntentBeforeCountingToday() =
        runTest {
            claims.pending[GiftClaimKind.PREMIUM_DAILY] =
                GiftClaimOutcome(GiftClaimKind.PREMIUM_DAILY, GiftClaimStatus.CLAIMED, true, 1, 2, 3, 4, 1, 5)
            claims.pending[GiftClaimKind.VISIT] =
                GiftClaimOutcome(GiftClaimKind.VISIT, GiftClaimStatus.NO_CHANGE, true, 1, 2, 3, 4, 0, 5)

            val report = useCase().invoke().getOrThrow()

            assertEquals(GiftClaimKind.OFFLINE_STREAK, claims.resumeOrder.first(), "незавершённые прежде всего")
            assertTrue(GiftClaimKind.VISIT in claims.resumeOrder)
            assertEquals(
                listOf(GiftClaimKind.OFFLINE_STREAK, GiftClaimKind.VISIT),
                claims.submits.map { it.kind },
            )
            assertEquals(
                GiftClaimStatus.CLAIMED,
                report.offlineClaim?.status,
                "сюжет офлайн-заявки в отчёте — что вернул повтор",
            )
        }

    @Test fun clockSetBackwardsCountsNoExtraDayAndClockForwardCannotOverclaim() =
        runTest {
            // Слово сервера свежее: якорь в будущем. Визит с отстающими часами — повтор в тех же
            // сутках, местный счёт не растёт, заявлять нечего.
            val word = GiftBoxStreak(1, 10, now.value + DAY)
            profile.value = UserProfile.offline().copy(uid = "A", boxCount = 1, boxStreakDays = 10, nextBoxAtMs = now.value + DAY)
            localStreak.saved["A"] = LocalBoxStreak(word, word, now.value)
            now.value -= 3 * DAY

            useCase().invoke().getOrThrow()

            assertEquals(listOf(GiftClaimKind.VISIT), claims.submits.map { it.kind }, "заявки вниз не отправляются")
            // Отставшие часы визит не насчитали, а пересев из свежего слова сервера остался тем же
            // словом: ни коробки, ни дня заявка сверху не добавила.
            val reseeded = localStreak.saved.getValue("A")
            assertEquals(reseeded.base, reseeded.counted)
        }
}

private class FakeGiftClaimRepository : GiftClaimRepository {
    val submits = mutableListOf<GiftClaimRequest>()
    val pending = mutableMapOf<GiftClaimKind, GiftClaimOutcome>()
    val resumeOrder = mutableListOf<GiftClaimKind>()
    var refreshes = 0
    var failNext = false
    var nextStatus: GiftClaimStatus? = null

    override suspend fun submit(request: GiftClaimRequest): Result<GiftClaimOutcome> {
        submits += request
        if (failNext) return Result.failure(IllegalStateException("no decision"))
        val status = nextStatus ?: if (request.kind == GiftClaimKind.OFFLINE_STREAK) GiftClaimStatus.CLAIMED else GiftClaimStatus.NO_CHANGE
        return Result.success(
            GiftClaimOutcome(request.kind, status, false, 1, 2, 3, 4, if (status == GiftClaimStatus.CLAIMED) 1 else 0, 5),
        )
    }

    override suspend fun resumePending(kind: GiftClaimKind): Result<GiftClaimOutcome>? {
        resumeOrder += kind
        return pending[kind]?.let { Result.success(it) }
    }

    override fun observePending(kind: GiftClaimKind): Flow<Boolean> = MutableStateFlow(pending.containsKey(kind))
}

private class FakeLocalStreakStore : GiftBoxLocalStreakStore {
    val saved = mutableMapOf<String, LocalBoxStreak>()

    override suspend fun find(ownerUid: String) = saved[ownerUid]

    override suspend fun save(ownerUid: String, value: LocalBoxStreak) {
        saved[ownerUid] = value
    }

    override suspend fun forget(ownerUid: String) {
        saved.remove(ownerUid)
    }
}
