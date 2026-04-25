package com.tpov.schoolquiz.shared.core.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented integration tests for [AppDatabase] schema version 1.
 *
 * Tests: version1_schema_valid, version1_user_stats_insert_query, version1_catalog_insert_query
 * Source: docs/features/menu-refactor/04-testing.md §5.1
 *
 * Run with: ./gradlew :shared:core:persistence:connectedAndroidTest
 * Requires connected device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun version1_schema_valid() {
        val db = helper.createDatabase(DB_NAME, version = 1)
        val tableNames = mutableListOf<String>()
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%'")
        while (cursor.moveToNext()) {
            tableNames.add(cursor.getString(0))
        }
        cursor.close()
        db.close()

        assert("user_stats" in tableNames) {
            "Expected table 'user_stats' but found: $tableNames"
        }
        assert("catalogs" in tableNames) {
            "Expected table 'catalogs' but found: $tableNames"
        }
    }

    @Test
    fun version1_user_stats_insert_query() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addTypeConverter(StringSetConverter())
            .build()

        val entity = UserStatsEntity(
            uid = "uid_test_001",
            nickname = "TestUser",
            avatarUrl = null,
            hasPremium = false,
            streakDays = 0,
            stars = 0L,
            nolics = 0L,
            standardHearts = 5,
            goldHearts = 0,
            gold = 0L,
            currentSkill = 0,
            testerLevel = 0,
            moderatorLevel = 0,
            sponsorLevel = 0,
            translatorLevel = 0,
            adminLevel = 0,
            developerLevel = 0,
        )

        val dao = db.userStatsDao()
        dao.upsert(entity)
        val result = dao.findByUid("uid_test_001")

        assert(result != null) { "Expected entity after upsert, got null" }
        assert(result!!.uid == "uid_test_001") { "uid mismatch: ${result.uid}" }

        db.close()
    }

    @Test
    fun version1_catalog_insert_query() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addTypeConverter(StringSetConverter())
            .build()

        val entities = listOf(
            CatalogEntity(id = "courses", name = "Courses", picturePath = null, pictureUrl = null),
            CatalogEntity(id = "games", name = "Games", picturePath = null, pictureUrl = null),
        )

        val dao = db.catalogDao()
        dao.insertAll(entities)
        val result = dao.observeAll().first()

        assert(result.isNotEmpty()) { "Expected non-empty list after insertAll, got empty" }
        assert(result.size == 2) { "Expected 2 catalogs, got ${result.size}" }

        db.close()
    }

    private companion object {
        const val DB_NAME = "test_app_database"
    }
}
