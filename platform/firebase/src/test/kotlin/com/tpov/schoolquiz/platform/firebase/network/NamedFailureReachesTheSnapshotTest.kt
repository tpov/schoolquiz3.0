package com.tpov.schoolquiz.platform.firebase.network

import com.google.firebase.firestore.FirebaseFirestoreException
import com.tpov.schoolquiz.platform.firebase.network.fake.EmptyOutbox
import com.tpov.schoolquiz.platform.firebase.network.fake.MemorySyncRunTraceStore
import com.tpov.schoolquiz.platform.firebase.network.fake.OneCatalogRepository
import com.tpov.schoolquiz.platform.firebase.network.fake.OneSectionChangeJournal
import com.tpov.schoolquiz.platform.firebase.network.fake.SilentLessonRepository
import com.tpov.schoolquiz.platform.firebase.network.fake.SilentQuestRepository
import com.tpov.schoolquiz.platform.firebase.network.fake.SilentQuestionRepository
import com.tpov.schoolquiz.platform.firebase.network.fake.SilentThemeRepository
import com.tpov.schoolquiz.platform.firebase.network.fake.UnusedQuestAuthoringLocal
import com.tpov.schoolquiz.platform.firebase.network.fake.UnusedSectionLocal
import com.tpov.schoolquiz.platform.firebase.network.fake.UnusedUserStatsDao
import com.tpov.schoolquiz.shared.core.catalog.domain.model.CatalogId
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.stats.RawUserStats
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import com.tpov.schoolquiz.shared.core.sync.CatalogSyncListOrchestrator
import com.tpov.schoolquiz.shared.core.sync.DefaultSyncStatusRepository
import com.tpov.schoolquiz.shared.core.sync.InMemorySyncStateRepository
import com.tpov.schoolquiz.shared.core.sync.SyncChangePage
import com.tpov.schoolquiz.shared.core.sync.SyncCursor
import com.tpov.schoolquiz.shared.core.sync.SyncRun
import com.tpov.schoolquiz.shared.core.sync.SyncRunKind
import com.tpov.schoolquiz.shared.core.sync.SyncStatus
import com.tpov.schoolquiz.shared.core.sync.Syncable
import com.tpov.schoolquiz.shared.feature.app_shell.data.UserStatsRepositoryImpl
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSnapshot
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.PrivateQuestSyncChange
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.remote.QuestPrivateRemoteDataSource
import com.tpov.schoolquiz.shared.feature.quest_authoring.data.sync.QuestPrivateSync
import com.tpov.schoolquiz.shared.feature.section.data.SectionRemoteDataSource
import com.tpov.schoolquiz.shared.feature.section.data.SectionRepositoryImpl
import com.tpov.schoolquiz.shared.feature.section.data.dto.SectionDto
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ветвь обязана доехать до снимка состояния, а не остановиться на границе с Firebase.
 *
 * Файл проверяет то, чего не проверял `NamedReadFailuresTest`: не сам разбор, а весь путь целиком —
 * от исключения, которое бросил бы SDK, через настоящий шаг прогона и через [SyncRun] до
 * [SyncStatus], который читает экран. Прогонов четыре, и все четыре названы критерием 4.5: каталог,
 * приватные квесты, задания на ревью, собственные числа аккаунта.
 *
 * Начало каждой проверки — брошенное исключение, а не готовая ветвь. Подсунуть [SyncError.NoNetwork]
 * прямо в шаг значило бы проверить, что снимок умеет хранить то, что ему дали, — а вопрос ровно
 * обратный: возьмётся ли ветвь там, где сегодня наверх уезжает «неизвестно».
 */
class NamedFailureReachesTheSnapshotTest {

    private val owner = "u-1"

    /** «Связи нет» в том виде, в каком его называет Firestore: запрос до сервера не доехал. */
    private fun firestoreOffline(): FirebaseFirestoreException =
        FirebaseFirestoreException(
            "Failed to get documents from server",
            FirebaseFirestoreException.Code.UNAVAILABLE,
        )

    // ── Каталог ───────────────────────────────────────────────────────────────

    @Test
    fun `given firestore is offline when the catalog run refetches a node then the snapshot says no network`() =
        runTest {
            val catalogId = CatalogId("c-1")
            val sections =
                SectionRepositoryImpl(
                    local = UnusedSectionLocal(),
                    // Единственный источник неудачи в этом прогоне — узел, который журнал велел
                    // перечитать. Сам журнал и список каталогов читаются без приключений.
                    remote = OfflineSectionReads { firestoreOffline() }.namingFailures(),
                )
            val catalogRun =
                CatalogSyncListOrchestrator(
                    catalogRepo = OneCatalogRepository(catalogId),
                    questRepo = SilentQuestRepository(),
                    sectionRepo = sections,
                    themeRepo = SilentThemeRepository(),
                    lessonRepo = SilentLessonRepository(),
                    questionRepo = SilentQuestionRepository(),
                    syncStateRepo = InMemorySyncStateRepository(),
                    syncChangeRemote = OneSectionChangeJournal(catalogId, sectionId = "s-1"),
                )

            val snapshot = runThrough(SyncRunKind.CONTENT, catalogRun)

            assertEquals(SyncError.NoNetwork, snapshot.lastError)
        }

