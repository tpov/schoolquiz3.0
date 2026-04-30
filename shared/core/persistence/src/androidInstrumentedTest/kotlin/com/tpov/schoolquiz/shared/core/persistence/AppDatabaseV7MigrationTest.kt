package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_6_7
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseV7MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate6to7_syncStateCreated() {
        helper.createDatabase(DB_NAME, version = 6).close()

        helper.runMigrationsAndValidate(DB_NAME, 7, true, MIGRATION_6_7).use { db ->
            db.execSQL("INSERT INTO sync_state (collectionId, cursor) VALUES ('catalog_sync:cat-1', 42)")
            val cursor = db.query("SELECT cursor FROM sync_state WHERE collectionId = 'catalog_sync:cat-1'")
            assert(cursor.moveToFirst()) { "sync_state row should be readable after migration" }
            assert(cursor.getLong(0) == 42L) { "Expected cursor 42, got ${cursor.getLong(0)}" }
            cursor.close()
        }
    }

    private companion object {
        const val DB_NAME = "test_v7_sync_state"
    }
}
