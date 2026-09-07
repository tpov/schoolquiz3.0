@file:Suppress("MagicNumber") // Explicit protocol/test boundaries, not runtime policy defaults.

package com.tpov.schoolquiz.shared.feature.lesson_runner.data.repository

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.SyncFailure
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.persistence.ARENA_RESOLVED_CANCELLED
import com.tpov.schoolquiz.shared.core.persistence.ArenaCommandEntity
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketRecordEntity
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaEntryLocalMapper
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaJournalStorage
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.ArenaTicketJournal
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.CancellationResolutionFixture
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.JournalCheckpointFake
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.JournalRemoteFake
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena.cancellationSha
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancelResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntriesCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryCommandOutcome
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryFailure
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryFailureKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaEntryResolution
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaLocalEntryState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationReason
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationState
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPreparationsCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRecoveryCursor
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketPage
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaEntryRepository
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionProvider
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArenaEntryRepositoryImplTest {
    @Test
    fun constructionAndLocalReadsNeverSendRecoverClaimOrMutateTheJournal() =
        runTest {
            val f = EntryFacadeFixture()
            f.seed(f.row)
            f.seedConsent(f.selected)
            val before = f.base.snapshot()
            val repository = f.repository(this)
            assertTrue(f.remote.boundSessions.isEmpty())
            assertEquals(
                ArenaLocalEntryState.PREPARED_SNAPSHOT,
                repository.readTicket(f.stamp, f.header.ticketId).getOrThrow()?.state,
            )
            assertEquals(1, repository.localTickets(f.stamp).getOrThrow().entries.size)
            assertEquals(1, repository.pendingPreparations(f.stamp).getOrThrow().preparations.size)
            assertNull(repository.readTicket(f.stamp, "0".repeat(64)).getOrThrow())
            assertEquals(before, f.base.snapshot())
            assertTrue(f.remote.boundSessions.isEmpty() && f.base.requests.isEmpty())
            assertEquals(0, f.networkReads)
        }

    @Test
    fun oneBoundedLocalQueryKeepsMalformedAndTerminalRowsVisibleAcrossMoreThanFiftyEntries() =
        runTest {
            val f = EntryFacadeFixture()
            repeat(52) { f.seed(f.headerRow("entry-$it")) }
            f.seed(f.headerRow("foreign", "other"))
            val terminal =
                f.codec.terminal(
                    f.row,
                    ArenaCancelResponse.Cancelled(f.header.ticketId, f.base.cancelled.receipt),
                    200,
                ).copy(snapshotJson = null, snapshotSha256 = null)
            f.seed(terminal)
            val repository = f.repository(this)
            val first = repository.localTickets(f.stamp, 50).getOrThrow()
            assertEquals(50, first.entries.size)
            assertEquals(1, f.storage.ticketPageReads)
            val second = repository.localTickets(f.stamp, 50, assertNotNull(first.nextCursor)).getOrThrow()
            val all = first.entries + second.entries
            assertEquals(53, all.size)
            assertEquals(53, all.map { it.ticketId }.toSet().size)
            assertTrue(all.all { it.header?.ownerUid == "player" })
            assertEquals(ArenaLocalEntryState.TERMINAL_OBSERVED, all.single { it.ticketId == f.header.ticketId }.state)
            assertEquals(52, all.count { it.state == ArenaLocalEntryState.NEEDS_RECOVERY })
            assertNull(second.nextCursor)
            assertEquals(listOf(51, 51), f.storage.localPageFetchLimits)
            assertTrue(f.remote.boundSessions.isEmpty())
        }

    @Test
    fun preparePagesOnlyIncludePendingOrAttentionPreparesWithUnchangedUtf8OrderAndConsent() =
        runTest {
            val f = EntryFacadeFixture()
            val ids =
                (0 until 51).map {
                    "request-${it.toString().padStart(
                        2,
                        '0',
                    )}"
                } + listOf("\uE000", "\uD800\uDC00")
            ids.reversed().forEach { f.seedConsent(ArenaPrepareRequest.Random("player", it, Difficulty.HARD)) }
            val special = f.storage.commands.getValue(Triple("player", "PREPARE", "\uE000"))
            f.storage.commands[Triple("player", "PREPARE", "\uE000")] =
                special.copy(
                    state = "ATTENTION",
                    lastFailure = "PRICE_CHANGED",
                )
            f.seedConsent(f.selected.copy(requestId = "acked"), "ACKNOWLEDGED")
            f.seedConsent(f.selected.copy(ownerUid = "other", requestId = "foreign"))
            f.storage.commands[
                Triple(
                    "player",
                    "CANCEL",
                    "cancel",
                ),
            ] = special.copy(commandKind = "CANCEL", commandId = "cancel")
            val repository = f.repository(this)
            val first = repository.pendingPreparations(f.stamp, 50).getOrThrow()
            val second = repository.pendingPreparations(f.stamp, 50, assertNotNull(first.nextCursor)).getOrThrow()
            assertEquals(ids, (first.preparations + second.preparations).map { it.requestId })
            assertEquals(2, f.storage.preparePageReads)
            val attention = second.preparations.single { it.requestId == "\uE000" }
            assertEquals(ArenaPreparationState.ATTENTION, attention.state)
            assertEquals(ArenaPreparationReason.PRICE_CHANGED_REQUIRES_CONSENT, attention.reason)
            assertIs<ArenaPrepareRequest.Random>(attention.request)
            assertNull(second.nextCursor)
            assertTrue(f.remote.boundSessions.isEmpty())
        }

    @Test
    fun allThreeOpaqueCursorsRejectForeignOwnerAndReturnedOwnerEpochBeforeAnyReadOrSend() =
        runTest {
            val f = EntryFacadeFixture()
            val rows = listOf(f.headerRow("one"), f.headerRow("two")).sortedBy { it.ticketId }
            rows.forEach(f::seed)
            f.seedConsent(f.selected)
            f.seedConsent(f.selected.copy(requestId = "second"))
            f.remote.listAction = {
                Result.success(
                    ArenaTicketPage(listOf(f.codec.header(rows.first())), rows.first().ticketId),
                )
            }
            val repository = f.repository(this)
            val old = f.stamp
            val tickets = assertNotNull(repository.localTickets(old, 1).getOrThrow().nextCursor)
            val prepares = assertNotNull(repository.pendingPreparations(old, 1).getOrThrow().nextCursor)
            val recovery = assertNotNull(repository.recoverPage(old, 1).getOrThrow().nextCursor)
            assertFalse(tickets is ArenaPreparationsCursor)
            assertFalse(prepares is ArenaRecoveryCursor)
            for (owner in listOf("other", "player")) {
                f.gate.switch(owner)
                val counts = listOf(f.storage.ticketPageReads, f.storage.preparePageReads, f.remote.boundSessions.size)
                failure(repository.localTickets(f.stamp, 1, tickets), ArenaEntryFailureKind.INVALID_REQUEST)
                failure(repository.pendingPreparations(f.stamp, 1, prepares), ArenaEntryFailureKind.INVALID_REQUEST)
                failure(repository.recoverPage(f.stamp, 1, recovery), ArenaEntryFailureKind.INVALID_REQUEST)
                assertEquals(
                    counts,
                    listOf(f.storage.ticketPageReads, f.storage.preparePageReads, f.remote.boundSessions.size),
                )
            }
        }

    @Test
    fun forgedCrossKindCursorAndInvalidBoundsCannotReachStorageOrRemote() =
        runTest {
            val f = EntryFacadeFixture()
            val repository = f.repository(this)
            val forged = object : ArenaEntriesCursor, ArenaPreparationsCursor, ArenaRecoveryCursor {}
            failure(repository.localTickets(f.stamp, 1, forged), ArenaEntryFailureKind.INVALID_REQUEST)
            failure(repository.pendingPreparations(f.stamp, 1, forged), ArenaEntryFailureKind.INVALID_REQUEST)
            failure(repository.recoverPage(f.stamp, 1, forged), ArenaEntryFailureKind.INVALID_REQUEST)
            for (size in listOf(0, 51, Int.MAX_VALUE)) {
                failure(repository.localTickets(f.stamp, size), ArenaEntryFailureKind.INVALID_REQUEST)
                failure(repository.pendingPreparations(f.stamp, size), ArenaEntryFailureKind.INVALID_REQUEST)
            }
            for (size in listOf(
                0,
                21,
                Int.MAX_VALUE,
            )) failure(repository.recoverPage(f.stamp, size), ArenaEntryFailureKind.INVALID_REQUEST)
            assertEquals(0, f.storage.ticketPageReads + f.storage.preparePageReads)
            assertTrue(f.remote.boundSessions.isEmpty())
        }

    @Test
    fun everyPublicOperationRejectsTheOriginalStampAfterAToBToABeforeItCanEnterJournal() =
        runTest {
            for (immediate in listOf(false, true)) {
                val f = EntryFacadeFixture(retainSettlement = true)
                val repository = f.repository(this, immediate = immediate)
                val original = f.stamp
                f.gate.switch("other")
                f.gate.switch("player")
                val before = f.base.snapshot()
                val operations: List<suspend () -> Result<*>> =
                    listOf(
                        { repository.localTickets(original) }, { repository.pendingPreparations(original) },
                        { repository.readTicket(original, f.header.ticketId) },
                        { repository.recordPrepareConsent(original, f.selected) },
                        { repository.dispatchPrepare(original, f.selected.requestId) },
                        { repository.recoverPage(original) }, { repository.recoverTicket(original, f.header.ticketId) },
                        { repository.recordCancelIntent(original, f.header.ticketId, "cancel") },
                        { repository.dispatchCancel(original, f.header.ticketId) },
                        { repository.reconcileCancelledSettlement(original, f.header.ticketId) },
                        { repository.restorePendingSettlement(original, f.header.ticketId) },
                    )
                for (operation in operations) failure(operation(), ArenaEntryFailureKind.ACCOUNT_CHANGED)
                assertEquals(before, f.base.snapshot())
                assertTrue(f.remote.boundSessions.isEmpty() && f.base.boundSessions.isEmpty())
                assertEquals(0, f.storage.ticketPageReads + f.storage.preparePageReads + f.networkReads)
            }
        }

    @Test
    fun dispatcherHandoffAndLateLocalReadCannotRenewTheInitiatingStamp() =
        runTest {
            val f = EntryFacadeFixture()
            f.seed(f.row)
            val repository = f.repository(this)
            val old = f.stamp
            val call = async(start = CoroutineStart.UNDISPATCHED) { repository.readTicket(old, f.header.ticketId) }
            f.gate.switch("other")
            f.gate.switch("player")
            failure(call.await(), ArenaEntryFailureKind.ACCOUNT_CHANGED)
            val readEntered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val storage =
                object : ArenaJournalStorage by f.storage {
                    override suspend fun ticket(
                        owner: String,
                        ticket: String,
                    ): ArenaTicketRecordEntity? {
                        val row = f.storage.ticket(owner, ticket)
                        readEntered.complete(Unit)
                        release.await()
                        return row
                    }
                }
            val current = f.stamp
            val late = async { f.repository(this@runTest, storage).readTicket(current, f.header.ticketId) }
            readEntered.await()
            // A deliberately external, outside-gate event: the post-read check must still drop the value.
            f.gate.current = OutboxSession("player", current.generation + 2)
            release.complete(Unit)
            failure(late.await(), ArenaEntryFailureKind.ACCOUNT_CHANGED)
        }

    @Test
    fun dispatcherReturnCannotDeliverAnOldEpochValueAfterTheIoBlockAlreadyPassedItsCheck() =
        runTest {
            val f = EntryFacadeFixture()
            f.seed(f.row)
            val original = f.stamp
            val storage =
                object : ArenaJournalStorage by f.storage {
                    override suspend fun ticket(
                        owner: String,
                        ticket: String,
                    ): ArenaTicketRecordEntity? {
                        val row = f.storage.ticket(owner, ticket)
                        // Queued before withContext posts its return to the caller's dispatcher.
                        this@runTest.launch {
                            f.gate.switch("other")
                            f.gate.switch("player")
                        }
                        return row
                    }
                }
            failure(
                f.repository(this, storage).readTicket(original, f.header.ticketId),
                ArenaEntryFailureKind.ACCOUNT_CHANGED,
            )
        }

    @Test
    fun explicitConsentIsDurableBeforeSendAndFreshRetryPreservesOriginalIdentityAndCeiling() =
        runTest {
            val f = EntryFacadeFixture()
            val repository = f.repository(this)
            val original = f.stamp
            repository.recordPrepareConsent(original, f.selected).getOrThrow()
            val frozen = f.storage.commands.values.single()
            assertTrue(f.remote.prepares.isEmpty())
            f.remote.beforeBoundCall = { expected ->
                f.gate.switch("other")
                f.gate.switch("player")
                f.gate.withOwner(expected) { Unit }
            }
            failure(repository.dispatchPrepare(original, f.selected.requestId), ArenaEntryFailureKind.ACCOUNT_CHANGED)
            assertEquals(frozen, f.storage.commands.values.single())
            assertTrue(f.remote.prepares.isEmpty())
            f.remote.beforeBoundCall = { expected -> f.gate.withOwner(expected) { Unit } }
            f.remote.prepareAction = { incoming ->
                assertEquals(f.selected, incoming)
                assertEquals(frozen.bodyJson, f.storage.commands.values.single().bodyJson)
                Result.success(ArenaPrepareResponse.Ticket(f.base.ticket))
            }
            assertEquals(
                ArenaEntryCommandOutcome.RECORDED,
                repository.dispatchPrepare(f.stamp, frozen.commandId).getOrThrow(),
            )
            assertEquals(f.selected, f.remote.prepares.single())
            assertEquals(frozen.bodyJson, f.storage.commands.values.single().bodyJson)
            assertEquals(frozen.bodySha256, f.storage.commands.values.single().bodySha256)
        }

    @Test
    fun failedOrCancelledConsentDoesNotSendAndUnknownReplyKeepsTheOriginalPendingCommand() =
        runTest {
            val f = EntryFacadeFixture()
            val repository = f.repository(this)
            f.online = false
            failure(repository.recordPrepareConsent(f.stamp, f.selected), ArenaEntryFailureKind.NO_NETWORK)
            assertTrue(f.storage.commands.isEmpty() && f.remote.prepares.isEmpty())
            f.online = true
            f.beforeNetwork = { throw CancellationException("consent cancelled") }
            cancelled { repository.recordPrepareConsent(f.stamp, f.selected) }
            assertTrue(f.storage.commands.isEmpty() && f.remote.prepares.isEmpty())
            f.beforeNetwork = {}
            repository.recordPrepareConsent(f.stamp, f.selected).getOrThrow()
            val frozen = f.storage.commands.values.single()
            f.remote.prepareAction = { Result.failure(IllegalStateException("SQL secret answerPayload")) }
            failure(repository.dispatchPrepare(f.stamp, f.selected.requestId), ArenaEntryFailureKind.UNAVAILABLE)
            val pending = f.storage.commands.values.single()
            assertEquals("PENDING", pending.state)
            assertEquals(frozen.bodyJson, pending.bodyJson)
            assertEquals(frozen.bodySha256, pending.bodySha256)
        }

    @Test
    fun priceChangeStaysExplicitAttentionAndCannotSilentlyRaiseOrReissueConsent() =
        runTest {
            val f = EntryFacadeFixture()
            val repository = f.repository(this)
            repository.recordPrepareConsent(f.stamp, f.selected).getOrThrow()
            val original = f.storage.commands.values.single()
            f.remote.prepareAction = { Result.success(ArenaPrepareResponse.PriceChanged) }
            repeat(2) {
                assertEquals(
                    ArenaEntryCommandOutcome.PRICE_CHANGED_REQUIRES_CONSENT,
                    repository.dispatchPrepare(f.stamp, f.selected.requestId).getOrThrow(),
                )
            }
            assertEquals(1, f.remote.prepares.size)
            failure(
                repository.recordPrepareConsent(f.stamp, f.selected.copy(maxPriceNolics = 100)),
                ArenaEntryFailureKind.CONFLICT,
            )
            val pending = repository.pendingPreparations(f.stamp).getOrThrow().preparations.single()
            assertEquals(f.selected, pending.request)
            assertEquals(ArenaPreparationReason.PRICE_CHANGED_REQUIRES_CONSENT, pending.reason)
            assertEquals(original.bodyJson, f.storage.commands.values.single().bodyJson)
            assertTrue(f.storage.tickets.isEmpty() && f.storage.outbox.isEmpty())
        }

    @Test
    fun localCancelIsNotServerCancellationAndRetryKeepsTheSameCancelRequest() =
        runTest {
            val f = EntryFacadeFixture()
            f.seed(f.row)
            val repository = f.repository(this)
            repository.recordCancelIntent(f.stamp, f.header.ticketId, "explicit-cancel").getOrThrow()
            assertTrue(f.remote.cancellations.isEmpty())
            assertEquals("RESERVED", f.storage.tickets.values.single().serverStatus)
            assertEquals(
                ArenaLocalEntryState.CANCEL_PENDING,
                repository.readTicket(f.stamp, f.header.ticketId).getOrThrow()?.state,
            )
            f.remote.cancelAction = { request ->
                Result.success(ArenaCancelResponse.RetryRegeneration(request.ticket.ticketId, request.requestId))
            }
            assertEquals(
                ArenaEntryCommandOutcome.RETRY_SAME_REQUEST,
                repository.dispatchCancel(f.stamp, f.header.ticketId).getOrThrow(),
            )
            f.remote.cancelAction = {
                Result.success(
                    ArenaCancelResponse.Cancelled(f.header.ticketId, f.base.cancelled.receipt),
                )
            }
            assertEquals(
                ArenaEntryCommandOutcome.RECORDED,
                repository.dispatchCancel(f.stamp, f.header.ticketId).getOrThrow(),
            )
            assertEquals(listOf("explicit-cancel", "explicit-cancel"), f.remote.cancellations.map { it.requestId })
            assertEquals(
                ArenaLocalEntryState.TERMINAL_OBSERVED,
                repository.readTicket(f.stamp, f.header.ticketId).getOrThrow()?.state,
            )
            assertTrue(f.storage.outbox.isEmpty() && f.storage.acknowledgedMutations.isEmpty())
        }

    @Test
    fun explicitRecoveryCachesHeadersThenFrozenTicketWithoutCreatingConsentOrSending() =
        runTest {
            val f = EntryFacadeFixture()
            val repository = f.repository(this)
            f.remote.listAction = { Result.success(ArenaTicketPage(listOf(f.header), null)) }
            val page = repository.recoverPage(f.stamp).getOrThrow()
            assertEquals(listOf(f.header), page.tickets)
            assertEquals(
                ArenaLocalEntryState.NEEDS_RECOVERY,
                repository.readTicket(f.stamp, f.header.ticketId).getOrThrow()?.state,
            )
            f.remote.getAction = { Result.success(f.base.ticket) }
            val recovered = repository.recoverTicket(f.stamp, f.header.ticketId).getOrThrow()
            assertEquals(ArenaLocalEntryState.PREPARED_SNAPSHOT, recovered.state)
            assertEquals(f.header, recovered.header)
            assertTrue(f.remote.prepares.isEmpty() && f.remote.cancellations.isEmpty())
            assertTrue(f.storage.commands.isEmpty() && f.storage.outbox.isEmpty())
            assertEquals(listOf(f.gate.current, f.gate.current), f.remote.boundSessions)
        }

    @Test
    fun pageRecoveryFailurePreservesCommittedPrefixAndDoesNotPretendAtomicSuccess() =
        runTest {
            val f = EntryFacadeFixture()
            val headers = listOf(f.headerRow("one"), f.headerRow("two")).map(f.codec::header).sortedBy { it.ticketId }
            var observed = 0
            val storage =
                object : ArenaJournalStorage by f.storage {
                    override suspend fun observe(row: ArenaTicketRecordEntity) {
                        observed++
                        if (observed == 2) error("database private details")
                        f.storage.observe(row)
                    }
                }
            f.remote.listAction = { Result.success(ArenaTicketPage(headers, null)) }
            failure(f.repository(this, storage).recoverPage(f.stamp), ArenaEntryFailureKind.UNAVAILABLE)
            assertEquals(listOf(headers.first().ticketId), f.storage.tickets.values.map { it.ticketId })
            assertTrue(f.storage.commands.isEmpty() && f.storage.outbox.isEmpty())
        }

    @Test
    fun resolverUsesOriginalStampAndResolvedCancellationNeverRequeuesOrFabricatesGenericReceipt() =
        runTest {
            val f = EntryFacadeFixture(retainSettlement = true)
            val repository = f.repository(this)
            val original = f.stamp
            f.gate.switch("other")
            f.gate.switch("player")
            val before = f.base.snapshot()
            failure(
                repository.reconcileCancelledSettlement(original, f.header.ticketId),
                ArenaEntryFailureKind.ACCOUNT_CHANGED,
            )
            assertEquals(before, f.base.snapshot())
            assertTrue(f.base.requests.isEmpty())
            val result =
                withTimeout(1000) {
                    repository.reconcileCancelledSettlement(f.stamp, f.header.ticketId).getOrThrow()
                }
            assertEquals(f.base.cancelled, assertIs<ArenaEntryResolution.CancelledResolved>(result).observation)
            assertEquals(listOf(f.gate.current), f.base.boundSessions)
            val retained = f.storage.commands.values.single()
            assertEquals(ARENA_RESOLVED_CANCELLED, retained.state)
            assertEquals(f.base.command.bodyJson, retained.bodyJson)
            val resolved = f.base.snapshot()
            repository.restorePendingSettlement(f.stamp, f.header.ticketId).getOrThrow()
            assertEquals(resolved, f.base.snapshot())
            assertTrue(f.storage.outbox.isEmpty() && f.storage.acknowledgedMutations.isEmpty())
            assertEquals(
                ArenaLocalEntryState.TERMINAL_OBSERVED,
                repository.readTicket(f.stamp, f.header.ticketId).getOrThrow()?.state,
            )
        }

    @Test
    fun explicitSettlementRestoreUsesOnlyTheExistingSenderAndOriginalBody() =
        runTest {
            val f = EntryFacadeFixture(retainSettlement = true)
            f.storage.outbox.clear()
            val repository = f.repository(this)
            repository.restorePendingSettlement(f.stamp, f.header.ticketId).getOrThrow()
            val delivery = f.storage.outbox.values.single()
            assertEquals(f.base.delivery.mutationId, delivery.mutationId)
            assertEquals(f.base.delivery.payload, delivery.payload)
            assertEquals(f.base.delivery.ownerUid, delivery.ownerUid)
            assertEquals(f.base.delivery.operation, delivery.operation)
            assertEquals(f.base.delivery.entityRef, delivery.entityRef)
            assertTrue(f.remote.boundSessions.isEmpty() && f.base.requests.isEmpty())
        }

    @Test
    fun cancelledAndStaleStatusNeverProduceAResolvedDomainSuccess() =
        runTest {
            val f = EntryFacadeFixture(retainSettlement = true)
            val repository = f.repository(this)
            val before = f.base.snapshot()
            f.base.action = { throw CancellationException("status cancelled") }
            cancelled { repository.reconcileCancelledSettlement(f.stamp, f.header.ticketId) }
            assertEquals(before, f.base.snapshot())
            val original = f.stamp
            f.base.action = {
                f.gate.switch("other")
                f.gate.switch("player")
                Result.success(f.base.cancelled)
            }
            failure(
                repository.reconcileCancelledSettlement(original, f.header.ticketId),
                ArenaEntryFailureKind.ACCOUNT_CHANGED,
            )
            assertEquals(before, f.base.snapshot())
        }

    @Test
    fun missingAccountAndFailedDurableConsentHaveNoHiddenDispatchOrSuccessfulLocalClaim() =
        runTest {
            val f = EntryFacadeFixture()
            f.sessionAvailable = false
            failure(f.repository(this).localTickets(f.stamp), ArenaEntryFailureKind.ACCOUNT_UNAVAILABLE)
            assertEquals(0, f.storage.ticketPageReads)
            f.sessionAvailable = true
            val storage =
                object : ArenaJournalStorage by f.storage {
                    override suspend fun saveConsent(command: ArenaCommandEntity) =
                        error("disk private consent details")
                }
            failure(
                f.repository(this, storage).recordPrepareConsent(f.stamp, f.selected),
                ArenaEntryFailureKind.UNAVAILABLE,
            )
            assertTrue(f.storage.commands.isEmpty() && f.remote.prepares.isEmpty())
            assertTrue(f.storage.tickets.isEmpty() && f.storage.outbox.isEmpty())
        }

    @Test
    fun failedStorageAndNetworkDetailsAreClosedAndCancellationKeepsItsOriginalMeaning() =
        runTest {
            val f = EntryFacadeFixture()
            for ((error, expected) in listOf(
                IllegalStateException("SQL private snapshot") to ArenaEntryFailureKind.UNAVAILABLE,
                IllegalArgumentException("secret parsed answers") to ArenaEntryFailureKind.NEEDS_RECOVERY,
                SyncFailure(SyncError.Refused("private backend detail")) to ArenaEntryFailureKind.UNAVAILABLE,
                SyncFailure(SyncError.VersionConflict(7)) to ArenaEntryFailureKind.CONFLICT,
            )) {
                val storage =
                    object : ArenaJournalStorage by f.storage {
                        override suspend fun ticket(
                            owner: String,
                            ticket: String,
                        ): ArenaTicketRecordEntity? = throw error
                    }
                failure(f.repository(this, storage).readTicket(f.stamp, f.header.ticketId), expected)
            }
            val original = CancellationException("same cancellation")
            val storage =
                object : ArenaJournalStorage by f.storage {
                    override suspend fun ticket(
                        owner: String,
                        ticket: String,
                    ): ArenaTicketRecordEntity? = throw original
                }
            val actual = cancelled { f.repository(this, storage).readTicket(f.stamp, f.header.ticketId) }
            assertTrue(actual === original || actual.cause === original)
        }

    private fun failure(
        result: Result<*>,
        expected: ArenaEntryFailureKind,
    ) {
        val error = assertIs<ArenaEntryFailure>(result.exceptionOrNull())
        assertEquals(expected, error.kind)
        assertEquals(expected.name, error.message)
        assertNull(error.cause)
    }

    private suspend fun cancelled(block: suspend () -> Any?): CancellationException =
        try {
            block()
            throw AssertionError("Cancellation must escape, never Result.failure or success")
        } catch (error: CancellationException) {
            error
        }
}

