package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_11_12
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseV12MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate11to12_reviewAssignmentsUseOwnerScopedPrimaryKey() {
        helper.createDatabase(DB_NAME, version = 11).use { db ->
            db.execSQL(insertAssignmentSql(ownerUid = "reviewer-a", title = "Assignment A"))
        }

        helper.runMigrationsAndValidate(DB_NAME, 12, true, MIGRATION_11_12).use { db ->
            db.query("SELECT title FROM review_assignments WHERE ownerUid = 'reviewer-a' AND id = 'assignment-1'")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Assignment A", cursor.getString(0))
                }

            db.execSQL(insertAssignmentSql(ownerUid = "reviewer-b", title = "Assignment B"))
            db.query("SELECT COUNT(*) FROM review_assignments WHERE id = 'assignment-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }

            var duplicateForSameOwnerFailed = false
            try {
                db.execSQL(insertAssignmentSql(ownerUid = "reviewer-b", title = "Duplicate B"))
            } catch (_: Exception) {
                duplicateForSameOwnerFailed = true
            }
            assertTrue("duplicate (ownerUid, id) should fail", duplicateForSameOwnerFailed)
        }
    }

    private fun insertAssignmentSql(
        ownerUid: String,
        title: String,
    ): String =
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
            reviewLanguages,
            isTested,
            testingScore,
            isLogicReviewed,
            logicScore,
            isTranslationReviewed,
            translationScore,
            translatedLanguages
        ) VALUES (
            'assignment-1',
            '$ownerUid',
            'submission-1',
            'catalog-1',
            'draft-1',
            'quest-1',
            'lesson-1',
            '$title',
            1,
            'TESTING',
            'ru',
            '',
            '',
            0,
            NULL,
            0,
            NULL,
            0,
            NULL,
            'ru=125'
        )
        """.trimIndent()

    private companion object {
        const val DB_NAME = "test_v12_review_assignment_owner_scope"
    }
}
