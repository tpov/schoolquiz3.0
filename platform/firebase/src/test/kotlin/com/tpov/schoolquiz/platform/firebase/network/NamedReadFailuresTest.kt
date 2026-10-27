package com.tpov.schoolquiz.platform.firebase.network

import com.google.firebase.firestore.FirebaseFirestoreException
import com.tpov.schoolquiz.shared.core.catalog.data.CatalogDto
import com.tpov.schoolquiz.shared.core.catalog.data.CatalogRemoteDataSource
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncChange
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.core.sync.LessonContentSyncChange
import com.tpov.schoolquiz.shared.core.sync.LessonContentSyncChangeRemoteDataSource
import com.tpov.schoolquiz.shared.core.sync.SyncChangePage
import com.tpov.schoolquiz.shared.core.sync.SyncCursor
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.ProfileBootstrapRequest
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.ProfileRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.LeagueStanding
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.UserProfile
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSnapshot
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSyncChange
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestPrivateRemoteDataSource
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Чтение обязано называть свою неудачу — тем же способом, каким её называет отправка.
 *
 * Все четыре источника из критерия 4.2 отдавали наверх голое исключение SDK, поэтому экран
 * показывал «неизвестно» даже там, где сеть просто отсутствовала. Тесты идут от читающего шага:
 * источник бросает то, что бросил бы Firestore или транспорт вызова, — а проверяется ветвь,
 * доехавшая до вызывающего. Готовую ветвь никто никому не подсовывает: если разбор пропадёт,
 * ни один тест в этом файле не пройдёт.
 */
class NamedReadFailuresTest {

    /** «Нет связи» в том виде, в каком его называет сам Firestore: запрос до сервера не доехал. */
    private fun firestoreOffline(): FirebaseFirestoreException =
        FirebaseFirestoreException(
            "Failed to get documents from server",
            FirebaseFirestoreException.Code.UNAVAILABLE,
        )

    private fun firestoreRefusal(): FirebaseFirestoreException =
        FirebaseFirestoreException(
            "Missing or insufficient permissions",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
        )

    // ── Каталог ───────────────────────────────────────────────────────────────

    @Test
    fun `given firestore says offline when the catalog page is read then the branch is no network`() =
        runTest {
            val offline = firestoreOffline()
            val catalog = FailingCatalogReads { offline }.namingFailures()

            val failure =
                caught<SyncFailure> {
                    catalog.fetchChangedSince(SyncCursor.BEGINNING, limit = 200)
                }

            assertEquals(SyncError.NoNetwork, failure.error)
            // Исходное исключение остаётся причиной: журналу оно нужно, экрану — нет (AD-15).
            assertSame(offline, failure.cause)
        }

    @Test
    fun `given firestore refuses when catalogs are read by id then the branch is a refusal that names the reason`() =
        runTest {
            val catalog = FailingCatalogReads { firestoreRefusal() }.namingFailures()

            val failure = caught<SyncFailure> { catalog.fetchByIds(setOf("c-1")) }

            assertEquals(SyncError.Refused("Missing or insufficient permissions"), failure.error)
        }

    @Test
    fun `given firestore says offline when the catalog journal is read then the branch is no network`() =
        runTest {
            val journal = FailingCatalogJournalReads { firestoreOffline() }.namingFailures()

            val failure =
                caught<SyncFailure> {
                    journal.fetchPage(CatalogId("c-1"), SyncCursor.BEGINNING, limit = 200)
                }

            assertEquals(SyncError.NoNetwork, failure.error)
        }

    @Test
    fun `given firestore says offline when the lesson content journal is read then the branch is no network`() =
        runTest {
            val journal = FailingLessonContentJournalReads { firestoreOffline() }.namingFailures()

            val failure =
                caught<SyncFailure> {
                    journal.fetchPage(LessonId("l-1"), SyncCursor.BEGINNING, limit = 200)
                }

            assertEquals(SyncError.NoNetwork, failure.error)
        }

    // ── Приватные квесты ──────────────────────────────────────────────────────

    @Test
    fun `given firestore says offline when the private quest journal is read then the branch is no network`() =
        runTest {
            val quests = FailingQuestPrivateReads { firestoreOffline() }.namingFailures()

            val failure = caught<SyncFailure> { quests.fetchPage(SyncCursor.BEGINNING, limit = 200) }

            assertEquals(SyncError.NoNetwork, failure.error)
        }

    @Test
    fun `given firestore aborts when a private quest snapshot is read then the branch is a version conflict`() =
        runTest {
            val aborted =
                FirebaseFirestoreException("Too much contention", FirebaseFirestoreException.Code.ABORTED)
            val quests = FailingQuestPrivateReads { aborted }.namingFailures()

            val failure = caught<SyncFailure> { quests.fetchSnapshots(emptyList()) }

            // Firestore своей версии не присылает — ветвь есть, номера нет (AD-24).
            assertEquals(SyncError.VersionConflict(serverVersion = null), failure.error)
        }

