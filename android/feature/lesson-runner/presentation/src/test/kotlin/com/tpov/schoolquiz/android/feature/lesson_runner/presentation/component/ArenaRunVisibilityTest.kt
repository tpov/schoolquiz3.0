package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.model.ArenaRunnerPhase
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaSessionStamp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArenaRunVisibilityTest {
    @Test
    fun foregroundAdvanceRevealsOnlyItsOwnNextPausedRevisionOnce() {
        val visibility = visible()
        visibility.advancing(token)
        val next = token.copy(index = 1, revision = 4, phase = ArenaRunnerPhase.PAUSED)
        assertEquals(ArenaRunAction.RevealPrepared, visibility.afterSaved(next))
        assertNull(visibility.afterSaved(next))
    }

    @Test
    fun backgroundDuringAdvanceNeverClosesNextUnshownQuestion() {
        val visibility = visible()
        visibility.advancing(token)
        visibility.background(false, 2000)
        assertFalse(visibility.foreground)
        val next = token.copy(index = 1, revision = 4, phase = ArenaRunnerPhase.PAUSED)
        assertNull(visibility.afterSaved(next))
        visibility.foreground()
        assertNull(visibility.afterSaved(next))
    }

    @Test
    fun backgroundDuringAnswerSaveClosesOnlyLockedCurrentSlotAtOriginalStopTime() {
        val visibility = visible()
        visibility.background(false, 2000)
        visibility.background(false, 9000)
        val locked = token.copy(revision = 4, phase = ArenaRunnerPhase.FEEDBACK)
        assertEquals(ArenaRunAction.BackgroundAt(2000), visibility.afterSaved(locked))
        assertNull(visibility.afterSaved(locked.copy(index = 1, revision = 5, phase = ArenaRunnerPhase.PAUSED)))
    }

    @Test
    fun returningBeforeCasDoesNotEraseTheBackgroundBoundaryOrAutoResume() {
        val visibility = visible()
        visibility.background(false, 2000)
        visibility.foreground()
        assertEquals(ArenaRunAction.BackgroundAt(2000), visibility.afterSaved(token.copy(revision = 4)))
        assertNull(visibility.afterSaved(token.copy(index = 1, revision = 5, phase = ArenaRunnerPhase.PAUSED)))
    }

    @Test
    fun rotationDoesNotCloseQuestionOrLoseForegroundAdvance() {
        val visibility = visible()
        visibility.advancing(token)
        visibility.background(true, 2000)
        assertTrue(visibility.foreground)
        assertEquals(
            ArenaRunAction.RevealPrepared,
            visibility.afterSaved(token.copy(index = 1, revision = 4, phase = ArenaRunnerPhase.PAUSED)),
        )
    }

    @Test
    fun restoredPauseAndFailedAdvanceNeverGainAutoRevealAuthority() {
        val paused = token.copy(index = 1, revision = 4, phase = ArenaRunnerPhase.PAUSED)
        val restored = ArenaRunVisibility().also { it.foreground() }
        assertNull(restored.afterSaved(paused))
        val failed = visible()
        failed.advancing(token)
        failed.writeFailed()
        assertNull(failed.afterSaved(paused))
    }

    @Test
    fun sameUidNewEpochAndWrongRevisionCannotBorrowForegroundAdvance() {
        val next = token.copy(index = 1, revision = 4, phase = ArenaRunnerPhase.PAUSED)
        for (other in listOf(next.copy(stamp = token.stamp.copy(generation = 2)), next.copy(revision = 5))) {
            val visibility = visible()
            visibility.advancing(token)
            assertNull(visibility.afterSaved(other))
        }
    }

    @Test
    fun startCompletingInBackgroundClosesAtMostOneCurrentSlot() {
        val visibility = ArenaRunVisibility()
        visibility.background(false, 2000)
        assertEquals(ArenaRunAction.BackgroundAt(2000), visibility.afterSaved(token))
        assertNull(visibility.afterSaved(token.copy(index = 1, phase = ArenaRunnerPhase.PAUSED)))
    }

    @Test
    fun failedBackgroundSaveKeepsStopTimeUntilExplicitReadProvesClosure() {
        val visibility = visible()
        visibility.background(false, 2000)
        assertEquals(ArenaRunAction.BackgroundAt(2000), visibility.afterSaved(token))
        visibility.writeFailed()
        visibility.foreground()
        // The host invokes afterSaved again only after the player's explicit reload has succeeded.
        assertEquals(ArenaRunAction.BackgroundAt(2000), visibility.afterSaved(token))
        val next = token.copy(index = 1, revision = 4, phase = ArenaRunnerPhase.PAUSED)
        assertNull(visibility.afterSaved(next))
        assertNull(visibility.afterSaved(next.copy(phase = ArenaRunnerPhase.ANSWERING, revision = 5)))
    }

    @Test
    fun firstBackgroundReadBindsUnknownInitialBoundaryBeforeRetry() {
        val visibility = ArenaRunVisibility()
        visibility.background(false, 2000)
        assertEquals(ArenaRunAction.BackgroundAt(2000), visibility.afterSaved(token))
        visibility.writeFailed()
        // Even an unexpected later read cannot use the initially-null boundary to close another slot.
        assertNull(visibility.afterSaved(token.copy(index = 1, revision = 4)))
    }

    private val token =
        ArenaRunToken(ArenaSessionStamp("owner", 1), "ticket", "attempt", 3, 0, ArenaRunnerPhase.ANSWERING)

    private fun visible() =
        ArenaRunVisibility().also {
            it.foreground()
            it.presented(token)
        }
}
