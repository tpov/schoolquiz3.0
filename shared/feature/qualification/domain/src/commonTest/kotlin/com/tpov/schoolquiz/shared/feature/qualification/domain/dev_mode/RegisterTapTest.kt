package com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode

import com.tpov.schoolquiz.shared.core.foundation.QualificationLevel
import com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.logic.registerTap
import com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.model.TapProgress
import com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.model.TapResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Domain Test Scenarios DM-01..DM-10 for [registerTap] (RegisterTap FSM).
 *
 * Spec: docs/features/menu-refactor/0-spec-dev-mode.md § Domain Test Scenarios (registerTap scenarios)
 * Spec: docs/features/menu-refactor/0-spec-dev-mode.md § State Matrix
 *
 * Walking Skeleton preserved — only param rename applied (old name removed).
 * All tests use Long epoch-millis for time (t0 = 0L baseline).
 */
class RegisterTapTest {

    private val t0 = 0L

    // ── DM-01 / Scenario 1 ────────────────────────────────────────────────────
    // GIVEN TapProgress(0, null) WHEN registerTap(now=t0, currentDeveloperLevel=0)
    // THEN returns NoChange(TapProgress(1, t0))
    @Test
    fun `scenario 1 first tap on initial state returns NoChange with count=1`() {
        val result = registerTap(
            progress = TapProgress(count = 0, lastTapAtMillis = null),
            nowMillis = t0,
            currentDeveloperLevel = 0,
        )

        assertIs<TapResult.NoChange>(result)
        assertEquals(TapProgress(count = 1, lastTapAtMillis = t0), result.newProgress)
    }

    // ── DM-07 / Scenario 2 ────────────────────────────────────────────────────
    // GIVEN TapProgress(9, t0) WHEN registerTap(now=t0+100ms, currentDeveloperLevel=0)
    // THEN returns Activated(TapProgress(0, null))
    @Test
    fun `scenario 2 tenth tap within threshold and level 0 returns Activated with reset progress`() {
        val result = registerTap(
            progress = TapProgress(count = 9, lastTapAtMillis = t0),
            nowMillis = t0 + 100L,
            currentDeveloperLevel = 0,
        )

        assertIs<TapResult.Activated>(result)
        assertEquals(TapProgress(count = 0, lastTapAtMillis = null), result.newProgress)
    }

    // ── DM-08 / Scenario 3 ────────────────────────────────────────────────────
    // GIVEN TapProgress(9, t0) WHEN registerTap(now=t0+100ms, currentDeveloperLevel=100)
    // THEN returns Activated(TapProgress(0, null)) so local level 100 can upgrade to >100.
    @Test
    fun `scenario 3 tenth tap within threshold and level=100 returns Activated with reset progress`() {
        val result = registerTap(
            progress = TapProgress(count = 9, lastTapAtMillis = t0),
            nowMillis = t0 + 100L,
            currentDeveloperLevel = 100,
        )

        assertIs<TapResult.Activated>(result)
        assertEquals(TapProgress(count = 0, lastTapAtMillis = null), result.newProgress)
    }

    // ── DM-08 boundary / Scenario 4 ───────────────────────────────────────────
    // GIVEN TapProgress(9, t0) WHEN registerTap(now=t0+100ms, currentDeveloperLevel=200)
    // THEN returns AlreadyDev(TapProgress(0, null))
    @Test
    fun `scenario 4 tenth tap within threshold and level=200 returns AlreadyDev`() {
        val result = registerTap(
            progress = TapProgress(count = 9, lastTapAtMillis = t0),
            nowMillis = t0 + 100L,
            currentDeveloperLevel = 200,
        )

        assertIs<TapResult.AlreadyDev>(result)
        assertEquals(TapProgress(count = 0, lastTapAtMillis = null), result.newProgress)
    }

