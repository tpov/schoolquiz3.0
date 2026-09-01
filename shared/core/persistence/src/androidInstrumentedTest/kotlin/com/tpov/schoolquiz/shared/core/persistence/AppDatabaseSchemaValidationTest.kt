package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented schema validation tests for [AppDatabase] version 2.
 * Source: docs/features/home-and-my-quests/plan/phase-01/tests.md §1
 * Run with: ./gradlew :shared:core:persistence:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseSchemaValidationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun schema_v2_has_all_7_tables() {
        val db = helper.createDatabase(DB_NAME_V2, version = 2)

        db.execSQL("SELECT * FROM quests LIMIT 1")
        db.execSQL("SELECT * FROM sections LIMIT 1")
        db.execSQL("SELECT * FROM themes LIMIT 1")
        db.execSQL("SELECT * FROM lessons LIMIT 1")
        db.execSQL("SELECT * FROM questions LIMIT 1")

        db.close()
    }

    @Test
    fun catalog_v2_has_new_columns() {
        val db = helper.createDatabase(DB_NAME_V2_COLS, version = 2)

        db.execSQL("SELECT version, contentsVersion, lastModifiedAt, archived FROM catalogs LIMIT 1")

        db.close()
    }

    private companion object {
        const val DB_NAME_V2 = "test_schema_v2"
        const val DB_NAME_V2_COLS = "test_schema_v2_cols"
    }
}
