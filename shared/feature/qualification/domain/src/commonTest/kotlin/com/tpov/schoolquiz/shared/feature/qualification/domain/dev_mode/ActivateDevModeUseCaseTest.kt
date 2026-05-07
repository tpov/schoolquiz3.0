package com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode

import com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.model.TapProgress
import com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.model.TapResult
import com.tpov.schoolquiz.shared.feature.qualification.domain.dev_mode.use_case.ActivateDevModeUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Domain Test Scenarios DM-11..DM-16 for [ActivateDevModeUseCase].
 *
 * Spec: docs/features/menu-refactor/0-spec-dev-mode.md § Domain Test Scenarios (ActivateDevModeUseCase)
 * REWRITTEN: removed overlayRepo; uses lambda fakes per ADR-L3-01.
 */
class ActivateDevModeUseCaseTest {

    // ── DM-11 ─────────────────────────────────────────────────────────────────
    // GIVEN 10th tap with developer=0
    // WHEN invoke(TapProgress(count=9,...), nowMillis)
    // THEN onDevModeActivated called exactly 1 time
    @Test
    fun `DM-11 given 10th tap developer=0 when invoke then onDevModeActivated called once`() = runTest {
        var onDevModeActivatedCallCount = 0
        var developerLevelToReturn = 0
        val useCase = ActivateDevModeUseCase(
            readCurrentDeveloperLevel = { developerLevelToReturn },
            onDevModeActivated = { onDevModeActivatedCallCount++ },
        )

        val result = useCase(
            progress = TapProgress(count = 9, lastTapAtMillis = 900L),
            nowMillis = 1000L,
        )

        assertIs<TapResult.Activated>(result)
        assertEquals(1, onDevModeActivatedCallCount)
    }

    // ── DM-12 ─────────────────────────────────────────────────────────────────
    // GIVEN 5th tap (count=4, NoChange path)
    // WHEN invoke
    // THEN onDevModeActivated NOT called, callCount == 0
    @Test
    fun `DM-12 given 5th tap when invoke then onDevModeActivated not called`() = runTest {
        var onDevModeActivatedCallCount = 0
        val useCase = ActivateDevModeUseCase(
            readCurrentDeveloperLevel = { 0 },
            onDevModeActivated = { onDevModeActivatedCallCount++ },
        )

        val result = useCase(
            progress = TapProgress(count = 4, lastTapAtMillis = 900L),
            nowMillis = 1000L,
        )

        assertIs<TapResult.NoChange>(result)
        assertEquals(0, onDevModeActivatedCallCount)
    }

    // ── DM-13 ─────────────────────────────────────────────────────────────────
    // GIVEN developerLevelToReturn=101 (AlreadyDev guard)
    // WHEN 10th tap
    // THEN onDevModeActivated NOT called
    @Test
    fun `DM-13 given developerLevel=101 when 10th tap then onDevModeActivated not called`() = runTest {
        var onDevModeActivatedCallCount = 0
        val useCase = ActivateDevModeUseCase(
            readCurrentDeveloperLevel = { 101 },
            onDevModeActivated = { onDevModeActivatedCallCount++ },
        )

        val result = useCase(
            progress = TapProgress(count = 9, lastTapAtMillis = 900L),
            nowMillis = 1000L,
        )

        assertIs<TapResult.AlreadyDev>(result)
        assertEquals(0, onDevModeActivatedCallCount)
    }

    // ── DM-14 ─────────────────────────────────────────────────────────────────
    // GIVEN any input
    // WHEN invoke
    // THEN return value == result from registerTap (Activated/AlreadyDev/NoChange/Reset)
    @Test
    fun `DM-14 invoke returns TapResult matching registerTap output for Activated`() = runTest {
        val useCase = ActivateDevModeUseCase(
            readCurrentDeveloperLevel = { 0 },
            onDevModeActivated = { },
        )

        val result = useCase(
            progress = TapProgress(count = 9, lastTapAtMillis = 900L),
            nowMillis = 1000L,
        )
        assertIs<TapResult.Activated>(result)
    }

    @Test
    fun `DM-14 invoke returns TapResult matching registerTap output for AlreadyDev`() = runTest {
        val useCase = ActivateDevModeUseCase(
            readCurrentDeveloperLevel = { 101 },
            onDevModeActivated = { },
        )

        val result = useCase(
            progress = TapProgress(count = 9, lastTapAtMillis = 900L),
            nowMillis = 1000L,
        )
        assertIs<TapResult.AlreadyDev>(result)
    }

    @Test
    fun `DM-14 invoke returns TapResult matching registerTap output for Reset`() = runTest {
        val useCase = ActivateDevModeUseCase(
            readCurrentDeveloperLevel = { 0 },
            onDevModeActivated = { },
        )

        val result = useCase(
            progress = TapProgress(count = 9, lastTapAtMillis = 0L),
            nowMillis = 700L,
        )
        assertIs<TapResult.Reset>(result)
    }

    // ── DM-15 ─────────────────────────────────────────────────────────────────
    // GIVEN any input
    // WHEN invoke called N times
    // THEN readCurrentDeveloperLevel called exactly N times (not cached)
    @Test
    fun `DM-15 when invoke called N times then readCurrentDeveloperLevel called N times`() = runTest {
        var readCalls = 0
        val useCase = ActivateDevModeUseCase(
            readCurrentDeveloperLevel = { readCalls++; 0 },
            onDevModeActivated = { },
        )

        val n = 3
        repeat(n) { i ->
            useCase(
                progress = TapProgress(count = i, lastTapAtMillis = if (i == 0) null else (i * 100L)),
                nowMillis = (i + 1) * 100L,
            )
        }

        assertEquals(n, readCalls)
    }

    // ── DM-16 ─────────────────────────────────────────────────────────────────
    // GIVEN onDevModeActivated is a suspend lambda (may have delay)
    // WHEN invoke
    // THEN returns TapResult without hanging — suspend lambda executes inside coroutine context
    @Test
    fun `DM-16 given onDevModeActivated with suspend when invoke then returns without hanging`() = runTest {
        var onDevModeActivatedCallCount = 0
        val useCase = ActivateDevModeUseCase(
            readCurrentDeveloperLevel = { 0 },
            onDevModeActivated = {
                onDevModeActivatedCallCount++
            },
        )

        val result = useCase(
            progress = TapProgress(count = 9, lastTapAtMillis = 900L),
            nowMillis = 1000L,
        )

        assertIs<TapResult.Activated>(result)
        assertEquals(1, onDevModeActivatedCallCount)
    }
}