    // ── DM-03 / DM-05 / Scenario 5 ───────────────────────────────────────────
    // GIVEN TapProgress(5, t0) WHEN registerTap(now=t0+501ms, currentDeveloperLevel=0)
    // THEN returns Reset(TapProgress(1, t0+501ms))
    @Test
    fun `scenario 5 tap after 501ms pause returns Reset with count=1`() {
        val result = registerTap(
            progress = TapProgress(count = 5, lastTapAtMillis = t0),
            nowMillis = t0 + 501L,
            currentDeveloperLevel = 0,
        )

        assertIs<TapResult.Reset>(result)
        assertEquals(TapProgress(count = 1, lastTapAtMillis = t0 + 501L), result.newProgress)
    }

    // ── DM-04 / Scenario 6 ────────────────────────────────────────────────────
    // GIVEN TapProgress(5, t0) WHEN registerTap(now=t0+500ms, currentDeveloperLevel=0)
    // (equal boundary — NOT a reset: elapsed=500 is NOT > 500)
    // THEN returns NoChange(TapProgress(6, t0+500ms))
    @Test
    fun `scenario 6 tap at exactly 500ms boundary is NOT a reset returns NoChange with count=6`() {
        val result = registerTap(
            progress = TapProgress(count = 5, lastTapAtMillis = t0),
            nowMillis = t0 + 500L,
            currentDeveloperLevel = 0,
        )

        assertIs<TapResult.NoChange>(result)
        assertEquals(TapProgress(count = 6, lastTapAtMillis = t0 + 500L), result.newProgress)
    }

    // ── DM-02 / Scenario 7 ────────────────────────────────────────────────────
    // GIVEN TapProgress(5, t0) WHEN registerTap(now=t0+499ms, currentDeveloperLevel=0)
    // THEN returns NoChange(TapProgress(6, t0+499ms))
    @Test
    fun `scenario 7 tap at 499ms is within threshold returns NoChange with count=6`() {
        val result = registerTap(
            progress = TapProgress(count = 5, lastTapAtMillis = t0),
            nowMillis = t0 + 499L,
            currentDeveloperLevel = 0,
        )

        assertIs<TapResult.NoChange>(result)
        assertEquals(TapProgress(count = 6, lastTapAtMillis = t0 + 499L), result.newProgress)
    }

    // ── Full 10-tap sequence / Scenario 8 ─────────────────────────────────────
    // GIVEN 10-tap sequence all intervals < 500ms AND currentDeveloperLevel=0
    // WHEN last tap applied THEN returns Activated(TapProgress(0, null))
    @Test
    fun `scenario 8 full 10-tap sequence with all intervals under 500ms returns Activated on last tap`() {
        var progress = TapProgress.initial
        val interval = 100L
        var time = t0
        var result: TapResult = TapResult.NoChange(progress)

        repeat(10) {
            time += interval
            result = registerTap(
                progress = progress,
                nowMillis = time,
                currentDeveloperLevel = 0,
            )
            progress = result.newProgress
        }

        assertIs<TapResult.Activated>(result)
        assertEquals(TapProgress(count = 0, lastTapAtMillis = null), result.newProgress)
    }

    // ── DM-09 / Scenario 9 ────────────────────────────────────────────────────
    // GIVEN post-Activated state TapProgress(0, null)
    // WHEN registerTap(now, currentDeveloperLevel=101) (now a developer)
    // THEN returns NoChange(TapProgress(1, now))
    @Test
    fun `scenario 9 tap after activation with developer level=101 restarts sequence as NoChange`() {
        val nowMillis = t0 + 5000L
        val result = registerTap(
            progress = TapProgress(count = 0, lastTapAtMillis = null),
            nowMillis = nowMillis,
            currentDeveloperLevel = 101,
        )

        assertIs<TapResult.NoChange>(result)
        assertEquals(TapProgress(count = 1, lastTapAtMillis = nowMillis), result.newProgress)
    }

