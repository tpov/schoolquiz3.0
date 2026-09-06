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

    /**
     * Two neighbouring versions cannot describe the same shape.
     *
     * `identityHash` is Room's fingerprint of the schema. If `N-1.json` and `N.json` carry the same
     * one, then either the version was bumped with nothing behind it, or — the case that actually
     * happened twice here — a schema file was re-exported from a later state of the entities and
     * quietly overwritten. The migration test above still passes in both cases: it only checks that
     * a migration and a call exist, and the exported file it validates against is the fiction.
     *
     * `1.json` was overwritten this way once (it was re-exported after `user_profiles` had already
     * changed shape), and `9.json` a second time (re-exported after `sync_run_trace` had gained its
     * `kind` column). Nothing on the JVM side saw either.
     */
    @Test
    fun `neighbouring exported schemas do not share an identity hash`() {
        if (!schemasDir.exists()) return
        val hashes = exportedVersions().associateWith(::identityHashOf)

        val collisions = exportedVersions().filter { it >= 2 }.filter { v ->
            hashes[v] != null && hashes[v] == hashes[v - 1]
        }

        assertTrue(
            "Neighbouring exported schemas describe the same shape: " +
                collisions.joinToString { "${it - 1}.json == $it.json (${hashes[it]})" } +
                ". A version was bumped without a shape change behind it, or a schema file was " +
                "re-exported from a later state of the entities. Re-export the older one from the " +
                "shape it actually had.",
            collisions.isEmpty(),
        )
    }

    /**
     * A table a migration creates has to have the shape the version it migrates to exports.
     *
     * This is the check that makes the previous one hard to fool: comparing hashes catches a file
     * copied wholesale, comparing the `CREATE TABLE` a migration issues against the exported
     * `createSql` catches the shape drifting by a single column. It is the same comparison
     * `runMigrationsAndValidate` makes on a device — done here on the text, so it runs in `ciCheck`
     * where the instrumented tests cannot.
     *
     * Whitespace is ignored: migrations write the statement across lines, the export writes it on
     * one. Tables the target schema does not contain are skipped — those are the scratch tables a
     * rebuild goes through, and they are not supposed to survive the migration.
     */
    @Test
    fun `a table a migration creates has the shape its target schema exports`() {
        if (!schemasDir.exists()) return
        val mismatches = mutableListOf<String>()

        migrationsDir.listFiles().orEmpty().sortedBy { it.name }.forEach { file ->
            val text = file.readText()
            val target = Regex("""Migration\(\d+,\s*(\d+)\)""")
                .find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
            val exported = exportedCreateSql(target)

            createTableStatements(text).forEach { (table, statement) ->
                val schema = exported[table]?.replace("\${TABLE_NAME}", table) ?: return@forEach
                if (statement.withoutWhitespace() != schema.withoutWhitespace()) {
                    mismatches += "${file.name} creates `$table` in a shape $target.json does not " +
                        "describe:\n  migration: $statement\n  schema:    $schema"
                }
            }
        }

        assertTrue(
            "A migration and the schema it migrates to disagree about a table. One of the two is " +
                "a lie, and the device finds out first:\n" + mismatches.joinToString("\n"),
            mismatches.isEmpty(),
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

    private fun identityHashOf(version: Int): String? =
        Regex(""""identityHash"\s*:\s*"(\w+)"""")
            .find(File(schemasDir, "$version.json").readText())
            ?.groupValues?.get(1)

    private fun exportedCreateSql(version: Int): Map<String, String> {
        val file = File(schemasDir, "$version.json")
        if (!file.exists()) return emptyMap()
        // The bundle is read with a regex rather than a JSON parser on purpose: this module's test
        // source set has no JSON dependency, and the two fields needed here sit next to each other.
        return Regex(""""tableName"\s*:\s*"(\w+)",\s*"createSql"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\`", "`").replace("\\\"", "\"") }
    }

    /**
     * The `CREATE TABLE` statements a migration issues, as (table, statement) pairs.
     *
     * Adjacent string literals are joined first, so a statement split across lines by `+` reads as
     * one; the body is then taken by counting parentheses, which works for both a concatenation and
     * a raw string.
     */
    private fun createTableStatements(source: String): List<Pair<String, String>> {
        val joined = source.replace(Regex(""""\s*\+\s*""""), "")
        return Regex("""CREATE TABLE IF NOT EXISTS `(\w+)`""").findAll(joined).mapNotNull { match ->
            val open = joined.indexOf('(', match.range.last + 1)
            if (open < 0) return@mapNotNull null
            var depth = 0
            var index = open
            while (index < joined.length) {
                if (joined[index] == '(') depth++
                if (joined[index] == ')') {
                    depth--
                    if (depth == 0) break
                }
                index++
            }
            if (index >= joined.length) null else match.groupValues[1] to joined.substring(match.range.first, index + 1)
        }.toList()
    }

    private fun String.withoutWhitespace(): String = filterNot { it.isWhitespace() }
}
