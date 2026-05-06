package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_15_16
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseV16MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate15to16_convertsLegacyDraftStatusesToSavedStatuses() {
        helper.createDatabase(DB_NAME, version = 15).use { db ->
            insertDraft(db, id = "draft-1", status = "DRAFT")
            insertDraft(db, id = "draft-2", status = "PRIVATE")
            insertDraft(db, id = "draft-3", status = "SYNCED_PRIVATE")
        }

        helper.runMigrationsAndValidate(DB_NAME, 16, true, MIGRATION_15_16).use { db ->
            assertDraftStatus(db, id = "draft-1", expected = "SAVED")
            assertDraftStatus(db, id = "draft-2", expected = "SAVED")
            assertDraftStatus(db, id = "draft-3", expected = "SYNCED")
        }
    }

    private fun insertDraft(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        id: String,
        status: String,
    ) {
        db.execSQL(
            """
            INSERT INTO quest_drafts (
                id,
                ownerUid,
                catalogId,
                title,
                description,
                defaultLanguage,
                defaultDifficulty,
                status,
                localRevision,
                serverRevision,
                publicQuestId,
                createdAtMs,
                updatedAtMs,
                isActive
            ) VALUES (
                '$id',
                'owner-1',
                'catalog-1',
                'Saved quest',
                NULL,
                'ru',
                'EASY',
                '$status',
                1,
                NULL,
                NULL,
                1,
                2,
                1
            )
            """.trimIndent(),
        )
    }

    private fun assertDraftStatus(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        id: String,
        expected: String,
    ) {
        db.query("SELECT status FROM quest_drafts WHERE id = '$id'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(expected, cursor.getString(0))
        }
    }

    private companion object {
        const val DB_NAME = "test_v16_quest_draft_status_saved"
    }
}
