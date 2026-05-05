package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_9_10
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseV10MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate9to10_addsReviewAssignments() {
        helper.createDatabase(DB_NAME, version = 9).close()

        helper.runMigrationsAndValidate(DB_NAME, 10, true, MIGRATION_9_10).use { db ->
            db.execSQL(
                """
                INSERT INTO review_assignments (
                    id,
                    ownerUid,
                    submissionId,
                    catalogId,
                    draftId,
                    questId,
                    lessonId,
                    title,
                    createdAtMs,
                    taskKinds,
                    sourceLanguages,
                    newTranslationLanguages,
                    reviewLanguages
                ) VALUES (
                    'task-1',
                    'reviewer-1',
                    'submission-1',
                    'catalog-1',
                    'draft-1',
                    'quest-1',
                    'lesson-1',
                    'Lesson',
                    10,
                    'TESTING',
                    '',
                    '',
                    ''
                )
                """.trimIndent(),
            )
            val cursor = db.query("SELECT taskKinds FROM review_assignments WHERE id = 'task-1'")
            assert(cursor.moveToFirst()) { "review assignment row should be readable after migration" }
            assert(cursor.getString(0) == "TESTING") { "Expected TESTING, got ${cursor.getString(0)}" }
            cursor.close()
        }
    }

    private companion object {
        const val DB_NAME = "test_v10_review_assignments"
    }
}
