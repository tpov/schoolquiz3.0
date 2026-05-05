package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_8_9
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseV9MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate8to9_addsQuestionLanguageLevelAndArenaOutbox() {
        helper.createDatabase(DB_NAME, version = 8).close()

        helper.runMigrationsAndValidate(DB_NAME, 9, true, MIGRATION_8_9).use { db ->
            val cursor = db.query("SELECT languageLevel FROM questions LIMIT 0")
            cursor.close()

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
            val outbox = db.query("SELECT ownerUid FROM quest_arena_submission_outbox WHERE id = 'submission-1'")
            assert(outbox.moveToFirst()) { "arena outbox row should be readable after migration" }
            assert(outbox.getString(0) == "owner-1") { "Expected owner-1, got ${outbox.getString(0)}" }
            outbox.close()
        }
    }

    private companion object {
        const val DB_NAME = "test_v9_arena_submission"
    }
}
