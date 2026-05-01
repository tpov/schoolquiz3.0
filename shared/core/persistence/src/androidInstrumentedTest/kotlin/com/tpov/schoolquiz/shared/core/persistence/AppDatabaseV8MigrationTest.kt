package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_7_8
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseV8MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate7to8_questDraftTablesCreated() {
        helper.createDatabase(DB_NAME, version = 7).close()

        helper.runMigrationsAndValidate(DB_NAME, 8, true, MIGRATION_7_8).use { db ->
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
                    'draft-1',
                    'owner-1',
                    'catalog-1',
                    'Draft',
                    NULL,
                    'ru',
                    'EASY',
                    'DRAFT',
                    1,
                    NULL,
                    NULL,
                    1,
                    2,
                    1
                )
                """.trimIndent(),
            )
            val cursor = db.query("SELECT title FROM quest_drafts WHERE id = 'draft-1'")
            assert(cursor.moveToFirst()) { "quest_drafts row should be readable after migration" }
            assert(cursor.getString(0) == "Draft") { "Expected title Draft, got ${cursor.getString(0)}" }
            cursor.close()
        }
    }

    private companion object {
        const val DB_NAME = "test_v8_quest_authoring"
    }
}