/** Reuses the canonical real-journal storage/gate/proof fixture; no duplicate permissive journal fake. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class EntryFacadeFixture(retainSettlement: Boolean = false) {
    val base = CancellationResolutionFixture()
    val codec get() = base.codec
    val parser get() = base.parser
    val storage get() = base.storage
    val gate get() = base.gate
    val header get() = base.header
    val row get() = codec.record(base.ticket, 100)
    val mapper = ArenaEntryLocalMapper(codec, parser)
    val selected = ArenaPrepareRequest.Selected("player", "client-selected", Difficulty.EASY, "lesson", 12)
    val remote = JournalRemoteFake()
    var online = true
    var sessionAvailable = true
    var networkReads = 0
    var beforeNetwork: suspend () -> Unit = {}
    val stamp get() = ArenaSessionStamp(gate.current.ownerUid, gate.current.generation)

    init {
        if (!retainSettlement) {
            storage.tickets.clear()
            storage.commands.clear()
            storage.outbox.clear()
            storage.firstIntents.clear()
        }
        remote.beforeBoundCall = { expected -> gate.withOwner(expected) { Unit } }
    }

    fun repository(
        scope: TestScope,
        actualStorage: ArenaJournalStorage = storage,
        immediate: Boolean = false,
    ): ArenaEntryRepository {
        val dispatcher =
            if (immediate) {
                UnconfinedTestDispatcher(
                    scope.testScheduler,
                )
            } else {
                StandardTestDispatcher(scope.testScheduler)
            }
        val journal =
            ArenaTicketJournal(
                actualStorage,
                remote,
                gate,
                object : NetworkMonitor {
                    override fun observeOnline() = flowOf(online)

                    override suspend fun isOnline(): Boolean {
                        networkReads++
                        beforeNetwork()
                        return online
                    }
                },
                codec,
                JournalCheckpointFake(),
                { 200L },
                dispatcher,
            )
        return ArenaEntryRepositoryImpl(
            actualStorage,
            journal,
            base.resolver(scope, actualStorage, immediate),
            mapper,
            gate,
            ArenaSessionProvider { if (sessionAvailable) stamp else null },
            dispatcher,
        )
    }

    fun seed(row: ArenaTicketRecordEntity) {
        storage.tickets[row.ownerUid to row.ticketId] = row
    }

    fun seedConsent(
        request: ArenaPrepareRequest,
        state: String = "PENDING",
    ) {
        val command = codec.consent(request, 100).copy(state = state)
        storage.commands[Triple(request.ownerUid, "PREPARE", request.requestId)] = command
    }

    fun headerRow(
        requestId: String,
        owner: String = "player",
    ): ArenaTicketRecordEntity {
        val ticket = digest("arena-ticket", owner, requestId)
        return codec.record(
            header.copy(
                ownerUid = owner,
                requestId = requestId,
                ticketId = ticket,
                attemptId = digest("arena-attempt", ticket),
            ),
            100,
        )
    }

    private fun digest(vararg parts: String): String = cancellationSha(JsonArray(parts.map(::JsonPrimitive)).toString())
}
