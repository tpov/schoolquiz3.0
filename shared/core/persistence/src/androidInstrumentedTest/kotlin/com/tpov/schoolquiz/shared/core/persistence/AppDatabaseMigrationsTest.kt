package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration1to2
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration2to3
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration3to4
import com.tpov.schoolquiz.shared.core.persistence.migrations.Migration4to5
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every schema step has to be a real migration, and every real migration has to be proven.
 *
 * `runMigrationsAndValidate` is the proof: it opens a database created at the previous version,
 * applies the migration, and then compares the result against the exported `N.json`. A migration
 * that forgets a column, an index or a default fails here rather than on a player's device.
 *
 * The pairing between `schemas/N.json` and a test in this file is enforced on the JVM side by
 * [MigrationCoverageTest], which runs in `ciCheck` — these instrumented tests need a device.
 *
 * Run with: ./gradlew :shared:core:persistence:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationsTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate_1_to_2_validates_against_exported_schema() {
        helper.createDatabase(DB_NAME, version = 1).close()

        helper.runMigrationsAndValidate(DB_NAME, 2, true, Migration1to2).close()
    }

    @Test
    fun migrate_2_to_3_validates_and_keeps_data_written_before_it() {
        helper.createDatabase(DB_NAME, version = 2).use { db ->
            // A sync cursor is the cheapest row that must never be lost: losing it silently
            // re-reads or, worse, skips a slice of the change journal.
            db.execSQL("INSERT INTO sync_state (collectionId, cursor) VALUES ('catalogs', 1700)")
        }

        helper.runMigrationsAndValidate(DB_NAME, 3, true, Migration2to3).use { db ->
            db.query("SELECT cursor FROM sync_state WHERE collectionId = 'catalogs'").use { c ->
                assertTrue("cursor written before the migration disappeared", c.moveToFirst())
                assertEquals(1700L, c.getLong(0))
            }
        }
    }

    @Test
    fun migrate_3_to_4_validates_against_exported_schema() {
        helper.createDatabase(DB_NAME, version = 3).close()

        helper.runMigrationsAndValidate(DB_NAME, 4, true, Migration3to4).close()
    }

    @Test
    fun migrate_4_to_5_validates_against_exported_schema() {
        helper.createDatabase(DB_NAME, version = 4).close()

        helper.runMigrationsAndValidate(DB_NAME, 5, true, Migration4to5).close()
    }

    @Test
    fun migrate_all_the_way_from_1_validates_against_the_current_schema() {
        helper.createDatabase(DB_NAME, version = 1).close()

        // The path a device that skipped several releases actually takes.
        helper.runMigrationsAndValidate(
            DB_NAME,
            CURRENT_VERSION,
            true,
            Migration1to2,
            Migration2to3,
            Migration3to4,
            Migration4to5,
        ).close()
    }

    private companion object {
        const val DB_NAME = "migration_test"

        /** Must match `@Database(version = …)` on [AppDatabase]; [MigrationCoverageTest] pins it. */
        const val CURRENT_VERSION = 5
    }
}
