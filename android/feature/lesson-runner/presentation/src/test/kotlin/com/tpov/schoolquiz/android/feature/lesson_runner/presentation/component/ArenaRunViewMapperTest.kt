@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRead
import com.tpov.schoolquiz.shared.feature.lesson_runner.domain.repository.ArenaRunRecoveryReason
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArenaRunViewMapperTest {
    @Test
    fun busyPreparedLocalAbsentAndRecoveryFramesDisableReload() =
        runTest {
            val fixture = RunCoordinatorFixture(this)
            val reads =
                listOf(
                    ArenaRunRead.Prepared(fixture.header),
                    ArenaRunRead.LocalAbsent,
                    ArenaRunRead.NeedsRecovery(fixture.header, ArenaRunRecoveryReason.INVALID_RECORD),
                )

            reads.forEach { read ->
                val view =
                    arenaRunView(
                        state = ArenaRunCoordinatorState(observed = read, busy = true),
                        stamp = fixture.stamp,
                        foreground = true,
                        lives = null,
                        showExit = false,
                    )
                val attention = assertIs<ArenaRunnerView.Attention>(view)
                assertTrue(attention.busy)
                assertFalse(attention.canReload)
            }
        }
}
