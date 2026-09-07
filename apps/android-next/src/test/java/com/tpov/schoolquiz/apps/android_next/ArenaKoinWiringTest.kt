@file:Suppress("MagicNumber")

package com.tpov.schoolquiz.apps.android_next

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.outbox.MutationReceiptStore
import com.tpov.schoolquiz.shared.core.outbox.MutationTransport
import com.tpov.schoolquiz.shared.core.outbox.OutboxEngine
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.outbox.OutboxSessionProvider
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.core.outbox.QuarantineListener
import com.tpov.schoolquiz.shared.core.persistence.AppDatabase
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketDao
import com.tpov.schoolquiz.shared.core.persistence.di.persistenceModule
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.question_schema.OptionId
import com.tpov.schoolquiz.shared.core.question_schema.QuestionContent
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaCancelledSettlementProof
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaCancelledSettlementResolver
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaEntryLocalMapper
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaJournalCheckpointCodec
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaJournalCodec
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaJournalStorage
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaOwnerGate
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaRunnerCheckpointCodec
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaTicketJournal
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.OutboxArenaOwnerGate
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.RoomArenaJournalStorage
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.arenaCheckpointCodecModule
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.arenaDataModule
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.arenaEntryProofModule
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.di.lessonRunnerDataModule
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketStatusRemoteDataSource
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository.ArenaEntryRepositoryImpl
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.computeTimer
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaInitialCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntryState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRemoteFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketHeader
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketListRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketQuestion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketSnapshot
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketStatus
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.TimerCoefficients
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaEntryRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Test
import org.koin.core.KoinApplication
import org.koin.core.error.NoDefinitionFoundException
import org.koin.dsl.module
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Real production modules, isolated Koin context, no Firebase startup or Room database opening. */
@OptIn(ExperimentalCoroutinesApi::class)
class ArenaKoinWiringTest {
    @Test
    fun productionParentModuleResolvesStrictJournalAndDaoWithoutFinancialActions() {
        val f = Fixture()
        val app = f.graph()
        try {
            val koin = app.koin
            assertSame(f.dao, koin.get<ArenaTicketDao>())
            assertSame(f.dao, koin.get<ArenaTicketDao>())
            assertIs<RoomArenaJournalStorage>(koin.get<ArenaJournalStorage>())
            assertIs<OutboxArenaOwnerGate>(koin.get<ArenaOwnerGate>())
            assertSame(koin.get<ArenaRunnerCheckpointCodec>(), koin.get<ArenaJournalCheckpointCodec>())
            assertIs<ArenaRunnerCheckpointCodec>(koin.get<ArenaJournalCheckpointCodec>())
            assertSame(koin.get<ArenaTicketJournal>(), koin.get<ArenaTicketJournal>())
            assertIs<ArenaEntryRepositoryImpl>(koin.get<ArenaEntryRepository>())
            assertSame(koin.get<ArenaEntryRepository>(), koin.get<ArenaEntryRepository>())
            assertSame(koin.get<ArenaEntryLocalMapper>(), koin.get<ArenaEntryLocalMapper>())
            assertSame(koin.get<ArenaTicketWireParser>(), koin.get<ArenaTicketWireParser>())
            assertSame(koin.get<ArenaCancelledSettlementProof>(), koin.get<ArenaCancelledSettlementProof>())
            assertSame(koin.get<ArenaCancelledSettlementResolver>(), koin.get<ArenaCancelledSettlementResolver>())
            assertSame(koin.get<ArenaSessionProvider>(), koin.get<ArenaSessionProvider>())
            assertSame(f.status, koin.get<ArenaTicketStatusRemoteDataSource>())
            assertSame(f.remote, koin.get<ArenaTicketRemoteDataSource>())
            assertSame(f.clock, koin.get<Clock>())
            verify(f.database).arenaTicketDao()
            verifyNoMoreInteractions(f.database)
            assertEquals(0, f.clockReads)
            assertEquals(0, f.sessions.reads)
            f.assertNoFinancialActions()
        } finally {
            app.close()
        }
    }