    // ── Профиль ───────────────────────────────────────────────────────────────

    @Test
    fun `given the callable transport finds no route when the profile is read then the branch is no network`() =
        runTest {
            val profile = FailingProfileReads { UnknownHostException("firebase") }.namingFailures()

            val failure =
                caught<SyncFailure> {
                    profile.ensureProfile(
                        ProfileBootstrapRequest(uid = "A", nickname = "n", knownLanguages = listOf("ru")),
                    )
                }

            assertEquals(SyncError.NoNetwork, failure.error)
        }

    @Test
    fun `given the league standing is read when the connection breaks then the branch is no network`() =
        runTest {
            val profile = FailingProfileReads { UnknownHostException("firebase") }.namingFailures()

            val failure = caught<SyncFailure> { profile.leagueStanding() }

            assertEquals(SyncError.NoNetwork, failure.error)
        }

    // ── Отмена ────────────────────────────────────────────────────────────────

    @Test
    fun `given the read is cancelled then cancellation passes through and does not become a branch`() =
        runTest {
            val cancelled = CancellationException("scope closed")
            val catalog = FailingCatalogReads { cancelled }.namingFailures()

            // Не SyncFailure: отмена — не неудача обращения, и назвать её ветвью значит проглотить
            // остановку, а вместе с ней и структурную конкурентность прогона.
            val thrown =
                caught<CancellationException> {
                    catalog.fetchChangedSince(SyncCursor.BEGINNING, limit = 200)
                }

            assertSame(cancelled, thrown)
        }

    @Test
    fun `given a failure that is already named then it is not wrapped a second time`() =
        runTest {
            val named = SyncFailure(SyncError.PreconditionNotMet)
            val catalog = FailingCatalogReads { named }.namingFailures()

            val failure = caught<SyncFailure> { catalog.fetchByIds(setOf("c-1")) }

            assertSame(named, failure)
        }
}

/**
 * То же, что `assertFailsWith`, но без `kotlin-test`: модуль собирается на JUnit 4.
 *
 * Функция встраиваемая и не `suspend` — тело всё равно исполняется в вызывающей корутине, поэтому
 * внутри можно звать читающие шаги.
 */
private inline fun <reified T : Throwable> caught(block: () -> Unit): T {
    try {
        block()
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Throwable,
    ) {
        if (e is T) return e
        throw AssertionError("ждали ${T::class.simpleName}, а прилетело ${e::class.simpleName}", e)
    }
    throw AssertionError("ждали ${T::class.simpleName}, а не прилетело ничего")
}

// ── Фейки читающих источников ─────────────────────────────────────────────────
//
// Настоящий Firestore подменить нечем: его классы финальны и без конструкторов. Поэтому фейк
// стоит на месте самого читающего источника и бросает ровно то, что бросил бы SDK, — а всё, что
// проверяется, лежит выше этой точки.

private class FailingCatalogReads(
    private val failure: () -> Throwable,
) : CatalogRemoteDataSource {
    override suspend fun fetchChangedSince(
        cursor: SyncCursor,
        limit: Int,
    ): List<CatalogDto> = throw failure()

    override suspend fun fetchByIds(ids: Set<String>): List<CatalogDto> = throw failure()
}

private class FailingCatalogJournalReads(
    private val failure: () -> Throwable,
) : CatalogSyncChangeRemoteDataSource {
    override suspend fun fetchPage(
        catalogId: CatalogId,
        cursor: SyncCursor,
        limit: Int,
    ): SyncChangePage<CatalogSyncChange> = throw failure()
}

private class FailingLessonContentJournalReads(
    private val failure: () -> Throwable,
) : LessonContentSyncChangeRemoteDataSource {
    override suspend fun fetchPage(
        lessonId: LessonId,
        cursor: SyncCursor,
        limit: Int,
    ): SyncChangePage<LessonContentSyncChange> = throw failure()
}

private class FailingQuestPrivateReads(
    private val failure: () -> Throwable,
) : QuestPrivateRemoteDataSource {
    override suspend fun fetchPage(
        cursor: SyncCursor,
        limit: Int,
    ): SyncChangePage<PrivateQuestSyncChange> = throw failure()

    override suspend fun fetchSnapshots(changes: List<PrivateQuestSyncChange>): List<PrivateQuestSnapshot> =
        throw failure()
}

private class FailingProfileReads(
    private val failure: () -> Throwable,
) : ProfileRemoteDataSource {
    override suspend fun ensureProfile(request: ProfileBootstrapRequest): UserProfile = throw failure()

    override suspend fun updateNickname(
        nickname: String,
        knownLanguages: List<String>,
    ): UserProfile = throw failure()

    override suspend fun leagueStanding(): LeagueStanding = throw failure()
}
