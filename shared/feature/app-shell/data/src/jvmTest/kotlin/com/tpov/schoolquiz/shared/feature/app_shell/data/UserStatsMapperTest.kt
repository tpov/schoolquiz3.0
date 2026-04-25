package com.tpov.schoolquiz.shared.feature.app_shell.data

import com.tpov.schoolquiz.shared.core.persistence.UserStatsEntity
import com.tpov.schoolquiz.shared.core.stats.RawUserStats
import com.tpov.schoolquiz.shared.feature.app_shell.data.mapper.toDomain
import com.tpov.schoolquiz.shared.feature.app_shell.data.mapper.toEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// UserStatsMapper round-trip tests — 04-testing.md "UserStatsMapper round-trip tests (как часть US-03)".
// Require: UserStatsMapper.kt (Phase 04).
class UserStatsMapperTest {

    // -------------------------------------------------------------------------
    // UserStatsEntity.toDomain() — qualification fields
    // -------------------------------------------------------------------------

    @Test
    fun `entity toDomain maps qualification developer field`() {
        val entity = entity(developerLevel = 100)
        assertEquals(100, entity.toDomain().qualification.developer)
    }

    @Test
    fun `entity toDomain maps all qualification fields correctly`() {
        val entity = entity(
            testerLevel = 2,
            moderatorLevel = 1,
            sponsorLevel = 3,
            translatorLevel = 0,
            adminLevel = 0,
            developerLevel = 100,
        )

        val domain = entity.toDomain()

        assertEquals(2, domain.qualification.tester)
        assertEquals(1, domain.qualification.moderator)
        assertEquals(3, domain.qualification.sponsor)
        assertEquals(0, domain.qualification.translator)
        assertEquals(0, domain.qualification.admin)
        assertEquals(100, domain.qualification.developer)
    }

    @Test
    fun `entity toDomain maps all scalar fields`() {
        val entity = entity(
            nickname = "Bob",
            avatarUrl = "https://example.com/b.png",
            hasPremium = false,
            streakDays = 7,
            stars = 1234L,
            nolics = 5678L,
            standardHearts = 5,
            goldHearts = 0,
            gold = 999L,
            currentSkill = 42,
        )

        val domain = entity.toDomain()

        assertEquals("Bob", domain.nickname)
        assertEquals("https://example.com/b.png", domain.avatarUrl)
        assertEquals(false, domain.hasPremium)
        assertEquals(7, domain.streakDays)
        assertEquals(1234L, domain.stars)
        assertEquals(5678L, domain.nolics)
        assertEquals(5, domain.standardHearts)
        assertEquals(0, domain.goldHearts)
        assertEquals(999L, domain.gold)
        assertEquals(42, domain.currentSkill)
    }

    @Test
    fun `entity toDomain preserves null avatarUrl`() {
        assertNull(entity(avatarUrl = null).toDomain().avatarUrl)
    }

    @Test
    fun `entity toDomain developer 0 preserved`() {
        assertEquals(0, entity(developerLevel = 0).toDomain().qualification.developer)
    }

    // -------------------------------------------------------------------------
    // RawUserStats.toEntity(uid) — flat field mapping
    // -------------------------------------------------------------------------

    @Test
    fun `raw toEntity sets uid correctly`() {
        assertEquals("uid-abc", RawUserStats().toEntity("uid-abc").uid)
    }

    @Test
    fun `raw toEntity maps qualification levels to flat fields`() {
        val raw = RawUserStats(
            developerLevel = 100,
            testerLevel = 2,
            moderatorLevel = 1,
            sponsorLevel = 3,
            translatorLevel = 0,
            adminLevel = 0,
        )

        val entity = raw.toEntity("uid-abc")

        assertEquals(100, entity.developerLevel)
        assertEquals(2, entity.testerLevel)
        assertEquals(1, entity.moderatorLevel)
        assertEquals(3, entity.sponsorLevel)
        assertEquals(0, entity.translatorLevel)
        assertEquals(0, entity.adminLevel)
    }

    @Test
    fun `raw toEntity maps all scalar fields`() {
        val raw = RawUserStats(
            nickname = "Charlie",
            avatarUrl = "https://example.com/c.png",
            hasPremium = true,
            streakDays = 10,
            stars = 500L,
            nolics = 1000L,
            standardHearts = 2,
            goldHearts = 1,
            gold = 77L,
            currentSkill = 999,
        )

        val entity = raw.toEntity("uid-xyz")

        assertEquals("Charlie", entity.nickname)
        assertEquals("https://example.com/c.png", entity.avatarUrl)
        assertEquals(true, entity.hasPremium)
        assertEquals(10, entity.streakDays)
        assertEquals(500L, entity.stars)
        assertEquals(1000L, entity.nolics)
        assertEquals(2, entity.standardHearts)
        assertEquals(1, entity.goldHearts)
        assertEquals(77L, entity.gold)
        assertEquals(999, entity.currentSkill)
    }

    @Test
    fun `raw toEntity preserves null avatarUrl`() {
        assertNull(RawUserStats(avatarUrl = null).toEntity("u1").avatarUrl)
    }

    // -------------------------------------------------------------------------
    // US-08 mapper proof — RawUserStats(developer=0).toEntity preserves 0
    // Ensures full-overwrite behaviour (dev mode auto-deactivation)
    // -------------------------------------------------------------------------

    @Test
    fun `raw developerLevel 0 toEntity preserves 0 for dev mode auto-deactivation`() {
        assertEquals(0, RawUserStats(developerLevel = 0).toEntity("u1").developerLevel)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun entity(
        uid: String = "u1",
        nickname: String = "Test",
        avatarUrl: String? = "https://example.com/a.png",
        hasPremium: Boolean = false,
        streakDays: Int = 0,
        stars: Long = 0L,
        nolics: Long = 0L,
        standardHearts: Int = 0,
        goldHearts: Int = 0,
        gold: Long = 0L,
        currentSkill: Int = 0,
        testerLevel: Int = 0,
        moderatorLevel: Int = 0,
        sponsorLevel: Int = 0,
        translatorLevel: Int = 0,
        adminLevel: Int = 0,
        developerLevel: Int = 0,
    ) = UserStatsEntity(
        uid = uid,
        nickname = nickname,
        avatarUrl = avatarUrl,
        hasPremium = hasPremium,
        streakDays = streakDays,
        stars = stars,
        nolics = nolics,
        standardHearts = standardHearts,
        goldHearts = goldHearts,
        gold = gold,
        currentSkill = currentSkill,
        testerLevel = testerLevel,
        moderatorLevel = moderatorLevel,
        sponsorLevel = sponsorLevel,
        translatorLevel = translatorLevel,
        adminLevel = adminLevel,
        developerLevel = developerLevel,
    )
}
