package com.tpov.schoolquiz.android.feature.lesson_runner.presentation

import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component.ArenaRunnerStatus
import com.tpov.schoolquiz.android.feature.lesson_runner.presentation.ui.SYNC_STATUSES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The sync Send button exists only for frames whose local journal still holds an open mutation
 * (retro L3: NEEDS_RECOVERY is an unreadable journal — its repair is Reload, and a Send tap
 * there was a silent no-op). This set outlives the composable that renders it, so the contract
 * is pinned where it can actually run: a compose test lives in androidTest, which ciCheck only
 * compiles.
 */
class ArenaRunnerSyncStatusesTest {
    @Test
    fun syncSendIsOfferedExactlyToFramesWithAnOpenMutation() {
        assertEquals(
            setOf(
                ArenaRunnerStatus.PENDING_COMPLETION,
                ArenaRunnerStatus.PENDING_CANCEL,
                ArenaRunnerStatus.SETTLED_AWAITING_RECEIPT,
            ),
            SYNC_STATUSES,
        )
    }

    @Test
    fun needsRecoveryNeverOffersTheSyncSend() {
        assertFalse(ArenaRunnerStatus.NEEDS_RECOVERY in SYNC_STATUSES)
        assertTrue(ArenaRunnerStatus.entries.size > SYNC_STATUSES.size)
    }
}