    @Test
    fun standaloneArenaModuleKeepsExistingClockEngineAndSessionProvider() {
        val f = Fixture()
        val app = KoinApplication.init().modules(f.dependencies()).modules(arenaDataModule)
        try {
            assertSame(f.clock, app.koin.get<Clock>())
            assertSame(f.engine, app.koin.get<OutboxEngine>())
            assertSame(f.sessions, app.koin.get<OutboxSessionProvider>())
            assertIs<ArenaTicketJournal>(app.koin.get<ArenaTicketJournal>())
            assertIs<ArenaEntryRepositoryImpl>(app.koin.get<ArenaEntryRepository>())
            assertEquals(0, f.clockReads)
            assertEquals(0, f.sessions.reads)
            f.assertNoFinancialActions()
        } finally {
            app.close()
        }
    }

    @Test
    fun missingStrictCodecPreventsJournalResolutionInsteadOfUsingFallback() {
        val f = Fixture()
        val app = f.graph()
        try {
            app.koin.unloadModules(listOf(arenaCheckpointCodecModule))
            val failure = assertFailsWith<Exception> { app.koin.get<ArenaTicketJournal>() }
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it is NoDefinitionFoundException })
            assertFailsWith<NoDefinitionFoundException> { app.koin.get<ArenaJournalCheckpointCodec>() }
            val entryFailure = assertFailsWith<Exception> { app.koin.get<ArenaEntryRepository>() }
            assertTrue(generateSequence<Throwable>(entryFailure) { it.cause }.any { it is NoDefinitionFoundException })
            f.assertNoFinancialActions()
        } finally {
            app.close()
        }
    }

    @Test
    fun resolvedStrictCodecRejectsForgedFreshProgressAndUnknownAuthorityFields() {
        val f = Fixture()
        val app = f.graph()
        try {
            val codec = app.koin.get<ArenaJournalCheckpointCodec>()
            val snapshot = snapshot()
            val duration = computeTimer(snapshot.questions.single().display, Difficulty.EASY, TimerCoefficients.Default)
            val hash = "c".repeat(64)
            val initial =
                codec.initial(
                    snapshot,
                    hash,
                    ArenaInitialCheckpoint(SessionMode.LEARNING, 7L, 1_000L, 1_000L + duration.seconds * 1_000L),
                )
            codec.validate(snapshot, hash, null, initial, null)
            for (forged in listOf(
                initial.replace("\"indexInPool\":0", "\"indexInPool\":1"),
                initial.dropLast(1) + ",\"percentScore\":100}",
                initial.replace("\"ownerUid\":\"A\"", "\"ownerUid\":\"B\""),
            )) {
                assertFailsWith<IllegalArgumentException> { codec.validate(snapshot, hash, null, forged, null) }
            }
            f.assertNoFinancialActions()
        } finally {
            app.close()
        }
    }

    @Test
    fun resolvedOwnerGateUsesTheExistingEngineMutexAndRejectsLateOwnerGeneration() =
        runTest {
            val f = Fixture()
            val app = f.graph()
            try {
                val gate = app.koin.get<ArenaOwnerGate>()
                val session = app.koin.get<ArenaTicketJournal>().captureSession("A")
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val held =
                    launch {
                        f.engine.withoutPasses {
                            entered.complete(Unit)
                            release.await()
                        }
                    }
                entered.await()
                var published = false
                val pending = async { runCatching { gate.withOwner(session) { published = true } } }
                try {
                    runCurrent()
                    assertFalse(pending.isCompleted)
                    f.sessions.session = OutboxSession("B", 2)
                    f.sessions.session = OutboxSession("A", 3)
                } finally {
                    release.complete(Unit)
                }
                held.join()
                val failure = assertIs<ArenaRemoteFailure>(pending.await().exceptionOrNull())
                assertEquals(ArenaRemoteFailureKind.ACCOUNT_CHANGED, failure.kind)
                assertFalse(published)
                gate.withOwner(gate.capture("A")) { published = true }
                assertTrue(published)
                f.assertNoFinancialActions()
            } finally {
                app.close()
            }
        }

    @Test
    fun journalCodecBindingProducesActualUtf8Sha256WithoutUsingClockOrStorage() {
        val f = Fixture()
        val app = f.graph()
        try {
            val command =
                app.koin.get<ArenaJournalCodec>().consent(
                    ArenaPrepareRequest.Selected("A", "explicit-consent", Difficulty.EASY, "lesson", 10),
                    1_000L,
                )
            val expected =
                MessageDigest.getInstance("SHA-256").digest(command.bodyJson.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
            assertEquals(expected, command.bodySha256)
            assertEquals(64, command.bodySha256.length)
            assertEquals(0, f.clockReads)
            f.assertNoFinancialActions()
        } finally {
            app.close()
        }
    }

    @Test
    fun missingEntryProofModuleBlocksTheMapperResolverAndFacadeWithoutPermissiveBindings() {
        val f = Fixture()
        val app = f.graph()
        try {
            app.koin.unloadModules(listOf(arenaEntryProofModule))
            assertFailsWith<NoDefinitionFoundException> { app.koin.get<ArenaTicketWireParser>() }
            assertFailsWith<NoDefinitionFoundException> { app.koin.get<ArenaCancelledSettlementProof>() }
            for (resolve in listOf<() -> Any>(
                { app.koin.get<ArenaEntryLocalMapper>() },
                { app.koin.get<ArenaCancelledSettlementResolver>() },
                { app.koin.get<ArenaEntryRepository>() },
            )) {
                val failure = assertFailsWith<Exception> { resolve() }
                assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it is NoDefinitionFoundException })
            }
            assertEquals(0, f.clockReads)
            assertEquals(0, f.sessions.reads)
            f.assertNoFinancialActions()
        } finally {
            app.close()
        }
    }

    @Test
    fun missingStatusRemotePreventsFacadeResolutionEvenWhenPlainTicketRemoteExists() {
        val f = Fixture()
        val app = f.graph(includeStatus = false)
        try {
            assertSame(f.remote, app.koin.get<ArenaTicketRemoteDataSource>())
            val failure = assertFailsWith<Exception> { app.koin.get<ArenaEntryRepository>() }
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it is NoDefinitionFoundException })
            assertEquals(0, f.clockReads)
            assertEquals(0, f.sessions.reads)
            f.assertNoFinancialActions()
        } finally {
            app.close()
        }
    }

    @Test
    fun resolvedFacadeRejectsTheOriginalReturnedOwnerEpochBeforeAnyDaoOrStatusCall() =
        runTest {
            val f = Fixture()
            val app = f.graph()
            try {
                val repository = app.koin.get<ArenaEntryRepository>()
                assertEquals(0, f.sessions.reads)
                val old = ArenaSessionStamp("A", 1)
                f.sessions.session = OutboxSession("B", 2)
                f.sessions.session = OutboxSession("A", 3)
                val local = assertIs<ArenaEntryFailure>(repository.localTickets(old).exceptionOrNull())
                val resolution =
                    assertIs<ArenaEntryFailure>(
                        repository.reconcileCancelledSettlement(old, "a".repeat(64)).exceptionOrNull(),
                    )
                assertEquals(ArenaEntryFailureKind.ACCOUNT_CHANGED, local.kind)
                assertEquals(ArenaEntryFailureKind.ACCOUNT_CHANGED, resolution.kind)
                assertEquals(ArenaSessionStamp("A", 3), app.koin.get<ArenaSessionProvider>().current())
                assertEquals(0, f.clockReads)
                f.assertNoFinancialActions()
            } finally {
                app.close()
            }
        }

    @Test
    fun resolvedParserAndMapperUseStrictIdentityAndTerminalConservationWithoutReadingTheBank() {
        val f = Fixture()
        val app = f.graph()
        try {
            val id = digest("""["arena-ticket","A","request"]""")
            val header =
                snapshot().header.copy(
                    ticketId = id,
                    attemptId = digest("""["arena-attempt","$id"]"""),
                    dayId = "1970-01-01",
                )
            val codec = app.koin.get<ArenaJournalCodec>()
            val row = codec.record(header, 200)
            val public = row.termsJson.dropLast(1) + ""","status":"RESERVED"}"""
            val page = """{"tickets":[$public],"nextTicketId":null}"""
            val parser = app.koin.get<ArenaTicketWireParser>()
            assertEquals(listOf(header), parser.list(page, ArenaTicketListRequest("A")).tickets)
            assertFailsWith<ArenaRemoteFailure> { parser.list(page, ArenaTicketListRequest("B")) }
            val terminal =
                """{"receiptId":"arena-cancel:$id","cancelledAtMs":100,"releasedNolics":10,"releasedLifePoints":50}"""
            val cancelled =
                row.copy(
                    serverStatus = "CANCELLED",
                    terminalJson = terminal,
                    snapshotJson = "unsafe private bank",
                    snapshotSha256 = null,
                )
            val mapper = app.koin.get<ArenaEntryLocalMapper>()
            val summary = mapper.ticket("A", cancelled)
            assertEquals(ArenaLocalEntryState.TERMINAL_OBSERVED, summary.state)
            assertEquals(header.copy(status = ArenaTicketStatus.CANCELLED), summary.header)
            assertEquals(
                ArenaLocalEntryState.NEEDS_RECOVERY,
                mapper.ticket(
                    "A",
                    cancelled.copy(terminalJson = terminal.replace("\"releasedNolics\":10", "\"releasedNolics\":0")),
                ).state,
            )
            assertEquals(0, f.clockReads)
            assertEquals(0, f.sessions.reads)
            f.assertNoFinancialActions()
        } finally {
            app.close()
        }
    }

    private class Sessions : OutboxSessionProvider {
        var session: OutboxSession? = OutboxSession("A", 1)
        var reads = 0

        override fun current(): OutboxSession? {
            reads++
            return session
        }
    }

    private class Fixture {
        val database = mock(AppDatabase::class.java)
        val dao = mock(ArenaTicketDao::class.java)
        val remote = mock(ArenaTicketRemoteDataSource::class.java)
        val status = mock(ArenaTicketStatusRemoteDataSource::class.java)
        val network = mock(NetworkMonitor::class.java)
        val store = mock(OutboxStore::class.java)
        val receipts = mock(MutationReceiptStore::class.java)
        val transport = mock(MutationTransport::class.java)
        val sessions = Sessions()
        var clockReads = 0
        val clock =
            object : Clock {
                override fun now(): Instant {
                    clockReads++
                    return Instant.fromEpochMilliseconds(1_000L)
                }
            }
        val engine =
            OutboxEngine(
                store = store,
                transport = transport,
                clock = { error("Unexpected outbox drain") },
                onQuarantined = QuarantineListener { error("Unexpected quarantine") },
                receipts = receipts,
                sessions = sessions,
            )

        init {
            `when`(database.arenaTicketDao()).thenReturn(dao)
        }

        fun dependencies(includeStatus: Boolean = true) =
            module {
                single<AppDatabase> { database }
                single<ArenaTicketDao> { dao }
                single<ArenaTicketRemoteDataSource> { remote }
                if (includeStatus) single<ArenaTicketStatusRemoteDataSource> { status }
                single<NetworkMonitor> { network }
                single<OutboxEngine> { engine }
                single<OutboxSessionProvider> { sessions }
                single<Clock> { clock }
            }

        fun graph(includeStatus: Boolean = true): KoinApplication {
            // Keep the production DAO binding, overriding only the unopened database and external ports.
            val app = KoinApplication.init().modules(lessonRunnerDataModule).modules(dependencies(includeStatus))
            app.modules(persistenceModule)
            app.modules(module { single<AppDatabase> { database } })
            return app
        }

        fun assertNoFinancialActions() = verifyNoInteractions(dao, remote, status, network, store, receipts, transport)
    }

    private fun digest(canonicalJson: String): String =
        MessageDigest.getInstance("SHA-256").digest(canonicalJson.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun snapshot(): ArenaTicketSnapshot {
        val content =
            QuestionContent.SingleChoice(
                "q1",
                Difficulty.EASY,
                "Choose",
                null,
                listOf(QuestionContent.Option(OptionId("a"), "A"), QuestionContent.Option(OptionId("b"), "B")),
                OptionId("a"),
            )
        val header =
            ArenaTicketHeader(
                1, "a".repeat(64), "b".repeat(64), "A", "request", ArenaEntryMode.SELECTED, Difficulty.EASY,
                "lesson", 1L, ArenaTicketStatus.RESERVED, 10, 50L, 100L, "2026-09-07",
            )
        return ArenaTicketSnapshot(
            header,
            "author",
            1L,
            null,
            listOf(ArenaTicketQuestion("q1", 1L, 0, "en", "unused", content)),
        )
    }
}
