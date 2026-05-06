package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_13_14
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseV14MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate13to14_addsArenaSubmissionTargetColumns() {
        helper.createDatabase(DB_NAME, version = 13).use { db ->
            db.execSQL(
                """
                INSERT INTO quest_arena_submission_outbox (
                    id,
                    draftId,
                    ownerUid,
                    localRevision,
                    requestedAtMs,
                    attemptCount,
                    lastError
                ) VALUES (
                    'submission-1',
                    'draft-1',
                    'owner-1',
                    1,
                    10,
                    0,
                    NULL
                )
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 14, true, MIGRATION_13_14).use { db ->
            db.query(
                """
                SELECT lessonIds, targetShelf
                FROM quest_arena_submission_outbox
                WHERE id = 'submission-1'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
                assertEquals("arena", cursor.getString(1))
            }
        }
    }

    private companion object {
        const val DB_NAME = "test_v14_arena_submission_targets"
    }
}
