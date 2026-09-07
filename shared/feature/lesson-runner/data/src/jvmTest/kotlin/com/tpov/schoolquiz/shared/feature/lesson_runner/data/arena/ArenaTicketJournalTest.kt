package com.tpov.schoolquiz.shared.feature.lesson_runner.data.arena

import com.tpov.schoolquiz.shared.core.network.NetworkMonitor
import com.tpov.schoolquiz.shared.core.outbox.OutboxSession
import com.tpov.schoolquiz.shared.core.persistence.ArenaTicketRecordEntity
import com.tpov.schoolquiz.shared.core.question_schema.Difficulty
import com.tpov.schoolquiz.shared.core.scoring.ChargeClaimMask
import com.tpov.schoolquiz.shared.feature.lesson.domain.model.LessonId
import com.tpov.schoolquiz.shared.feature.lesson_runner.data.remote.ArenaTicketWireParser
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.logic.arenaCheckpointCompletion
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancelResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaCancellationReceipt
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaInitialCheckpoint
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaPrepareResponse
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaTicketGetRequest
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptCompletionKind
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptId
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.AttemptIdentity
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.SessionMode
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.UnscoredTerminalIntent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArenaTicketJournalTest {
    private val fixture =
        javaClass.classLoader!!.getResourceAsStream("arena-ticket-client-fixtures.json")!!
            .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
    private val request = ArenaPrepareRequest.Selected("player", "client-selected", Difficulty.EASY, "lesson", 12)
    private val codec = ArenaJournalCodec(::sha)
    private val prepared =
        assertIs<ArenaPrepareResponse.Ticket>(
            ArenaTicketWireParser(::sha).prepare(fixture.getValue("issuedSelected").toString(), request),
        )
    private val header get() = prepared.ticket.snapshot.header
    private val storage = JournalStorageFake()
    private val gate = JournalGateFake()
    private val remote = JournalRemoteFake()
    private val checkpoints = JournalCheckpointFake()
    private val initial get() =
        ArenaInitialCheckpoint(
            SessionMode.LEARNING,
            17,
            header.issuedAtMs,
            header.issuedAtMs + 30_000,
        )
    private var online = true
    private var now = 2000L

    private fun TestScope.journal() =
        ArenaTicketJournal(
            storage,
            remote,
            gate,
            object : NetworkMonitor {
                override fun observeOnline() = flowOf(online)

                override suspend fun isOnline() = online
            },
            codec,
            checkpoints,
            { now },
            StandardTestDispatcher(testScheduler),
        )

    private fun intent() =
        UnscoredTerminalIntent(
            AttemptIdentity(
                AttemptId(header.attemptId),
                "player",
                LessonId(header.lessonId),
                header.lessonVersion,
                Difficulty.EASY,
            ),
            AttemptCompletionKind.COMPLETED,
            header.issuedAtMs + 1000,
            emptyList(),
            emptyList(),
            ChargeClaimMask.none(1),
        )

    @Test
    fun consentIsOnlineExplicitAndPersistedBeforeTheFirstRemoteCall() =
        runTest {
            val journal = journal()
            val session = gate.current
            online = false
            assertTrue(journal.recordPrepareConsent(session, request).isFailure)
            assertTrue(storage.commands.isEmpty())
            assertTrue(remote.prepares.isEmpty())
            online = true
            journal.recordPrepareConsent(session, request).getOrThrow()
            remote.prepareAction = { incoming ->
                assertEquals(request, incoming)
                assertEquals(
                    12,
                    assertIs<ArenaPrepareRequest.Selected>(
                        codec.prepare(storage.commands.values.single()),
                    ).maxPriceNolics,
                )
                Result.success(prepared)
            }
            assertEquals(
                ArenaCommandDispatch.RECORDED,
                journal.dispatchPrepare(session, request.requestId).getOrThrow(),
            )
            assertEquals(
                ArenaCommandDispatch.ALREADY_RECORDED,
                journal.dispatchPrepare(session, request.requestId).getOrThrow(),
            )
            assertEquals(1, remote.prepares.size)
        }

    @Test
    fun unknownReplyAndProcessRestartPreserveOriginalIdentityAndCeiling() =
        runTest {
            val first = journal()
            val session = gate.current
            first.recordPrepareConsent(session, request).getOrThrow()
            remote.prepareAction = { Result.failure(IllegalStateException("lost reply")) }
            assertTrue(first.dispatchPrepare(session, request.requestId).isFailure)
            val frozen = storage.commands.values.single().bodyJson
            assertEquals("PENDING", storage.commands.values.single().state)
            now = 9000
            val restarted = journal()
            remote.prepareAction = { Result.success(prepared) }
            restarted.dispatchPrepare(session, request.requestId).getOrThrow()
            assertEquals(frozen, storage.commands.values.single().bodyJson)
            assertEquals(listOf<ArenaPrepareRequest>(request, request), remote.prepares.toList())
        }

    @Test
    fun sameIdCannotChangeConsentAndPriceChangeRequiresAnotherExplicitConsent() =
        runTest {
            val journal = journal()
            val session = gate.current
            journal.recordPrepareConsent(session, request).getOrThrow()
            assertTrue(journal.recordPrepareConsent(session, request.copy(maxPriceNolics = 100)).isFailure)
            remote.prepareAction = { Result.success(ArenaPrepareResponse.PriceChanged) }
            assertEquals(
                ArenaCommandDispatch.PRICE_CHANGED,
                journal.dispatchPrepare(session, request.requestId).getOrThrow(),
            )
            assertEquals("ATTENTION", storage.commands.values.single().state)
            assertTrue(journal.dispatchPrepare(session, request.requestId).isFailure)
            assertTrue(storage.tickets.isEmpty())
        }

    @Test
    fun accountAtoBtoARejectsLateReplyWithoutWritingOrAcknowledgingIt() =
        runTest {
            val journal = journal()
            val session = gate.current
            journal.recordPrepareConsent(session, request).getOrThrow()
            remote.prepareAction = {
                gate.switch("other")
                gate.switch("player")
                Result.success(prepared)
            }
            assertTrue(journal.dispatchPrepare(session, request.requestId).isFailure)
            assertTrue(storage.tickets.isEmpty())
            assertEquals("PENDING", storage.commands.values.single().state)
            assertTrue(journal.read(session, header.ticketId).isFailure)
            assertTrue(journal.recordPrepareConsent(OutboxSession("other", gate.current.generation), request).isFailure)
        }

    @Test
    fun cancellationOfAwaitDoesNotAnnounceServerCancellationOrConsumePendingIntent() =
        runTest {
            val journal = journal()
            val session = gate.current
            journal.recordPrepareConsent(session, request).getOrThrow()
            remote.prepareAction = { throw CancellationException("screen stopped") }
            assertFailsWith<CancellationException> { journal.dispatchPrepare(session, request.requestId) }
            assertEquals("PENDING", storage.commands.values.single().state)
            assertTrue(storage.tickets.isEmpty())
        }

    @Test
    fun persistenceFailureAfterServerReplyKeepsPendingAndNeverReportsRecorded() =
        runTest {
            val journal = journal()
            val session = gate.current
            journal.recordPrepareConsent(session, request).getOrThrow()
            remote.prepareAction = { Result.success(prepared) }
            storage.failAcknowledgment = true
            assertTrue(journal.dispatchPrepare(session, request.requestId).isFailure)
            assertEquals("PENDING", storage.commands.values.single().state)
            assertTrue(storage.tickets.isEmpty())
        }

    @Test
    fun recoveryWithoutOriginalConsentNeverReconstructsPurchaseCeiling() =
        runTest {
            val journal = journal()
            val session = gate.current
            remote.getAction = { Result.success(prepared.ticket) }
            assertIs<ArenaCachedTicket.Available>(journal.recoverTicket(session, header.ticketId).getOrThrow())
            assertTrue(journal.dispatchPrepare(session, request.requestId).isFailure)
            assertTrue(remote.prepares.isEmpty())
            assertTrue(storage.commands.isEmpty())
            val row = storage.ticket("player", header.ticketId)!!
            assertFalse("status" in Json.parseToJsonElement(row.termsJson).jsonObject)
            assertFalse("maxPriceNolics" in row.termsJson)
        }

    @Test
    fun invalidSnapshotIsStillCancellableAndOnlyConfirmedReplyIsTerminal() =
        runTest {
            val journal = journal()
            val session = gate.current
            storage.observe(codec.record(header, now).copy(snapshotJson = "corrupt", snapshotSha256 = "wrong"))
            assertIs<ArenaCachedTicket.SnapshotUnavailable>(journal.read(session, header.ticketId).getOrThrow())
            assertIs<ArenaLaunchClaim.NotFresh>(journal.claimForLaunch(session, header.ticketId, initial).getOrThrow())
            journal.requestCancel(session, header.ticketId, "cancel-original").getOrThrow()
            remote.cancelAction = {
                Result.success(
                    ArenaCancelResponse.RetryRegeneration(header.ticketId, it.requestId),
                )
            }
            assertEquals(
                ArenaCommandDispatch.RETRY_REGEN,
                journal.dispatchCancel(session, header.ticketId).getOrThrow(),
            )
            assertEquals("RESERVED", storage.ticket("player", header.ticketId)!!.serverStatus)
            remote.cancelAction = {
                Result.success(
                    ArenaCancelResponse.Cancelled(
                        header.ticketId,
                        ArenaCancellationReceipt(
                            "arena-cancel:${header.ticketId}",
                            header.issuedAtMs,
                            header.priceNolics,
                            header.lifeCost,
                        ),
                    ),
                )
            }
            journal.dispatchCancel(session, header.ticketId).getOrThrow()
            assertIs<ArenaCachedTicket.Terminal>(journal.read(session, header.ticketId).getOrThrow())
            assertEquals(listOf("cancel-original", "cancel-original"), remote.cancellations.map { it.requestId })
        }

    @Test
    fun launchedSnapshotAndOriginalAttemptSurviveRestartWithoutAnyLiveHierarchyDependency() =
        runTest {
            val journal = journal()
            val session = gate.current
            storage.observe(codec.record(prepared.ticket, now))
            val claim =
                assertIs<ArenaLaunchClaim.Claimed>(
                    journal.claimForLaunch(session, header.ticketId, initial).getOrThrow(),
                )
            assertEquals(header.attemptId, claim.snapshot.header.attemptId)
            assertFalse(journal.evictInactiveSnapshot(session, header.ticketId).getOrThrow())
            val restarted = journal()
            assertIs<ArenaCachedTicket.ResumeOrReconcile>(restarted.read(session, header.ticketId).getOrThrow())
            assertIs<ArenaLaunchClaim.NotFresh>(
                restarted.claimForLaunch(session, header.ticketId, initial).getOrThrow(),
            )
            assertEquals("0", restarted.checkpoint(session, header.ticketId).getOrThrow().json)
            assertTrue(restarted.freezeCompletion(session, header.ticketId, intent()).isFailure)
            assertTrue(restarted.updateCheckpoint(session, header.ticketId, header.attemptId, 0, "1").getOrThrow())
            restarted.freezeCompletion(session, header.ticketId, intent()).getOrThrow()
            val frozen = storage.outbox.values.single()
            val body = Json.parseToJsonElement(frozen.payload).jsonObject
            assertEquals(JsonPrimitive(header.attemptId), body.getValue("attemptId"))
            assertEquals(JsonPrimitive(header.lessonVersion), body.getValue("lessonVersion"))
            assertFalse("percentScore" in body)
            assertFalse("codeAnswer" in body)
            storage.outbox.clear()
            now = 99999
            restarted.restorePendingSettlement(session, header.ticketId).getOrThrow()
            assertEquals(frozen, storage.outbox.values.single())
            assertTrue(restarted.requestCancel(session, header.ticketId, "abort-after-complete").isFailure)
        }

    @Test
    fun failedTypedCheckpointValidationLeavesTicketAvailableAndNoPartialLaunch() =
        runTest {
            val journal = journal()
            val session = gate.current
            storage.observe(codec.record(prepared.ticket, now))
            checkpoints.reject = true
            assertTrue(journal.claimForLaunch(session, header.ticketId, initial).isFailure)
            assertIs<ArenaCachedTicket.Available>(journal.read(session, header.ticketId).getOrThrow())
            assertEquals(null, storage.ticket("player", header.ticketId)!!.checkpointJson)
            checkpoints.reject = false
            assertIs<ArenaLaunchClaim.Claimed>(journal.claimForLaunch(session, header.ticketId, initial).getOrThrow())
        }

    @Test
    fun terminalEvictionStillAllowsOnlyTheExactAlreadyFrozenCompletionWithoutWritingOrReenqueueing() =
        runTest {
            val journal = journal()
            val session = gate.current
            storage.observe(codec.record(prepared.ticket, now))
            journal.claimForLaunch(session, header.ticketId, initial).getOrThrow()
            journal.updateCheckpoint(session, header.ticketId, header.attemptId, 0, "1").getOrThrow()
            val completed = intent()
            journal.freezeCompletion(session, header.ticketId, completed).getOrThrow()
            remote.getAction = {
                Result.success(
                    ArenaTicketWireParser(::sha).get(
                        fixture.getValue("recoveredCancelled").toString(),
                        ArenaTicketGetRequest(session.ownerUid, header.ticketId),
                    ),
                )
            }
            assertIs<ArenaCachedTicket.Terminal>(journal.recoverTicket(session, header.ticketId).getOrThrow())
            assertTrue(journal.evictInactiveSnapshot(session, header.ticketId).getOrThrow())
            val ticketsBefore = storage.tickets.toMap()
            val commandsBefore = storage.commands.toMap()
            val queueBefore = storage.outbox.toMap()
            assertEquals(null, ticketsBefore.values.single().snapshotJson)
            assertEquals(null, ticketsBefore.values.single().checkpointJson)
            checkpoints.reject = true // A replay does not require or substitute a missing checkpoint/snapshot.
            val restarted = journal()
            restarted.freezeCompletion(session, header.ticketId, completed).getOrThrow()
            assertTrue(
                restarted.freezeCompletion(
                    session,
                    header.ticketId,
                    completed.copy(endedAtMs = completed.endedAtMs + 1),
                ).isFailure,
            )
            assertTrue(
                restarted.freezeCompletion(
                    session,
                    header.ticketId,
                    completed.copy(claims = ChargeClaimMask("S")),
                ).isFailure,
            )
            assertTrue(
                restarted.freezeCompletion(
                    session,
                    header.ticketId,
                    completed.copy(identity = completed.identity.copy(userId = "other")),
                ).isFailure,
            )
            assertEquals(ticketsBefore, storage.tickets)
            assertEquals(commandsBefore, storage.commands)
            assertEquals(queueBefore, storage.outbox)
            storage.outbox.clear()
            restarted.freezeCompletion(session, header.ticketId, completed).getOrThrow()
            assertTrue(storage.outbox.isEmpty())
            assertEquals(commandsBefore, storage.commands)
        }

    @Test
    fun checkpointRevisionAndOriginalOwnerLeasePreventStaleProgressOverwrite() =
        runTest {
            val journal = journal()
            val session = gate.current
            storage.observe(codec.record(prepared.ticket, now))
            journal.claimForLaunch(session, header.ticketId, initial).getOrThrow()
            assertFalse(journal.updateCheckpoint(session, header.ticketId, "other-attempt", 0, "1").getOrThrow())
            assertTrue(journal.updateCheckpoint(session, header.ticketId, header.attemptId, 0, "1").getOrThrow())
            assertFalse(journal.updateCheckpoint(session, header.ticketId, header.attemptId, 0, "0").getOrThrow())
            assertTrue(journal.updateCheckpoint(session, header.ticketId, header.attemptId, 1, "0").isFailure)
            gate.switch("other")
            gate.switch("player")
            assertTrue(journal.checkpoint(session, header.ticketId).isFailure)
            assertTrue(journal.updateCheckpoint(session, header.ticketId, header.attemptId, 1, "1").isFailure)
            val restored = journal().checkpoint(gate.current, header.ticketId).getOrThrow()
            assertEquals("1", restored.json)
            assertEquals(1L, restored.revision)
            assertEquals(header.attemptId, restored.snapshot.header.attemptId)
        }

    @Test
    fun responseWaitingForSharedCommitGateCannotBeatCompletedAccountSwitch() =
        runTest {
            val journal = journal()
            val session = gate.current
            journal.recordPrepareConsent(session, request).getOrThrow()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            remote.prepareAction = {
                entered.complete(Unit)
                release.await()
                Result.success(prepared)
            }
            val delivery = async { journal.dispatchPrepare(session, request.requestId) }
            entered.await()
            gate.switch("other")
            release.complete(Unit)
            assertTrue(delivery.await().isFailure)
            assertTrue(storage.tickets.isEmpty())
            assertNotNull(storage.command("player", "PREPARE", request.requestId))
        }

    @Test
    fun abortNeverBecomesUnscoredSubmitAndTerminalRecoveryNeverLaunches() =
        runTest {
            val journal = journal()
            val session = gate.current
            storage.observe(codec.record(prepared.ticket, now))
            journal.claimForLaunch(session, header.ticketId, initial).getOrThrow()
            assertTrue(
                journal.freezeCompletion(
                    session,
                    header.ticketId,
                    intent().copy(completionKind = AttemptCompletionKind.ABORTED),
                ).isFailure,
            )
            assertTrue(storage.outbox.isEmpty())
            journal.requestCancel(session, header.ticketId, "abort").getOrThrow()
            assertTrue(storage.outbox.isEmpty())
        }

    private fun sha(value: String): String =
        MessageDigest.getInstance(
            "SHA-256",
        ).digest(value.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    @Test
    fun realStrictCodecAcceptsFreshClaimProgressAndOneFrozenFinalBody() =
        runTest {
            for (hard in listOf(false, true)) {
                val f = RunReadFixture(this, hard)
                val journal = strictWriteJournal(f)
                assertTrue(strictWrite(f, journal, "CLAIM").getOrThrow())
                assertEquals(0L, f.row.checkpointRevision)
                assertTrue(strictWrite(f, journal, "CAS").getOrThrow())
                assertEquals(1L, f.row.checkpointRevision)
                val final = f.checkpoint(ArenaRunnerPhase.COMPLETING)
                val json = f.checkpoints.encode(final, f.binding)
                journal.updateCheckpoint(f.original, f.header.ticketId, f.header.attemptId, 1, json).getOrThrow()
                assertTrue(strictWrite(f, journal, "FREEZE").getOrThrow())
                assertEquals("SETTLE", f.row.decision)
                val body = f.storage.commands.values.single().bodyJson
                assertEquals(body, f.storage.outbox.values.single().payload)
                assertEquals(body, f.storage.firstIntents.values.single().payload)
                // `codeAnswerIndex` is a documented field of every served row (the strict codec requires the
            // key set {questionId, codeAnswerIndex}), so a bare "codeAnswer" substring check can never
            // hold. The intent — no synthetic percent score and no synthetic code answer — is kept, and
            // expressed against the key rather than a substring of a legitimate one.
            assertFalse("percentScore" in body)
            assertFalse("\"codeAnswer\":" in body)
            }
        }

    @Test
    fun realStrictClaimProofRefusesPostReadImmutableDriftBeforeLaunch() =
        runTest { assertStrictImmutableWriteFence("CLAIM") }

    @Test
    fun realStrictProgressProofRefusesPostReadImmutableDriftBeforeRevisionChange() =
        runTest { assertStrictImmutableWriteFence("CAS") }

    @Test
    fun realStrictFinalProofRefusesPostReadImmutableDriftBeforeAnyIntentInsert() =
        runTest { assertStrictImmutableWriteFence("FREEZE") }

    @Test
    fun canonicalTermsUnknownDuplicateAndReorderedBytesCannotEnterAnyNewWriteProof() =
        runTest {
            for (operation in listOf("CLAIM", "CAS", "FREEZE")) {
                for (kind in listOf("unknown", "duplicate", "reordered")) {
                    val f = RunReadFixture(this)
                    prepareStrictWrite(f, operation)
                    val original = f.row.termsJson
                    val fields = Json.parseToJsonElement(original).jsonObject
                    val changed =
                        when (kind) {
                            "unknown" -> JsonObject(fields + ("futureTerms" to JsonPrimitive("private"))).toString()
                            "duplicate" -> original.dropLast(1) + ",\"schemaVersion\":1}"
                            else -> JsonObject(fields.entries.reversed().associate { it.key to it.value }).toString()
                        }
                    assertTrue(changed != original)
                    f.row = f.row.copy(termsJson = changed)
                    val before = f.financialState()
                    assertTrue(strictWrite(f, strictWriteJournal(f), operation).isFailure)
                    assertEquals(before, f.financialState())
                }
            }
        }

    @Test
    fun newWriteProofRejectsInvalidCapturedFlagsWithoutRepairingThem() =
        runTest {
            val changes: List<(ArenaTicketRecordEntity) -> ArenaTicketRecordEntity> =
                listOf(
                    { it.copy(terminalJson = "opaque terminal") },
                    { it.copy(observedAtMs = -1) },
                    { it.copy(checkpointRevision = -1) },
                    { it.copy(localUse = "UNKNOWN") },
                )
            for (operation in listOf("CLAIM", "CAS", "FREEZE")) {
                for (change in changes) {
                    val f = RunReadFixture(this)
                    prepareStrictWrite(f, operation)
                    f.row = change(f.row)
                    val before = f.financialState()
                    val result = strictWrite(f, strictWriteJournal(f), operation)
                    assertTrue(result.isFailure || result.getOrNull() == false)
                    assertEquals(before, f.financialState())
                }
            }
            val f = RunReadFixture(this)
            f.row = f.row.copy(checkpointRevision = 7)
            val before = f.financialState()
            assertTrue(strictWrite(f, strictWriteJournal(f), "CLAIM").isFailure)
            assertEquals(before, f.financialState())
        }

    @Test
    fun actualWriteRereadRefusesTerminalOrDecisionRacesWithoutUsingCapturedActiveState() =
        runTest {
            val changes: List<(ArenaTicketRecordEntity) -> ArenaTicketRecordEntity> =
                listOf(
                    { it.copy(serverStatus = "CANCELLED") },
                    { it.copy(decision = "CANCEL") },
                    { it.copy(terminalJson = "opaque terminal") },
                    { it.copy(localUse = "UNKNOWN") },
                    { it.copy(observedAtMs = -1) },
                )
            for (operation in listOf("CLAIM", "CAS", "FREEZE")) {
                for (change in changes) {
                    val f = RunReadFixture(this)
                    prepareStrictWrite(f, operation)
                    val changed = change(f.row)
                    var before: List<Any>? = null
                    val journal =
                        strictWriteJournal(f) {
                            f.row = changed
                            before = f.financialState()
                        }
                    val result = strictWrite(f, journal, operation)
                    assertTrue(result.isFailure || result.getOrNull() == false)
                    assertEquals(before, f.financialState())
                }
            }
        }

    @Test
    fun strictFrozenReplayAndExplicitCancellationStillDoNotDecodeAnUnsafeBank() =
        runTest {
            val f = RunReadFixture(this)
            prepareStrictWrite(f, "FREEZE")
            val journal = strictWriteJournal(f)
            val finalIntent = arenaCheckpointCompletion(f.checkpoint(ArenaRunnerPhase.COMPLETING), f.binding)
            journal.freezeCompletion(f.original, f.header.ticketId, finalIntent).getOrThrow()
            f.row = f.row.copy(serverStatus = "SETTLED", snapshotJson = null, checkpointJson = null)
            val before = f.financialState()
            journal.freezeCompletion(f.original, f.header.ticketId, finalIntent).getOrThrow()
            assertEquals(before, f.financialState())

            val cancellable = RunReadFixture(this)
            cancellable.row = cancellable.row.copy(snapshotJson = "unsafe bank", snapshotSha256 = "bad hash")
            strictWriteJournal(cancellable).requestCancel(
                cancellable.original,
                cancellable.header.ticketId,
                "explicit-cancel",
            ).getOrThrow()
            assertEquals("CANCEL", cancellable.row.decision)
            assertTrue(cancellable.storage.outbox.isEmpty())
            assertTrue(cancellable.storage.firstIntents.isEmpty())
        }

    private suspend fun TestScope.assertStrictImmutableWriteFence(operation: String) {
        val changes: List<(ArenaTicketRecordEntity) -> ArenaTicketRecordEntity> =
            listOf(
                { it.copy(requestId = "changed-request") },
                { it.copy(termsJson = it.termsJson.dropLast(1) + ",\"schemaVersion\":1}") },
                { it.copy(snapshotJson = requireNotNull(it.snapshotJson) + " ") },
                { it.copy(snapshotSha256 = "changed-hash") },
            )
        for (change in changes) {
            val f = RunReadFixture(this)
            prepareStrictWrite(f, operation)
            val changed = change(f.row)
            var before: List<Any>? = null
            val journal =
                strictWriteJournal(f) {
                    f.row = changed
                    before = f.financialState()
                }
            assertTrue(strictWrite(f, journal, operation).exceptionOrNull() is IllegalArgumentException)
            assertEquals(before, f.financialState())
            assertTrue(f.remote.boundSessions.isEmpty())
        }
    }

    // TZ-01 stage B agreed checks, strict-codec side. The current error channel is deliberately left as
    // it is; these tests PIN it so the future ArenaRunRepository write adapter has a described contract.
    //
    // For the write adapter: a refusal arrives on two different channels.
    //  - claimForLaunch  -> Result.success(ArenaLaunchClaim.NotFresh) when state() already sees the row as
    //                       unavailable, but Result.failure(IllegalArgumentException) when the row passes
    //                       state() and fails the new-write row guard.
    //  - updateCheckpoint -> Result.success(false) only for a genuine lost CAS; a row that is not writable
    //                       at all now yields Result.failure(IllegalArgumentException).
    // "Lost CAS" and "row not writable" are therefore NOT distinguishable by the Boolean alone.

    @Test
    fun claimForLaunchReportsNotFreshForAnAlreadyUnavailableRowAndFailsForAnUnwritableOne() =
        runTest {
            val unavailable = RunReadFixture(this)
            unavailable.row = unavailable.row.copy(serverStatus = "SETTLED", terminalJson = "receipt")
            val notFresh = strictWrite(unavailable, strictWriteJournal(unavailable), "CLAIM")
            assertTrue(notFresh.isSuccess)
            assertEquals(false, notFresh.getOrNull())

            val unwritable = RunReadFixture(this)
            // Still RESERVED / NONE / UNUSED with a decodable bank, so state() calls it Available, but a
            // fresh claim refuses it: an UNUSED row carrying a checkpoint at revision 7 is corrupted, and
            // the contract says it must not be zeroed back into a new run.
            unwritable.row = unwritable.row.copy(checkpointJson = "{}", checkpointRevision = 7)
            val before = unwritable.financialState()
            val failure = strictWrite(unwritable, strictWriteJournal(unwritable), "CLAIM")
            assertTrue(failure.isFailure)
            assertIs<IllegalArgumentException>(failure.exceptionOrNull())
            assertEquals(before, unwritable.financialState())
        }

    @Test
    fun updateCheckpointFailsRatherThanReportingALostCasWhenTheRowIsNotWritable() =
        runTest {
            val f = RunReadFixture(this)
            f.launch()
            f.row = f.row.copy(decision = "CANCEL")
            val before = f.financialState()
            val outcome = strictWrite(f, strictWriteJournal(f), "CAS")
            assertTrue(outcome.isFailure)
            assertIs<IllegalArgumentException>(outcome.exceptionOrNull())
            assertEquals(before, f.financialState())
        }

    @Test
    fun strictCodecAcceptsAFreezeAfterAnIdentityCasThatOnlyAdvancedTheRevision() =
        runTest {
            val f = RunReadFixture(this)
            val journal = strictWriteJournal(f)
            f.launch(ArenaRunnerPhase.COMPLETING)
            val durable = requireNotNull(f.row.checkpointJson)
            val revisionBefore = f.row.checkpointRevision
            // The final checkpoint is immutable, so the only legal CAS on it is the identity one.
            assertTrue(
                journal.updateCheckpoint(
                    f.original,
                    f.header.ticketId,
                    f.header.attemptId,
                    revisionBefore,
                    durable,
                ).getOrThrow(),
            )
            assertEquals(revisionBefore + 1, f.row.checkpointRevision)
            assertEquals(durable, f.row.checkpointJson)
            assertTrue(strictWrite(f, journal, "FREEZE").getOrThrow())
            assertEquals("SETTLE", f.row.decision)
            val body = f.storage.commands.values.single().bodyJson
            assertEquals(body, f.storage.outbox.values.single().payload)
            assertEquals(body, f.storage.firstIntents.values.single().payload)
        }

    @Test
    fun strictCodecRefusesAFinalBodyBuiltForADifferentCheckpointAndWritesNothing() =
        runTest {
            val f = RunReadFixture(this)
            val journal = strictWriteJournal(f)
            f.launch(ArenaRunnerPhase.COMPLETING)
            val before = f.financialState()
            // A completion intent for a checkpoint the row does not hold: the strict codec compares the
            // body against the checkpoint actually read inside the write, so it cannot be committed.
            val foreign =
                arenaCheckpointCompletion(
                    f.checkpoint(ArenaRunnerPhase.COMPLETING).copy(endedAtMs = f.header.issuedAtMs + 9_999),
                    f.binding,
                )
            val outcome = journal.freezeCompletion(f.original, f.header.ticketId, foreign)
            assertTrue(outcome.isFailure)
            assertEquals(before, f.financialState())
            assertTrue(f.storage.commands.isEmpty())
            assertTrue(f.storage.outbox.isEmpty())
            assertTrue(f.storage.firstIntents.isEmpty())
        }

    @Test
    fun theCanonicalFakeRefusesTheSameCorruptedRowsAsTheRealDaoWithTheSameOutcome() =
        runTest {
            // Behavioural parity, not a source read: the same enumerated corruptions the real Room test
            // applies to the DAO are applied here to the canonical fake, and must produce false, not a
            // silent success. If either side drifts, one of the two suites goes red.
            val corruptions: List<Pair<String, (ArenaTicketRecordEntity) -> ArenaTicketRecordEntity>> =
                listOf(
                    "terminal" to { row: ArenaTicketRecordEntity -> row.copy(terminalJson = "receipt") },
                    "cancelled" to { row: ArenaTicketRecordEntity -> row.copy(decision = "CANCEL") },
                    "serverSettled" to { row: ArenaTicketRecordEntity -> row.copy(serverStatus = "SETTLED") },
                    "unknownLocalUse" to { row: ArenaTicketRecordEntity -> row.copy(localUse = "UNKNOWN") },
                    "negativeClock" to { row: ArenaTicketRecordEntity -> row.copy(observedAtMs = -1) },
                    "negativeRevision" to { row: ArenaTicketRecordEntity -> row.copy(checkpointRevision = -1) },
                )
            for ((name, change) in corruptions) {
                val claimFixture = RunReadFixture(this)
                claimFixture.row = change(claimFixture.row)
                var claimProofSeen = false
                assertFalse(
                    claimFixture.storage.claim(
                        claimFixture.header.ownerUid,
                        claimFixture.header.ticketId,
                        // The guard runs before the proof, so any non-empty candidate is enough here.
                        "{}",
                    ) { _, _, _, _ -> claimProofSeen = true },
                    name,
                )
                assertFalse(claimProofSeen, name)

                val casFixture = RunReadFixture(this)
                casFixture.launch()
                val durable = requireNotNull(casFixture.row.checkpointJson)
                val revision = casFixture.row.checkpointRevision
                casFixture.row = change(casFixture.row)
                var casProofSeen = false
                assertFalse(
                    casFixture.storage.saveCheckpoint(
                        casFixture.header.ownerUid,
                        casFixture.header.ticketId,
                        casFixture.header.attemptId,
                        revision,
                        durable,
                    ) { _, _, _, _ -> casProofSeen = true },
                    name,
                )
                assertFalse(casProofSeen, name)
            }
        }

    private fun prepareStrictWrite(
        f: RunReadFixture,
        operation: String,
    ) {
        when (operation) {
            "CAS" -> f.launch()
            "FREEZE" -> f.launch(ArenaRunnerPhase.COMPLETING)
        }
    }

    private suspend fun strictWrite(
        f: RunReadFixture,
        journal: ArenaTicketJournal,
        operation: String,
    ): Result<Boolean> =
        when (operation) {
            "CLAIM" -> {
                val initial = f.checkpoint(ArenaRunnerPhase.ANSWERING)
                journal.claimForLaunch(
                    f.original,
                    f.header.ticketId,
                    ArenaInitialCheckpoint(
                        initial.sessionMode,
                        initial.seed,
                        initial.questionStartedAtMs,
                        initial.deadlineMs,
                    ),
                ).map { it is ArenaLaunchClaim.Claimed }
            }
            "CAS" ->
                journal.updateCheckpoint(
                    f.original,
                    f.header.ticketId,
                    f.header.attemptId,
                    f.row.checkpointRevision,
                    requireNotNull(f.row.checkpointJson),
                )
            else ->
                journal.freezeCompletion(
                    f.original,
                    f.header.ticketId,
                    arenaCheckpointCompletion(f.checkpoint(ArenaRunnerPhase.COMPLETING), f.binding),
                ).map { true }
        }

    /** Only the pre-transaction read is intercepted; all writes use the shared canonical storage fake. */
    private fun strictWriteJournal(
        f: RunReadFixture,
        afterRead: () -> Unit = {},
    ): ArenaTicketJournal {
        val storage =
            object : ArenaJournalStorage by f.storage {
                override suspend fun ticket(
                    owner: String,
                    ticket: String,
                ): ArenaTicketRecordEntity? {
                    val captured = f.storage.ticket(owner, ticket)
                    afterRead()
                    return captured
                }
            }
        return ArenaTicketJournal(
            storage,
            f.remote,
            f.gate,
            object : NetworkMonitor {
                override fun observeOnline() = flowOf(false)

                override suspend fun isOnline(): Nothing = error("No transport during local checkpoint write")
            },
            f.journalCodec,
            f.checkpoints,
            { f.header.issuedAtMs + 1_000 },
            f.dispatcher,
        )
    }
}
