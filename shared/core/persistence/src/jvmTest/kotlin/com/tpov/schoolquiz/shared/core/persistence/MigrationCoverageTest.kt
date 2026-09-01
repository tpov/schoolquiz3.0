package com.tpov.schoolquiz.shared.core.persistence

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate that keeps the schema history honest.
 *
 * The history was fiction once already: `1.json` and `2.json` were exported with the same
 * `identityHash`, so a version had been bumped without a migration behind it. Nothing caught that,
 * because the proof lives in instrumented tests that need a device and never run in `ciCheck`.
 *
 * This test is the part that can run without a device: it does not migrate anything, it checks
 * that for every exported schema step there is a migration and a test that validates it. The
 * migration itself is proven on a device by `AppDatabaseMigrationsTest`.
 *
 * A file-reading test rather than a reflective one, on purpose — the thing being guarded is the
 * presence of source, not the behaviour of a class.
 */
class MigrationCoverageTest {

    private val schemasDir = File("schemas/com.tpov.schoolquiz.shared.core.persistence.AppDatabase")
    private val migrationsDir = File("src/androidMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/migrations")
    private val migrationTest = File(
        "src/androidInstrumentedTest/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabaseMigrationsTest.kt",
    )

    private fun exportedVersions(): List<Int> =
        schemasDir.listFiles()
            .orEmpty()
            .mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }
            .sorted()

    @Test
    fun `every exported schema step has a migration`() {
        if (!schemasDir.exists()) return // skip when run outside the module directory
        val versions = exportedVersions()
        assertTrue("no exported schemas found in ${schemasDir.path}", versions.isNotEmpty())

        val declared = migrationsDir.listFiles().orEmpty().joinToString("\n") { it.readText() }
        val missing = versions.filter { it >= 2 }.filterNot { v ->
            declared.contains("Migration(${v - 1}, $v)")
        }

        assertTrue(
            "Exported schema exists with no migration behind it: " +
                missing.joinToString { "${it - 1} -> $it" } +
                ". Add the migration in ${migrationsDir.path}.",
            missing.isEmpty(),
        )
    }

    @Test
    fun `every migration is validated by an instrumented test`() {
        if (!schemasDir.exists()) return
        assertTrue("missing ${migrationTest.path}", migrationTest.exists())
        val body = migrationTest.readText()

        val unproven = exportedVersions().filter { it >= 2 }.filterNot { v ->
            body.contains(Regex("""runMigrationsAndValidate\(\s*\w+\s*,\s*$v\s*,"""))
        }

        assertTrue(
            "Migration with no runMigrationsAndValidate behind it: " +
                unproven.joinToString { "${it - 1} -> $it" } +
                ". Add the case to ${migrationTest.path}.",
            unproven.isEmpty(),
        )
    }

    @Test
    fun `declared database version matches the newest exported schema`() {
        if (!schemasDir.exists()) return
        val newest = exportedVersions().max()

        val declared = File(
            "src/commonMain/kotlin/com/tpov/schoolquiz/shared/core/persistence/AppDatabase.kt",
        ).readText()
        val declaredVersion = Regex("""version\s*=\s*(\d+)""").find(declared)?.groupValues?.get(1)?.toInt()
        assertEquals("@Database version does not match the newest exported schema", newest, declaredVersion)

        val pinned = Regex("""CURRENT_VERSION\s*=\s*(\d+)""").find(migrationTest.readText())
            ?.groupValues?.get(1)?.toInt()
        assertEquals("CURRENT_VERSION in the migration test drifted from the schema", newest, pinned)
    }

    @Test
    fun `no source set calls fallbackToDestructiveMigration`() {
        val src = File("src")
        if (!src.exists()) return

        val offenders = src.walkTopDown()
            .filter { it.extension == "kt" }
            .filter { it.name != "MigrationCoverageTest.kt" && it.name != "TypeConvertersPhase02Test.kt" }
            .filter { it.readText().contains("fallbackToDestructiveMigration") }
            .map { it.path }
            .toList()

        assertTrue(
            "fallbackToDestructiveMigration silently wipes the player's data. Found in: $offenders",
            offenders.isEmpty(),
        )
    }
}
