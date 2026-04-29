package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tpov.schoolquiz.shared.core.persistence.migrations.MIGRATION_3_4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented migration tests for AppDatabase v3 → v4.
 * Source: docs/features/lesson-runner/plan/phase-02/tests.md §MT-01..MT-04
 *
 * Run with: ./gradlew :shared:core:persistence:connectedAndroidTest
 * Requires connected device or emulator.
 *
 * Spec coverage:
 *   MT-01: lesson_attempts table + 8 columns
 *   MT-02: lesson_rating_submitted_local table + compound PK
 *   MT-03: existing lessons get default values for new columns
 *   MT-04: all 7 existing tables preserved with data
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseV4MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    // MT-01: GIVEN v3 db WHEN MIGRATION_3_4 THEN lesson_attempts table exists with 8 columns
    @Test
    fun migrate3to4_lessonAttemptsCreated() {
        helper.createDatabase(DB_NAME, version = 3).close()

        helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_3_4).use { db ->
            db.execSQL(
                "SELECT attempt_id, user_id, lesson_id, lesson_version, is_hard, code_answer, percent_score, completed_at " +
                    "FROM lesson_attempts LIMIT 0"
            )
        }
    }

    // MT-02: GIVEN v3 db WHEN MIGRATION_3_4 THEN lesson_rating_submitted_local exists with compound PK
    @Test
    fun migrate3to4_lessonRatingSubmittedLocalCreated() {
        helper.createDatabase(DB_NAME_MT02, version = 3).close()

        helper.runMigrationsAndValidate(DB_NAME_MT02, 4, true, MIGRATION_3_4).use { db ->
            db.execSQL("SELECT user_id, lesson_id, submitted_at FROM lesson_rating_submitted_local LIMIT 0")
            db.execSQL(
                "INSERT INTO lesson_rating_submitted_local (user_id, lesson_id, submitted_at) VALUES ('u1','l1',1000)"
            )
            var threw = false
            try {
                db.execSQL(
                    "INSERT INTO lesson_rating_submitted_local (user_id, lesson_id, submitted_at) VALUES ('u1','l1',2000)"
                )
            } catch (_: Exception) {
                threw = true
            }
            assert(threw) { "Expected compound PK violation on duplicate (user_id, lesson_id)" }
        }
    }

    // MT-03: GIVEN v3 db with one lessons row WHEN MIGRATION_3_4 THEN average_rating IS NULL, rating_count=0, top3='[]'
    @Test
    fun migrate3to4_existingLessonsNewColumnsDefaultValues() {
        val db = helper.createDatabase(DB_NAME_MT03, version = 3)
        db.execSQL("INSERT INTO catalogs (id, name, picturePath, pictureUrl, version, contentsVersion, lastModifiedAt, archived) VALUES ('cat1','Cat',NULL,NULL,1,0,0,0)")
        db.execSQL("INSERT INTO quests (id, catalogId, authorUid, title, picturePath, pictureUrl, visibleOn, averageRating, averageRatingCount, version, contentsVersion, lastModifiedAt, archived) VALUES ('q1','cat1','u1','Quest',NULL,NULL,'',NULL,0,1,0,0,0)")
        db.execSQL("INSERT INTO sections (id, questId, title, \"order\", version, contentsVersion, lastModifiedAt, archived) VALUES ('s1','q1','Section',0,1,0,0,0)")
        db.execSQL("INSERT INTO themes (id, sectionId, title, \"order\", version, contentsVersion, lastModifiedAt, archived) VALUES ('th1','s1','Theme',0,1,0,0,0)")
        db.execSQL("INSERT INTO lessons (id, themeId, title, \"order\", version, contentsVersion, lastModifiedAt, archived) VALUES ('l1','th1','Lesson',0,1,0,0,0)")
        db.close()

        helper.runMigrationsAndValidate(DB_NAME_MT03, 4, true, MIGRATION_3_4).use { migratedDb ->
            val cursor = migratedDb.query("SELECT average_rating, rating_count, top3 FROM lessons WHERE id = 'l1'")
            assert(cursor.moveToFirst()) { "lesson row should still exist after migration" }

            val avgRatingIdx = cursor.getColumnIndexOrThrow("average_rating")
            val ratingCountIdx = cursor.getColumnIndexOrThrow("rating_count")
            val top3Idx = cursor.getColumnIndexOrThrow("top3")

            assert(cursor.isNull(avgRatingIdx)) {
                "average_rating should be NULL after migration, got ${cursor.getString(avgRatingIdx)}"
            }
            assert(cursor.getInt(ratingCountIdx) == 0) {
                "rating_count should be 0, got ${cursor.getInt(ratingCountIdx)}"
            }
            assert(cursor.getString(top3Idx) == "[]") {
                "top3 should be '[]', got ${cursor.getString(top3Idx)}"
            }
            cursor.close()
        }
    }

    // MT-04: GIVEN v3 db with 1 row in each of 7 tables WHEN MIGRATION_3_4 THEN all rows preserved
    @Test
    fun migrate3to4_allExistingTablesPreserved() {
        val db = helper.createDatabase(DB_NAME_MT04, version = 3)
        db.execSQL("INSERT INTO user_stats (uid, nickname, avatarUrl, hasPremium, streakDays, stars, nolics, standardHearts, goldHearts, gold, currentSkill, testerLevel, moderatorLevel, sponsorLevel, translatorLevel, adminLevel, developerLevel) VALUES ('u1','Nick',NULL,0,0,0,0,5,0,0,0,0,0,0,0,0,0)")
        db.execSQL("INSERT INTO catalogs (id, name, picturePath, pictureUrl, version, contentsVersion, lastModifiedAt, archived) VALUES ('cat1','Cat',NULL,NULL,1,0,0,0)")
        db.execSQL("INSERT INTO quests (id, catalogId, authorUid, title, picturePath, pictureUrl, visibleOn, averageRating, averageRatingCount, version, contentsVersion, lastModifiedAt, archived) VALUES ('q1','cat1','u1','Quest',NULL,NULL,'',NULL,0,1,0,0,0)")
        db.execSQL("INSERT INTO sections (id, questId, title, \"order\", version, contentsVersion, lastModifiedAt, archived) VALUES ('s1','q1','Section',0,1,0,0,0)")
        db.execSQL("INSERT INTO themes (id, sectionId, title, \"order\", version, contentsVersion, lastModifiedAt, archived) VALUES ('th1','s1','Theme',0,1,0,0,0)")
        db.execSQL("INSERT INTO lessons (id, themeId, title, \"order\", version, contentsVersion, lastModifiedAt, archived) VALUES ('l1','th1','Lesson',0,1,0,0,0)")
        db.execSQL("INSERT INTO questions (id, lessonId, text, payload, language, \"order\", version, lastModifiedAt, archived) VALUES ('qn1','l1','Q','{}','en',0,1,0,0)")
        db.close()

        helper.runMigrationsAndValidate(DB_NAME_MT04, 4, true, MIGRATION_3_4).use { migratedDb ->
            fun rowCount(table: String): Int {
                val c = migratedDb.query("SELECT COUNT(*) FROM $table")
                c.moveToFirst()
                return c.getInt(0).also { c.close() }
            }

            listOf("user_stats", "catalogs", "quests", "sections", "themes", "lessons", "questions").forEach { table ->
                assert(rowCount(table) == 1) {
                    "Expected 1 row in '$table' after migration, got ${rowCount(table)}"
                }
            }
        }
    }

    private companion object {
        const val DB_NAME = "test_v4_mt01"
        const val DB_NAME_MT02 = "test_v4_mt02"
        const val DB_NAME_MT03 = "test_v4_mt03"
        const val DB_NAME_MT04 = "test_v4_mt04"
    }
}