    // ── Приватные квесты ──────────────────────────────────────────────────────

    @Test
    fun `given firestore is offline when the private quest run reads its journal then the snapshot says no network`() =
        runTest {
            val privateQuests =
                QuestPrivateSync(
                    local = UnusedQuestAuthoringLocal(),
                    remote = OfflineQuestPrivateReads { firestoreOffline() }.namingFailures(),
                    syncStateRepo = InMemorySyncStateRepository(),
                    currentUidProvider = { owner },
                )

            val snapshot = runThrough(SyncRunKind.CONTENT, privateQuests)

            assertEquals(SyncError.NoNetwork, snapshot.lastError)
        }

    // ── Профиль ───────────────────────────────────────────────────────────────

    @Test
    fun `given firestore is offline when the profile run reads the account numbers then the snapshot says so`() =
        runTest {
            val accountNumbers =
                UserStatsRepositoryImpl(
                    remoteDataSource = OfflineUserStatsReads { firestoreOffline() }.namingFailures(),
                    userStatsDao = UnusedUserStatsDao(),
                    currentUidFlow = { flowOf(owner) },
                )

            val snapshot = runThrough(SyncRunKind.PROFILE, accountNumbers)

            // Профильный след — свой: контентный от этой неудачи не меняется (AD-14).
            assertEquals(SyncError.NoNetwork, snapshot.profileLastError)
            assertNull(snapshot.lastError)
        }

    // ── Отмена ────────────────────────────────────────────────────────────────

    @Test
    fun `given the run is cancelled then cancellation passes through and leaves no branch behind`() =
        runTest {
            val cancelled = CancellationException("scope closed")
            val privateQuests =
                QuestPrivateSync(
                    local = UnusedQuestAuthoringLocal(),
                    remote = OfflineQuestPrivateReads { cancelled }.namingFailures(),
                    syncStateRepo = InMemorySyncStateRepository(),
                    currentUidProvider = { owner },
                )
            val trace = MemorySyncRunTraceStore()
            val status = statusRepository(trace)

            var thrown: Throwable? = null
            try {
                SyncRun(status = status, clock = { NOW_MS }).run(SyncRunKind.CONTENT, listOf(privateQuests))
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Throwable,
            ) {
                thrown = e
            }

            // Отмена — не неудача обращения: она проходит насквозь и следа не оставляет. Если бы
            // она стала ветвью, прогон записал бы исход и объявил остановку неудачей сети.
            assertTrue(
                "ждали отмену, а прилетело ${thrown?.let { it::class.simpleName }}",
                thrown is CancellationException,
            )
            val snapshot = status.observeStatus().first()
            assertNull(snapshot.lastError)
            assertEquals(0L, snapshot.lastSuccessAtMs)
        }

    // ── Обстановка ────────────────────────────────────────────────────────────

    private fun statusRepository(trace: MemorySyncRunTraceStore) =
        DefaultSyncStatusRepository(
            store = EmptyOutbox(),
            trace = trace,
            currentUidFlow = flowOf(owner),
            currentUidProvider = { owner },
        )

    /**
     * Прогоняет шаг ровно так, как это делает приложение, и отдаёт снимок, который увидит экран.
     *
     * Настоящий [SyncRun] здесь обязателен: именно он превращает неудачу шага в ветвь следа, и
     * именно на этом переходе ветвь может потеряться.
     */
    private suspend fun runThrough(
        kind: SyncRunKind,
        step: Syncable,
    ): SyncStatus {
        val status = statusRepository(MemorySyncRunTraceStore())
        val outcome = SyncRun(status = status, clock = { NOW_MS }).run(kind, listOf(step))
        assertTrue("прогон обязан был закончиться неудачей", outcome.isFailure)
        return status.observeStatus().first()
    }

    private companion object {
        const val NOW_MS = 1_000L
    }
}

// ── Источники, которые не отвечают ───────────────────────────────────────────
//
// Настоящий Firestore подменить нечем: его классы финальны и без конструкторов. Фейк стоит на месте
// читающего источника и бросает ровно то, что бросил бы SDK; всё, что проверяется, лежит выше.

private class OfflineSectionReads(
    private val failure: () -> Throwable,
) : SectionRemoteDataSource {
    override suspend fun fetchByIds(ids: Set<String>): List<SectionDto> = throw failure()

    override suspend fun fetchChangedByParents(
        questIds: Set<String>,
        cursor: Long,
    ): List<SectionDto> = throw failure()
}

private class OfflineUserStatsReads(
    private val failure: () -> Throwable,
) : UserStatsDataSource {
    override fun observeRaw(): Flow<RawUserStats> = flowOf(RawUserStats())

    override suspend fun fetchRaw(): RawUserStats = throw failure()
}

private class OfflineQuestPrivateReads(
    private val failure: () -> Throwable,
) : QuestPrivateRemoteDataSource {
    override suspend fun fetchPage(
        cursor: SyncCursor,
        limit: Int,
    ): SyncChangePage<PrivateQuestSyncChange> = throw failure()

    override suspend fun fetchSnapshots(changes: List<PrivateQuestSyncChange>): List<PrivateQuestSnapshot> =
        throw failure()
}