    // ── DM-10 / Scenario 10 ───────────────────────────────────────────────────
    // GIVEN 10 tap sequence with one interval > 500ms AND currentDeveloperLevel=0
    // WHEN series processed THEN Activated NOT reached; there is a Reset in the middle
    @Test
    fun `scenario 10 sequence with one timeout interval produces Reset and Activated is never reached`() {
        var progress = TapProgress.initial
        var time = t0
        var activatedReached = false
        var resetSeen = false

        // Taps 1-5: fast (100ms)
        repeat(5) {
            time += 100L
            val result = registerTap(progress, time, 0)
            progress = result.newProgress
        }
        // Tap 6: slow (600ms > 500ms threshold) → should Reset
        time += 600L
        val resetResult = registerTap(progress, time, 0)
        if (resetResult is TapResult.Reset) resetSeen = true
        progress = resetResult.newProgress

        // 4 more taps after reset — not enough to reach 10 again
        repeat(4) {
            time += 100L
            val result = registerTap(progress, time, 0)
            if (result is TapResult.Activated) activatedReached = true
            progress = result.newProgress
        }

        assertEquals(false, activatedReached)
        assertEquals(true, resetSeen)
    }

    // ── State Matrix rows (additional coverage) ────────────────────────────────

    @Test
    fun `state matrix count=0 first tap produces NoChange count=1`() {
        val result = registerTap(
            progress = TapProgress(count = 0, lastTapAtMillis = null),
            nowMillis = 1000L,
            currentDeveloperLevel = 0,
        )
        assertIs<TapResult.NoChange>(result)
        assertEquals(1, result.newProgress.count)
    }

    @Test
    fun `state matrix count=1-8 within threshold produces NoChange count+1`() {
        for (count in 1..8) {
            val lastTap = 0L
            val result = registerTap(
                progress = TapProgress(count = count, lastTapAtMillis = lastTap),
                nowMillis = lastTap + 400L,
                currentDeveloperLevel = 0,
            )
            assertIs<TapResult.NoChange>(result)
            assertEquals(count + 1, result.newProgress.count)
        }
    }

    @Test
    fun `state matrix count=1-8 over threshold produces Reset count=1`() {
        for (count in 1..8) {
            val lastTap = 0L
            val result = registerTap(
                progress = TapProgress(count = count, lastTapAtMillis = lastTap),
                nowMillis = lastTap + 600L,
                currentDeveloperLevel = 0,
            )
            assertIs<TapResult.Reset>(result)
            assertEquals(1, result.newProgress.count)
        }
    }

    // ── DM-09 boundary ────────────────────────────────────────────────────────
    @Test
    fun `state matrix count=9 within threshold level below LEVEL_1 produces Activated`() {
        val result = registerTap(
            progress = TapProgress(count = 9, lastTapAtMillis = 0L),
            nowMillis = 100L,
            currentDeveloperLevel = QualificationLevel.LEVEL_1.points - 1, // 99
        )
        assertIs<TapResult.Activated>(result)
        assertNull(result.newProgress.lastTapAtMillis)
        assertEquals(0, result.newProgress.count)
    }

    // ── DM-08 boundary ────────────────────────────────────────────────────────
    @Test
    fun `state matrix count=9 within threshold level at LEVEL_1 produces Activated`() {
        val result = registerTap(
            progress = TapProgress(count = 9, lastTapAtMillis = 0L),
            nowMillis = 100L,
            currentDeveloperLevel = QualificationLevel.LEVEL_1.points, // 100
        )
        assertIs<TapResult.Activated>(result)
        assertEquals(0, result.newProgress.count)
    }

    @Test
    fun `state matrix count=9 within threshold level above LEVEL_1 produces AlreadyDev`() {
        val result = registerTap(
            progress = TapProgress(count = 9, lastTapAtMillis = 0L),
            nowMillis = 100L,
            currentDeveloperLevel = QualificationLevel.LEVEL_1.points + 1, // 101
        )
        assertIs<TapResult.AlreadyDev>(result)
        assertEquals(0, result.newProgress.count)
    }

    // ── DM-10 with varied levels ───────────────────────────────────────────────
    @Test
    fun `state matrix count=9 over threshold produces Reset regardless of level`() {
        for (level in listOf(0, 50, 100, 200)) {
            val result = registerTap(
                progress = TapProgress(count = 9, lastTapAtMillis = 0L),
                nowMillis = 600L,
                currentDeveloperLevel = level,
            )
            assertIs<TapResult.Reset>(result)
            assertEquals(1, result.newProgress.count)
        }
    }
}
