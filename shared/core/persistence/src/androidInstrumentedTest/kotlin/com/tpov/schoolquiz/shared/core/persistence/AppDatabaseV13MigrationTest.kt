package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_12_13
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseV13MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate12to13_createsUserProfilesTable() {
        helper.createDatabase(DB_NAME, version = 12).close()

        helper.runMigrationsAndValidate(DB_NAME, 13, true, MIGRATION_12_13).use { db ->
            db.execSQL(
                """
                INSERT INTO user_profiles (
                    uid,
                    nickname,
                    status,
                    avatarUrl,
                    knownLanguages,
                    createdAtMs,
                    updatedAtMs,
                    skillPoints,
                    gold,
                    nolics,
                    standardHearts,
                    goldHearts,
                    sponsorLevel,
                    testerLevel,
                    translatorLevel,
                    moderatorLevel,
                    adminLevel,
                    developerLevel
                ) VALUES (
                    'uid-1',
                    'User000001',
                    'ANONYMOUS',
                    NULL,
                    'ru',
                    1,
                    1,
                    0,
                    0,
                    0,
                    5,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
                )
                """.trimIndent(),
            )
            db.query("SELECT nickname, standardHearts FROM user_profiles WHERE uid = 'uid-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("User000001", cursor.getString(0))
                assertEquals(5, cursor.getInt(1))
            }
        }
    }

    private companion object {
        const val DB_NAME = "test_v13_user_profiles"
    }
}
