package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_16_17
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseV17MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
        )

    @Test
    fun migrate16to17_addsGiftBoxRewardProfileColumns() {
        helper.createDatabase(DB_NAME, version = 16).use { db ->
            insertProfile(db)
        }

        helper.runMigrationsAndValidate(DB_NAME, 17, true, MIGRATION_16_17).use { db ->
            db.query(
                """
                SELECT boxCount, boxStreakDays, nextBoxAtMs, premiumUntilMs, trophies, ownedLogos
                FROM user_profiles
                WHERE uid = 'uid-1'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
                assertEquals(0, cursor.getInt(1))
                assertEquals(0L, cursor.getLong(2))
                assertEquals(0L, cursor.getLong(3))
                assertEquals(0L, cursor.getLong(4))
                assertEquals("", cursor.getString(5))
            }
        }
    }

    private fun insertProfile(db: SupportSQLiteDatabase) {
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
    }

    private companion object {
        const val DB_NAME = "test_v17_profile_gift_box_rewards"
    }
}
