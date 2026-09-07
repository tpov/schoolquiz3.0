package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real SQLite keyset order/filters. Page-size validation and opaque cursors belong to the data adapter. */
class ArenaLocalPagePersistenceTest {
    private lateinit var directory: File
    private lateinit var db: AppDatabase
    private val dao get() = db.arenaTicketDao()

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("arena-local-pages").toFile()
        db = open()
    }

    private fun open() =
        Room.databaseBuilder<AppDatabase>(name = File(directory, "arena.db").absolutePath)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO)
            .addTypeConverter(StringSetConverter()).addTypeConverter(TopParticipantListConverter()).build()

    @After
    fun tearDown() {
        db.close()
        directory.deleteRecursively()
    }

    private fun ticket(
        id: String,
        owner: String = "A",
        status: String = "RESERVED",
    ) = ArenaTicketRecordEntity(
        owner, id, "attempt-$id", "request-$id", "opaque terms", null, "hash-$id", status,
        if (status == "RESERVED") null else "opaque terminal", "LAUNCHED", "SETTLE", 100,
        "opaque checkpoint", 4,
    )

    private fun command(
        id: String,
        owner: String = "A",
        kind: String = "PREPARE",
        state: String = "PENDING",
    ) = ArenaCommandEntity(owner, kind, id, null, null, "{not parsed", "hash", state, "retained failure", 100)

    private suspend fun sql(statement: String) =
        db.useWriterConnection { connection -> connection.usePrepared(statement) { it.step() } }

    @Test
    fun ticketsKeepTerminalOpaqueRowsAndSeparateOwnersWithTheSameIds() =
        runTest {
            val expected =
                listOf(ticket("a"), ticket("b", status = "CANCELLED"), ticket("c", status = "SETTLED"))
            for (row in expected.reversed()) {
                dao.insertTicket(row)
                dao.insertTicket(row.copy(ownerUid = "B", termsJson = "foreign"))
            }
            assertEquals(expected, dao.selectLocalTickets("A", null, 51))
            assertEquals(
                expected.map { it.copy(ownerUid = "B", termsJson = "foreign") },
                dao.selectLocalTickets("B", null, 51),
            )
            assertTrue(dao.selectLocalTickets("missing-owner", null, 51).isEmpty())
            db.close()
            db = open()
            assertEquals(expected, dao.selectLocalTickets("A", null, 51))
        }

    @Test
    fun ticketLookaheadOverFiftyContinuesFromLastVisibleIdWithoutDuplicatesOrTimestampTies() =
        runTest {
            val expected = (0 until 103).map { "ticket-" + it.toString().padStart(3, '0') }
            for ((index, id) in expected.withIndex().reversed()) {
                dao.insertTicket(ticket(id, status = listOf("RESERVED", "CANCELLED", "SETTLED")[index % 3]))
                dao.insertTicket(ticket(id, owner = "B"))
            }
            var cursor: String? = null
            val seen = mutableListOf<String>()
            val rawSizes = mutableListOf<Int>()
            repeat(3) {
                val raw = dao.selectLocalTickets("A", cursor, 51)
                assertTrue(raw.size <= 51)
                rawSizes += raw.size
                val visible = raw.take(50)
                seen += visible.map { it.ticketId }
                cursor = if (raw.size > 50) visible.last().ticketId else null
            }
            assertEquals(listOf(51, 51, 3), rawSizes)
            assertEquals(expected, seen)
            assertEquals(seen.size, seen.toSet().size)
            assertNull(cursor)
        }

    @Test
    fun prepareQueryIncludesOnlyPendingAndAttentionOfThatOwnerAndKind() =
        runTest {
            val expected = listOf(command("a"), command("b", state = "ATTENTION"))
            for (row in expected) dao.insertCommand(row)
            for (kind in listOf("CANCEL", "SETTLE", "UNKNOWN")) {
                for (id in listOf("a", "b", "c")) dao.insertCommand(command(id, kind = kind))
            }
            for ((index, state) in listOf("ACKNOWLEDGED", "RESOLVED_CANCELLED", "QUARANTINED", "UNKNOWN").withIndex()) {
                dao.insertCommand(command("other-$index", state = state))
            }
            for (row in expected) dao.insertCommand(row.copy(ownerUid = "B"))
            assertEquals(expected, dao.selectLocalPendingPrepares("A", null, 51))
            assertEquals(expected.map { it.copy(ownerUid = "B") }, dao.selectLocalPendingPrepares("B", null, 51))
            assertTrue(dao.selectLocalPendingPrepares("missing-owner", null, 51).isEmpty())
        }

    @Test
    fun prepareLookaheadOverFiftyUsesCommandIdRatherThanSharedCreationTime() =
        runTest {
            val expected = (0 until 103).map { "request-" + it.toString().padStart(3, '0') }
            for ((index, id) in expected.withIndex().reversed()) {
                dao.insertCommand(command(id, state = if (index % 2 == 0) "PENDING" else "ATTENTION"))
                dao.insertCommand(command(id, kind = "CANCEL"))
                dao.insertCommand(command(id, owner = "B"))
            }
            var cursor: String? = null
            val seen = mutableListOf<String>()
            val rawSizes = mutableListOf<Int>()
            repeat(3) {
                val raw = dao.selectLocalPendingPrepares("A", cursor, 51)
                rawSizes += raw.size
                val visible = raw.take(50)
                seen += visible.map { it.commandId }
                cursor = if (raw.size > 50) visible.last().commandId else null
            }
            assertEquals(listOf(51, 51, 3), rawSizes)
            assertEquals(expected, seen)
            assertEquals(seen.size, seen.toSet().size)
            assertNull(cursor)
        }

    @Test
    fun binaryUtf8OrderingAndExclusiveBoundaryDifferFromKotlinUtf16ForValidNonAsciiRequestIds() =
        runTest {
            val expected = listOf("a", "z", "é", "\uE000", "\uD800\uDC00", "\uD83D\uDE00")
            assertNotEquals(expected, expected.sorted()) // UTF-16 puts supplementary characters before U+E000.
            for (id in expected.reversed()) {
                dao.insertCommand(command(id))
                dao.insertTicket(ticket(id)) // Storage is opaque; normal issued ticket IDs are hexadecimal.
            }
            assertEquals(expected, dao.selectLocalPendingPrepares("A", null, 51).map { it.commandId })
            assertEquals(expected, dao.selectLocalTickets("A", null, 51).map { it.ticketId })
            for ((index, anchor) in expected.withIndex()) {
                val remaining = expected.drop(index + 1)
                assertEquals(remaining, dao.selectLocalPendingPrepares("A", anchor, 51).map { it.commandId })
                assertEquals(remaining, dao.selectLocalTickets("A", anchor, 51).map { it.ticketId })
            }
        }

    @Test
    fun deletedAnchorAndInsertedEarlierRowDoNotBreakExclusiveContinuation() =
        runTest {
            for (id in listOf("a", "b", "c")) dao.insertCommand(command(id))
            assertEquals(listOf("a", "b"), dao.selectLocalPendingPrepares("A", null, 2).map { it.commandId })
            sql(
                "DELETE FROM arena_command_journal WHERE owner_uid = 'A' " +
                    "AND command_kind = 'PREPARE' AND command_id = 'a'",
            )
            dao.insertCommand(command("0-before-anchor"))
            dao.insertCommand(command("d"))
            assertEquals(listOf("b", "c", "d"), dao.selectLocalPendingPrepares("A", "a", 51).map { it.commandId })
            assertEquals(
                listOf("0-before-anchor", "b", "c", "d"),
                dao.selectLocalPendingPrepares("A", null, 51).map { it.commandId },
            ) // Pages are not an atomic complete inventory; a fresh scan sees earlier insertions.
        }

    @Test
    fun singleBoundedSelectorsCannotWriteCheckpointsQueueCommandsOrReceipts() =
        runTest {
            val row = ticket("ticket", status = "CANCELLED")
            val pending = command("request", state = "ATTENTION")
            dao.insertTicket(row)
            dao.insertCommand(pending)
            for (table in listOf(
                "arena_ticket_records",
                "arena_command_journal",
                "lesson_attempt_intents",
                "outbox",
                "mutation_receipts",
            )) {
                for (event in listOf("INSERT", "UPDATE", "DELETE")) {
                    sql(
                        "CREATE TRIGGER reject_${table}_$event BEFORE $event ON $table " +
                            "BEGIN SELECT RAISE(ABORT, 'local page must only read'); END",
                    )
                }
            }
            assertEquals(listOf(row), dao.selectLocalTickets("A", null, 2))
            assertEquals(listOf(pending), dao.selectLocalPendingPrepares("A", null, 2))
            assertTrue(dao.selectLocalTickets("A", "zzz", 2).isEmpty())
            assertTrue(dao.selectLocalPendingPrepares("A", "zzz", 2).isEmpty())
            assertEquals(row, dao.ticket("A", "ticket"))
            assertEquals(pending, dao.command("A", "PREPARE", "request"))
        }
}
