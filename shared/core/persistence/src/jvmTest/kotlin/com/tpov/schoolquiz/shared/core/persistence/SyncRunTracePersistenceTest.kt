package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.sync.SyncRunKind
import com.tpov.schoolquiz.shared.core.sync.SyncRunTrace
import com.tpov.schoolquiz.shared.core.sync.traceOf
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * След прогона против настоящего SQLite на диске (история 4.2).
 *
 * Файл, а не память: проверяется ровно то, что критерий и требует, — процесс умер, база открыта
 * новым экземпляром, время последнего успеха прочитано то же. В памяти это не проверить: там
 * «перезапуск» неотличим от продолжения.
 */
class SyncRunTracePersistenceTest {

    private lateinit var dir: File
    private lateinit var db: AppDatabase
    private lateinit var store: RoomSyncRunTraceStore

    private val previous = "uid-A"
    private val next = "uid-B"

    /** Вид прогона, под которым идут проверки, не про разведение расписаний. */
    private val content = SyncRunKind.CONTENT

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("sync-run-trace").toFile()
        db = open()
        store = RoomSyncRunTraceStore(db.syncRunTraceDao())
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    /** Тот же файл — то же устройство. Новый экземпляр здесь и есть «приложение стартует заново». */
    private fun open(): AppDatabase =
        Room.databaseBuilder<AppDatabase>(name = File(dir, "schoolquiz.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .addTypeConverter(StringSetConverter())
            .addTypeConverter(TopParticipantListConverter())
            .build()

    @Test
    fun theSuccessTimeSurvivesTheDatabaseBeingOpenedByANewInstance() = runTest {
        store.recordSuccess(previous, content, atMs = 1_700L)

        db.close()
        db = open()
        val afterRestart =
            RoomSyncRunTraceStore(db.syncRunTraceDao()).observeTraces(previous).first().traceOf(content)

        assertEquals("след прогона не пережил перезапуск", 1_700L, afterRestart.lastSuccessAtMs)
    }

    @Test
    fun theLastErrorBranchSurvivesTooAndComesBackAsABranchNotAsText() = runTest {
        store.recordFailure(previous, content, SyncError.Refused("not enough gold"))

        db.close()
        db = open()
        val afterRestart =
            RoomSyncRunTraceStore(db.syncRunTraceDao()).observeTraces(previous).first().traceOf(content)

        assertEquals(SyncError.Refused("not enough gold"), afterRestart.lastError)
    }

    @Test
    fun theServerVersionComesBackAsANumberNotAsAParsedString() = runTest {
        // AD-15: числа отдаются целыми. Разобрать их из текста ошибки — то, ради отказа от чего
        // ветвь и заведена.
        store.recordFailure(previous, content, SyncError.VersionConflict(serverVersion = 7L))

        val trace = store.observeTraces(previous).first().traceOf(content)

        assertEquals(SyncError.VersionConflict(7L), trace.lastError)
    }

    @Test
    fun theTraceOfOneOwnerIsNotTheTraceOfAnother() = runTest {
        store.recordSuccess(previous, content, atMs = 1_000L)
        store.recordSuccess(next, content, atMs = 2_000L)

        assertEquals(1_000L, store.observeTraces(previous).first().traceOf(content).lastSuccessAtMs)
        assertEquals(2_000L, store.observeTraces(next).first().traceOf(content).lastSuccessAtMs)
    }

    @Test
    fun anOwnerWithoutATraceReadsAsZeroesNotAsNothing() = runTest {
        // Пустой поток оставил бы подписчика без единого значения, и он показал бы прошлое
        // (инвариант 8, NFR8).
        assertEquals(SyncRunTrace(), store.observeTraces("uid-never-synced").first().traceOf(content))
    }

    @Test
    fun aFailureKeepsTheTimeOfTheRunThatDidGoThrough() = runTest {
        store.recordSuccess(previous, content, atMs = 1_000L)

        store.recordFailure(previous, content, SyncError.NoNetwork)

        val trace = store.observeTraces(previous).first().traceOf(content)
        assertEquals("неудача не отменяет того, что прогон когда-то прошёл", 1_000L, trace.lastSuccessAtMs)
        assertEquals(SyncError.NoNetwork, trace.lastError)
    }

    @Test
    fun aLaterSuccessClearsTheComplaintThatWasAlreadyFixed() = runTest {
        store.recordFailure(previous, content, SyncError.NoNetwork)

        store.recordSuccess(previous, content, atMs = 2_000L)

        assertNull(store.observeTraces(previous).first().traceOf(content).lastError)
    }

    @Test
    fun theTraceOfThePreviousOwnerIsRemovedWithHisOtherRows() = runTest {
        // Вторая операция AD-8: перечень закрыт, и след в нём назван. Иначе строка забытого
        // владельца лежала бы на устройстве вечно.
        store.recordSuccess(previous, content, atMs = 1_000L)
        store.recordSuccess(next, content, atMs = 2_000L)
        val traces = RoomOwnerTraces(db.ownerRowsDao()) { uid -> listOf("private_quests:$uid") }

        traces.forget(previous)

        assertEquals(SyncRunTrace(), store.observeTraces(previous).first().traceOf(content))
        assertEquals(2_000L, store.observeTraces(next).first().traceOf(content).lastSuccessAtMs)
    }

    @Test
    fun theProfileRunDoesNotOverwriteWhatTheContentRunRecorded() = runTest {
        // Два расписания эпика 9 писали в одну строку: удачный профильный прогон снимал ветвь
        // упавшего контентного и ставил поверх неё своё время успеха. Ключ теперь пара, и строки
        // у них разные — на диске, а не только в памяти.
        store.recordFailure(previous, SyncRunKind.CONTENT, SyncError.NoNetwork)

        store.recordSuccess(previous, SyncRunKind.PROFILE, atMs = 2_000L)

        val traces = store.observeTraces(previous).first()
        assertEquals(SyncError.NoNetwork, traces.traceOf(SyncRunKind.CONTENT).lastError)
        assertEquals(0L, traces.traceOf(SyncRunKind.CONTENT).lastSuccessAtMs)
        assertEquals(2_000L, traces.traceOf(SyncRunKind.PROFILE).lastSuccessAtMs)
        assertNull(traces.traceOf(SyncRunKind.PROFILE).lastError)
    }

    @Test
    fun theResyncKeepsItsOwnRowAndNotTheScheduledOne() = runTest {
        // «Перечитать всё» перечитывает каталог и только его: объявить прошедшим плановый прогон
        // оно не имеет права ни временем, ни снятием ветви.
        store.recordFailure(previous, SyncRunKind.CONTENT, SyncError.NoNetwork)

        store.recordSuccess(previous, SyncRunKind.RESYNC, atMs = 3_000L)

        val traces = store.observeTraces(previous).first()
        assertEquals(SyncError.NoNetwork, traces.traceOf(SyncRunKind.CONTENT).lastError)
        assertEquals(3_000L, traces.traceOf(SyncRunKind.RESYNC).lastSuccessAtMs)
    }

    @Test
    fun everyKindOfTheForgottenOwnerGoesAndNotJustOne() = runTest {
        // Уборка идёт по владельцу, а не по строке: видов прогона несколько, и оставить один
        // значило бы оставить прежнего владельца на устройстве наполовину (AD-8).
        store.recordSuccess(previous, SyncRunKind.CONTENT, atMs = 1_000L)
        store.recordSuccess(previous, SyncRunKind.PROFILE, atMs = 1_100L)
        store.recordSuccess(previous, SyncRunKind.RESYNC, atMs = 1_200L)
        store.recordSuccess(next, SyncRunKind.PROFILE, atMs = 2_000L)
        val traces = RoomOwnerTraces(db.ownerRowsDao()) { uid -> listOf("private_quests:$uid") }

        traces.forget(previous)

        assertEquals(emptyMap<SyncRunKind, SyncRunTrace>(), store.observeTraces(previous).first())
        assertEquals(2_000L, store.observeTraces(next).first().traceOf(SyncRunKind.PROFILE).lastSuccessAtMs)
    }

    @Test
    fun anOwnerKnownOnlyByHisRunTraceIsStillSomeoneToForget() = runTest {
        // Пустая очередь и отсутствие черновиков не значат «терять нечего»: след прогона — тоже
        // личная строка, и владельца, известного только по ней, забыть некому, если про него не
        // спросить.
        store.recordSuccess(previous, content, atMs = 1_000L)
        val traces = RoomOwnerTraces(db.ownerRowsDao()) { uid -> listOf("private_quests:$uid") }

        assertTrue(previous in traces.ownersWithTraces())
    }
}
