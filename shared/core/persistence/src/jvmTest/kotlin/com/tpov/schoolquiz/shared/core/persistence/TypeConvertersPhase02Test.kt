package com.tpov.schoolquiz.shared.core.persistence

import com.tpov.schoolquiz.shared.core.leaderboard.TopParticipant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JVM unit tests for TopParticipantListConverter.
 * Also includes MT-06 static grep check for production code safety.
 * Source: docs/features/lesson-runner/plan/phase-02/tests.md §MT-06..MT-07c
 * Note: MT-05 (DifficultyConverter) removed per ADR-LR-18 — converter deleted, mapper handles Difficulty↔Int.
 *
 * Run with: ./gradlew :shared:core:persistence:jvmTest --no-configuration-cache
 *
 * Spec coverage:
 *   MT-06: no source set may contain fallbackToDestructiveMigration
 *   MT-07: TopParticipantListConverter roundtrip
 *   MT-07b: fromDb("") → emptyList()
 *   MT-07c: fromDb("not-json") → emptyList(), no exception
 */
class TypeConvertersPhase02Test {

    private val topParticipantListConverter = TopParticipantListConverter()

    // MT-06: GIVEN any source set WHEN grep fallbackToDestructiveMigration THEN no match
    @Test
    fun noSourceSet_callsFallbackToDestructiveMigration() {
        // Widened from androidMain to every source set: a destructive fallback in a test is just
        // as good at hiding a missing migration, and that is exactly how the schema history became
        // fiction the first time. MigrationCoverageTest owns the same rule for the pairing gate.
        val srcDir = File("src")
        if (!srcDir.exists()) return // skip if run outside the module directory

        val offendingFiles = srcDir.walkTopDown()
            .filter { it.name != "TypeConvertersPhase02Test.kt" && it.name != "MigrationCoverageTest.kt" }
            .filter { it.extension == "kt" }
            .filter { it.readText().contains("fallbackToDestructiveMigration") }
            .map { it.path }
            .toList()

        assertTrue(
            "No source set may call fallbackToDestructiveMigration. Found in: $offendingFiles",
            offendingFiles.isEmpty()
        )
    }

    // MT-07: GIVEN TopParticipantListConverter WHEN toDb(list) → fromDb THEN same list
    @Test
    fun `topParticipantListConverter roundtrip`() {
        val original = listOf(TopParticipant("Alice", null, 90))

        val encoded = topParticipantListConverter.toDb(original)
        val decoded = topParticipantListConverter.fromDb(encoded)

        assertEquals("Roundtrip should preserve size", 1, decoded.size)
        assertEquals("Roundtrip should preserve nickname", "Alice", decoded.first().nickname)
        assertEquals(null, decoded.first().avatarUrl)
        assertEquals(90, decoded.first().percent)
    }

    // MT-07: GIVEN list with avatarUrl WHEN roundtrip THEN preserved
    @Test
    fun `topParticipantListConverter roundtrip with avatarUrl`() {
        val original = listOf(TopParticipant("Bob", "https://cdn.example/bob.png", 85))

        val decoded = topParticipantListConverter.fromDb(topParticipantListConverter.toDb(original))

        assertEquals("Bob", decoded.first().nickname)
        assertEquals("https://cdn.example/bob.png", decoded.first().avatarUrl)
        assertEquals(85, decoded.first().percent)
    }

    // MT-07b: GIVEN TopParticipantListConverter WHEN fromDb("") THEN emptyList()
    @Test
    fun `topParticipantListConverter emptyString returns emptyList`() {
        val result = topParticipantListConverter.fromDb("")
        assertTrue("fromDb(\"\") must return emptyList, got $result", result.isEmpty())
    }

    // MT-07c: GIVEN TopParticipantListConverter WHEN fromDb("not-json") THEN emptyList(), no exception
    @Test
    fun `topParticipantListConverter malformedJson returns emptyList`() {
        val result = topParticipantListConverter.fromDb("not-json")
        assertTrue("fromDb(malformed) must return emptyList, got $result", result.isEmpty())
    }
}
