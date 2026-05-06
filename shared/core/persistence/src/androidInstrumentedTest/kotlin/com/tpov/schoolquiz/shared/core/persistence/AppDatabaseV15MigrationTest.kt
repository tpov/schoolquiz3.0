package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_14_15
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseV15MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate14to15_createsLessonResultOutboxTables() {
        helper.createDatabase(DB_NAME, version = 14).close()

        helper.runMigrationsAndValidate(DB_NAME, 15, true, MIGRATION_14_15).use { db ->
            db.execSQL(
                """
                INSERT INTO lesson_result_attempt_outbox (
                    attempt_id,
                    user_id,
                    scope,
                    owner_uid,
                    catalog_id,
                    quest_id,
                    section_id,
                    theme_id,
                    lesson_id,
                    lesson_version,
                    source_shelf,
                    difficulty,
                    code_answer,
                    percent_score,
                    completed_at_ms,
                    created_at_ms
                ) VALUES (
                    'attempt-1',
                    'uid-1',
                    'public',
                    NULL,
                    'catalog-1',
                    'quest-1',
                    'section-1',
                    'theme-1',
                    'lesson-1',
                    1,
                    'arena',
                    'EASY',
                    '9',
                    100,
                    1000,
                    1001
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO quest_rating_outbox (
                    rating_id,
                    user_id,
                    scope,
                    owner_uid,
                    catalog_id,
                    quest_id,
                    section_id,
                    theme_id,
                    lesson_id,
                    lesson_version,
                    source_shelf,
                    rating,
                    rated_at_ms,
                    created_at_ms
                ) VALUES (
                    'rating-1',
                    'uid-1',
                    'public',
                    NULL,
                    'catalog-1',
                    'quest-1',
                    'section-1',
                    'theme-1',
                    'lesson-1',
                    1,
                    'arena',
                    3,
                    1000,
                    1001
                )
                """.trimIndent(),
            )

            db.query("SELECT COUNT(*) FROM lesson_result_attempt_outbox").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT rating FROM quest_rating_outbox WHERE rating_id = 'rating-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val DB_NAME = "test_v15_lesson_result_outbox"
    }
}
