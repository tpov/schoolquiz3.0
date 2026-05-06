package com.tpov.schoolquiz.shared.feature.app_shell.domain

import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.actualLevel
import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.emptyRootFor
import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.isVisible
import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.rootOf
import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.visibleFooterActions
import com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.visibleSections
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerFooterAction
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.EventsConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.InternetConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.LocalConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Qualification
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Role
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.ShopConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Title
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for progressive section unlock visibility functions.
 *
 * Coverage:
 * - Domain Test Scenarios 22-35 (except 34 which is in AppShellTransitionsTest)
 * - Section Visibility Rules table (all 12 rows)
 * - Title.skillRange and Title.first
 * - actualLevel routing
 */
class VisibilityTest {

    // -----------------------------------------------------------------------
    // Title enum — verify thresholds
    // -----------------------------------------------------------------------

    @Test
    fun `Title RECRUIT skillRange starts at 0`() {
        assertEquals(0, Title.RECRUIT.first)
    }

    @Test
    fun `Title TEACHINGS first equals 3000`() {
        assertEquals(3_000, Title.TEACHINGS.first)
    }

    @Test
    fun `Title PLAYER first equals 10000`() {
        assertEquals(10_000, Title.PLAYER.first)
    }

    @Test
    fun `Title LEGEND upper bound is Int MAX_VALUE`() {
        assertEquals(Int.MAX_VALUE, Title.LEGEND.skillRange.last)
    }

    @Test
    fun `Title has 11 values`() {
        assertEquals(11, Title.entries.size)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 32 — actualLevel routes USER to currentSkill
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 32 actualLevel for Role USER returns stats currentSkill`() {
        val stats = UserStats.guest().copy(currentSkill = 5_000)
        assertEquals(5_000, actualLevel(Role.USER, stats))
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 33 — actualLevel routes ADMIN to qualification.admin
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 33 actualLevel for Role ADMIN returns qualification admin`() {
        val stats = UserStats.guest().copy(
            qualification = Qualification.zero().copy(admin = 42),
        )
        assertEquals(42, actualLevel(Role.ADMIN, stats))
    }

    @Test
    fun `actualLevel routes TESTER to qualification tester`() {
        val stats = UserStats.guest().copy(qualification = Qualification.zero().copy(tester = 7))
        assertEquals(7, actualLevel(Role.TESTER, stats))
    }

    @Test
    fun `actualLevel routes MODERATOR to qualification moderator`() {
        val stats = UserStats.guest().copy(qualification = Qualification.zero().copy(moderator = 55))
        assertEquals(55, actualLevel(Role.MODERATOR, stats))
    }

    @Test
    fun `actualLevel routes SPONSOR to qualification sponsor`() {
        val stats = UserStats.guest().copy(qualification = Qualification.zero().copy(sponsor = 13))
        assertEquals(13, actualLevel(Role.SPONSOR, stats))
    }

    @Test
    fun `actualLevel routes TRANSLATOR to qualification translator`() {
        val stats = UserStats.guest().copy(qualification = Qualification.zero().copy(translator = 99))
        assertEquals(99, actualLevel(Role.TRANSLATOR, stats))
    }

    @Test
    fun `actualLevel routes DEVELOPER to qualification developer`() {
        val stats = UserStats.guest().copy(qualification = Qualification.zero().copy(developer = 200))
        assertEquals(200, actualLevel(Role.DEVELOPER, stats))
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 35 — empty requiredRoles → always visible
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 35 empty requiredRoles returns true for any stats`() {
        // MyQuests has emptyMap()
        val section = DrawerSection.LocalSection.MyQuests
        assertTrue(section.requiredRoles.isEmpty())
        assertTrue(isVisible(section, UserStats.guest()))
    }

    @Test
    fun `scenario 35 empty requiredRoles returns true for high skill stats too`() {
        val section = DrawerSection.LocalSection.Settings
        assertTrue(isVisible(section, UserStats.guest().copy(currentSkill = 999_999)))
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 29 — Arena invisible at skill 2999 (boundary inclusive)
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 29 Arena requires USER 3000 at skill 2999 returns false`() {
        val stats = UserStats.guest().copy(currentSkill = 2_999)
        assertFalse(isVisible(DrawerSection.InternetSection.Arena, stats))
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 30 — Arena visible at skill 3000
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 30 Arena requires USER 3000 at skill 3000 returns true`() {
        val stats = UserStats.guest().copy(currentSkill = 3_000)
        assertTrue(isVisible(DrawerSection.InternetSection.Arena, stats))
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 31 — ActiveEvents AND semantics (developer=99 fails)
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 31 ActiveEvents requires all 4 roles 100 developer 99 returns false`() {
        val stats = UserStats.guest().copy(
            qualification = Qualification(
                tester = 100,
                moderator = 100,
                sponsor = 0,
                translator = 0,
                admin = 100,
                developer = 99,
            ),
        )
        assertFalse(isVisible(DrawerSection.EventsSection.ActiveEvents, stats))
    }

    @Test
    fun `ActiveEvents all 4 roles at exactly 100 returns true`() {
        val stats = UserStats.guest().copy(
            qualification = Qualification(
                tester = 100,
                moderator = 100,
                sponsor = 0,
                translator = 0,
                admin = 100,
                developer = 100,
            ),
        )
        assertTrue(isVisible(DrawerSection.EventsSection.ActiveEvents, stats))
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 22 — visibleSections LOCAL for guest
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 22 visibleSections LOCAL guest returns HomeQuests Archive ReviewQueue MyQuests Settings`() {
        val sections = visibleSections(Tab.LOCAL, UserStats.guest())
        // DesignCatalog removed from LocalSection (now DrawerFooterAction).
        assertEquals(
            listOf(
                DrawerSection.LocalSection.HomeQuests,
                DrawerSection.LocalSection.Archive,
                DrawerSection.LocalSection.ReviewQueue,
                DrawerSection.LocalSection.MyQuests,
                DrawerSection.LocalSection.Settings,
            ),
            sections,
        )
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 23 — visibleSections INTERNET for guest
    // Product order keeps Profile before Qualifications so Internet opens on the profile card
    // for guest and anonymous users.
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 23 visibleSections INTERNET guest returns Profile and Qualifications`() {
        val sections = visibleSections(Tab.INTERNET, UserStats.guest())
        // Product order: Arena(3000), Catalog(3000), Profile(empty), Qualifications(empty), Social(10000), Leaderboard(3000)
        // Visible for guest: Profile, Qualifications
        assertEquals(
            listOf(
                DrawerSection.InternetSection.Profile,
                DrawerSection.InternetSection.Qualifications,
            ),
            sections,
        )
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 24 — visibleSections EVENTS for guest = empty
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 24 visibleSections EVENTS guest returns empty list`() {
        val sections = visibleSections(Tab.EVENTS, UserStats.guest())
        assertTrue(sections.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 25 — visibleSections INTERNET at skill 3000
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 25 visibleSections INTERNET skill 3000 returns Arena Catalog Profile Qualifications Leaderboard`() {
        val stats = UserStats.guest().copy(currentSkill = 3_000)
        val sections = visibleSections(Tab.INTERNET, stats)
        // Arena(3000✓), Catalog(3000✓), Profile(empty✓), Qualifications(empty✓), Social(10000✗), Leaderboard(3000✓)
        assertEquals(
            listOf(
                DrawerSection.InternetSection.Arena,
                DrawerSection.InternetSection.Catalog,
                DrawerSection.InternetSection.Profile,
                DrawerSection.InternetSection.Qualifications,
                DrawerSection.InternetSection.Leaderboard,
            ),
            sections,
        )
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 26 — visibleSections INTERNET at skill 10000
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 26 visibleSections INTERNET skill 10000 returns all 6 sections`() {
        val stats = UserStats.guest().copy(currentSkill = 10_000)
        val sections = visibleSections(Tab.INTERNET, stats)
        assertEquals(6, sections.size)
        assertEquals(
            listOf(
                DrawerSection.InternetSection.Arena,
                DrawerSection.InternetSection.Catalog,
                DrawerSection.InternetSection.Profile,
                DrawerSection.InternetSection.Qualifications,
                DrawerSection.InternetSection.Social,
                DrawerSection.InternetSection.Leaderboard,
            ),
            sections,
        )
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 27 — visibleSections EVENTS at skill 10000
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 27 visibleSections EVENTS skill 10000 returns Minigames only`() {
        val stats = UserStats.guest().copy(currentSkill = 10_000)
        val sections = visibleSections(Tab.EVENTS, stats)
        // ActiveEvents needs all 4 Qualification roles >= 100 (qualification is still zero())
        // Minigames needs USER >= 10000 → visible
        assertEquals(listOf(DrawerSection.EventsSection.Minigames), sections)
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 28 — visibleSections EVENTS with Qualification
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 28 visibleSections EVENTS with developer at 100 superqual returns ActiveEvents and Minigames`() {
        val stats = UserStats.guest().copy(
            qualification = Qualification(
                tester = 100,
                moderator = 100,
                sponsor = 0,
                translator = 0,
                admin = 100,
                developer = 100,
            ),
            // developer=100 → superqualification bypass; both ActiveEvents and Minigames visible
        )
        val sections = visibleSections(Tab.EVENTS, stats)
        // Superqualification OR-bypass: developer >= LEVEL_1.points overrides ALL requiredRoles
        assertEquals(
            listOf(
                DrawerSection.EventsSection.ActiveEvents,
                DrawerSection.EventsSection.Minigames,
            ),
            sections,
        )
    }


    // -----------------------------------------------------------------------
    // Section Visibility Rules — verify all 12 requiredRoles mappings
    // -----------------------------------------------------------------------

    @Test
    fun `visibility rules row 1 LOCAL MyQuests has empty requiredRoles`() {
        assertTrue(DrawerSection.LocalSection.MyQuests.requiredRoles.isEmpty())
    }

    @Test
    fun `visibility rules row 2 LOCAL HomeQuests has empty requiredRoles`() {
        assertTrue(DrawerSection.LocalSection.HomeQuests.requiredRoles.isEmpty())
    }

    @Test
    fun `visibility rules row 3 LOCAL Settings has empty requiredRoles`() {
        assertTrue(DrawerSection.LocalSection.Settings.requiredRoles.isEmpty())
    }

    @Test
    fun `visibility rules row 4 LOCAL Archive has empty requiredRoles`() {
        assertTrue(DrawerSection.LocalSection.Archive.requiredRoles.isEmpty())
    }

    @Test
    fun `visibility rules LOCAL has exactly 5 sections HomeQuests Archive ReviewQueue MyQuests Settings`() {
        // DesignCatalog removed from LocalSection (spec codex fix #3 — now DrawerFooterAction)
        val sections = visibleSections(Tab.LOCAL, UserStats.guest())
        assertEquals(5, sections.size)
        assertTrue(sections.contains(DrawerSection.LocalSection.HomeQuests))
        assertTrue(sections.contains(DrawerSection.LocalSection.Archive))
        assertTrue(sections.contains(DrawerSection.LocalSection.ReviewQueue))
        assertTrue(sections.contains(DrawerSection.LocalSection.MyQuests))
        assertTrue(sections.contains(DrawerSection.LocalSection.Settings))
    }

    @Test
    fun `visibility rules row 5 INTERNET Arena requires USER at TEACHINGS first 3000`() {
        val roles = DrawerSection.InternetSection.Arena.requiredRoles
        assertEquals(1, roles.size)
        assertEquals(Title.TEACHINGS.first, roles[Role.USER])
    }

    @Test
    fun `visibility rules row 6 INTERNET Catalog requires USER at TEACHINGS first 3000`() {
        val roles = DrawerSection.InternetSection.Catalog.requiredRoles
        assertEquals(1, roles.size)
        assertEquals(Title.TEACHINGS.first, roles[Role.USER])
    }

    @Test
    fun `visibility rules row 7 INTERNET Qualifications has empty requiredRoles`() {
        assertTrue(DrawerSection.InternetSection.Qualifications.requiredRoles.isEmpty())
    }

    @Test
    fun `visibility rules row 8 INTERNET Profile has empty requiredRoles`() {
        assertTrue(DrawerSection.InternetSection.Profile.requiredRoles.isEmpty())
    }

    @Test
    fun `visibility rules row 9 INTERNET Social requires USER at PLAYER first 10000`() {
        val roles = DrawerSection.InternetSection.Social.requiredRoles
        assertEquals(1, roles.size)
        assertEquals(Title.PLAYER.first, roles[Role.USER])
    }

    @Test
    fun `visibility rules row 10 INTERNET Leaderboard requires USER at TEACHINGS first 3000`() {
        val roles = DrawerSection.InternetSection.Leaderboard.requiredRoles
        assertEquals(1, roles.size)
        assertEquals(Title.TEACHINGS.first, roles[Role.USER])
    }

    @Test
    fun `visibility rules row 11 EVENTS ActiveEvents requires TESTER MODERATOR ADMIN DEVELOPER at 100`() {
        val roles = DrawerSection.EventsSection.ActiveEvents.requiredRoles
        assertEquals(4, roles.size)
        assertEquals(100, roles[Role.TESTER])
        assertEquals(100, roles[Role.MODERATOR])
        assertEquals(100, roles[Role.ADMIN])
        assertEquals(100, roles[Role.DEVELOPER])
    }

    @Test
    fun `visibility rules row 12 EVENTS Minigames requires USER at PLAYER first 10000`() {
        val roles = DrawerSection.EventsSection.Minigames.requiredRoles
        assertEquals(1, roles.size)
        assertEquals(Title.PLAYER.first, roles[Role.USER])
    }

    // -----------------------------------------------------------------------
    // visibleSections preserves product order
    // -----------------------------------------------------------------------

    @Test
    fun `visibleSections INTERNET at skill 10000 preserves product order`() {
        val stats = UserStats.guest().copy(currentSkill = 10_000)
        val sections = visibleSections(Tab.INTERNET, stats)
        val expected = listOf(
            DrawerSection.InternetSection.Arena,
            DrawerSection.InternetSection.Catalog,
            DrawerSection.InternetSection.Profile,
            DrawerSection.InternetSection.Qualifications,
            DrawerSection.InternetSection.Social,
            DrawerSection.InternetSection.Leaderboard,
        )
        assertEquals(expected, sections)
    }

    @Test
    fun `visibleSections SHOP always returns empty list`() {
        assertTrue(visibleSections(Tab.SHOP, UserStats.guest()).isEmpty())
        assertTrue(visibleSections(Tab.SHOP, UserStats.guest().copy(currentSkill = 999_999)).isEmpty())
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 44 — rootOf table-driven: 11 section → TabConfig mappings
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 44 rootOf maps each DrawerSection to correct TabConfig member`() {
        assertEquals(LocalConfig.MyQuestsRoot, rootOf(DrawerSection.LocalSection.MyQuests))
        assertEquals(LocalConfig.HomeQuestsRoot, rootOf(DrawerSection.LocalSection.HomeQuests))
        assertEquals(LocalConfig.ArchiveRoot, rootOf(DrawerSection.LocalSection.Archive))
        assertEquals(LocalConfig.ReviewQueueRoot, rootOf(DrawerSection.LocalSection.ReviewQueue))
        assertEquals(LocalConfig.SettingsRoot, rootOf(DrawerSection.LocalSection.Settings))
        assertEquals(InternetConfig.ArenaRoot, rootOf(DrawerSection.InternetSection.Arena))
        assertEquals(InternetConfig.CatalogRoot, rootOf(DrawerSection.InternetSection.Catalog))
        assertEquals(InternetConfig.QualificationsRoot, rootOf(DrawerSection.InternetSection.Qualifications))
        assertEquals(InternetConfig.ProfileRoot, rootOf(DrawerSection.InternetSection.Profile))
        assertEquals(InternetConfig.SocialRoot, rootOf(DrawerSection.InternetSection.Social))
        assertEquals(InternetConfig.LeaderboardRoot, rootOf(DrawerSection.InternetSection.Leaderboard))
        assertEquals(EventsConfig.ActiveEventsRoot, rootOf(DrawerSection.EventsSection.ActiveEvents))
        assertEquals(EventsConfig.MinigamesRoot, rootOf(DrawerSection.EventsSection.Minigames))
    }

    // -----------------------------------------------------------------------
    // Domain Test Scenario 45 — emptyRootFor table-driven: 4 tab → sentinel mappings
    // -----------------------------------------------------------------------

    @Test
    fun `scenario 45 emptyRootFor maps each Tab to correct empty sentinel`() {
        assertEquals(LocalConfig.EmptyRoot, emptyRootFor(Tab.LOCAL))
        assertEquals(InternetConfig.EmptyRoot, emptyRootFor(Tab.INTERNET))
        assertEquals(EventsConfig.EmptyRoot, emptyRootFor(Tab.EVENTS))
        assertEquals(ShopConfig.ShopRoot, emptyRootFor(Tab.SHOP)) // Shop special: no empty state
    }

    // -----------------------------------------------------------------------
    // DM-17 — superqualification bypass: developer=100 → visible regardless of requiredRoles
    // ActiveEvents requires {T=100, M=100, A=100, D=100}; only D is satisfied → bypass
    // -----------------------------------------------------------------------

    @Test
    fun `DM-17 isVisible ActiveEvents developer 100 superqualification bypass returns true`() {
        val stats = UserStats.guest().copy(
            qualification = Qualification.zero().copy(developer = 100),
        )
        assertTrue(isVisible(DrawerSection.EventsSection.ActiveEvents, stats))
    }

    // -----------------------------------------------------------------------
    // DM-18 — all 4 roles satisfied explicitly (no superqual shortcut)
    // -----------------------------------------------------------------------

    @Test
    fun `DM-18 isVisible ActiveEvents all 4 roles at 100 returns true`() {
        val stats = UserStats.guest().copy(
            qualification = Qualification(
                tester = 100,
                moderator = 100,
                sponsor = 0,
                translator = 0,
                admin = 100,
                developer = 100,
            ),
        )
        assertTrue(isVisible(DrawerSection.EventsSection.ActiveEvents, stats))
    }

    // -----------------------------------------------------------------------
    // DM-19 — partial fail: developer=0, moderator=0 → no bypass, not all met → false
    // -----------------------------------------------------------------------

    @Test
    fun `DM-19 isVisible ActiveEvents developer 0 moderator 0 partial fail returns false`() {
        val stats = UserStats.guest().copy(
            qualification = Qualification(
                tester = 100,
                moderator = 0,
                sponsor = 0,
                translator = 0,
                admin = 100,
                developer = 0,
            ),
        )
        assertFalse(isVisible(DrawerSection.EventsSection.ActiveEvents, stats))
    }

    // -----------------------------------------------------------------------
    // DM-20 — superqual bypasses section that has NO DEVELOPER in requiredRoles
    // Arena requires only Role.USER >= 3000; developer not in requiredRoles.
    // With developer=100 and currentSkill=0 (USER requirement not met), bypass applies → true
    // -----------------------------------------------------------------------

    @Test
    fun `DM-20 isVisible Arena developer 100 superqual bypasses section without D requirement`() {
        val stats = UserStats.guest().copy(
            qualification = Qualification.zero().copy(developer = 100),
            currentSkill = 0,
        )
        assertTrue(isVisible(DrawerSection.InternetSection.Arena, stats))
    }

    // -----------------------------------------------------------------------
    // DM-21 — no superqual, single role satisfied normally → true
    // -----------------------------------------------------------------------

    @Test
    fun `DM-21 isVisible Arena developer 0 currentSkill 3000 single role satisfied returns true`() {
        val stats = UserStats.guest().copy(
            currentSkill = 3_000,
        )
        assertTrue(isVisible(DrawerSection.InternetSection.Arena, stats))
    }

    // -----------------------------------------------------------------------
    // DM-22 — no superqual, single role NOT met → false
    // -----------------------------------------------------------------------

    @Test
    fun `DM-22 isVisible Arena developer 0 currentSkill 2999 single role not met returns false`() {
        val stats = UserStats.guest().copy(
            currentSkill = 2_999,
        )
        assertFalse(isVisible(DrawerSection.InternetSection.Arena, stats))
    }

    // -----------------------------------------------------------------------
    // DM-23 — empty requiredRoles always returns true (aligns with scenario 35)
    // -----------------------------------------------------------------------

    @Test
    fun `DM-23 isVisible empty requiredRoles developer 0 always returns true`() {
        assertTrue(isVisible(DrawerSection.LocalSection.MyQuests, UserStats.guest()))
    }

    // -----------------------------------------------------------------------
    // DM-24 — debug build + developer=0 → [DesignCatalog, SyncNow, About] (debug bypass)
    // -----------------------------------------------------------------------

    @Test
    fun `DM-24 visibleFooterActions debug build developer 0 returns DesignCatalog SyncNow About`() {
        val stats = UserStats.guest()
        val actions = visibleFooterActions(isDebugBuild = true, stats = stats)
        assertEquals(3, actions.size)
        assertEquals(DrawerFooterAction.DesignCatalog, actions[0])
        assertEquals(DrawerFooterAction.SyncNow, actions[1])
        assertEquals(DrawerFooterAction.About, actions[2])
    }

    // -----------------------------------------------------------------------
    // DM-25 — release build + developer=0 → [About] only (no dev tools leaked)
    // -----------------------------------------------------------------------

    @Test
    fun `DM-25 visibleFooterActions release build developer 0 returns About only`() {
        val stats = UserStats.guest()
        val actions = visibleFooterActions(isDebugBuild = false, stats = stats)
        assertEquals(1, actions.size)
        assertEquals(DrawerFooterAction.About, actions[0])
    }

    // -----------------------------------------------------------------------
    // DM-26 — release build + developer=100 → [DesignCatalog, SyncNow, About]
    // -----------------------------------------------------------------------

    @Test
    fun `DM-26 visibleFooterActions release build developer 100 returns DesignCatalog SyncNow About`() {
        val stats = UserStats.guest().copy(
            qualification = Qualification.zero().copy(developer = 100),
        )
        val actions = visibleFooterActions(isDebugBuild = false, stats = stats)
        assertEquals(3, actions.size)
        assertEquals(DrawerFooterAction.DesignCatalog, actions[0])
        assertEquals(DrawerFooterAction.SyncNow, actions[1])
        assertEquals(DrawerFooterAction.About, actions[2])
    }

    // -----------------------------------------------------------------------
    // DM-27 — release build + developer=99 → [About] only (below threshold boundary)
    // -----------------------------------------------------------------------

    @Test
    fun `DM-27 visibleFooterActions release build developer 99 below threshold returns About only`() {
        val stats = UserStats.guest().copy(
            qualification = Qualification.zero().copy(developer = 99),
        )
        val actions = visibleFooterActions(isDebugBuild = false, stats = stats)
        assertEquals(1, actions.size)
        assertEquals(DrawerFooterAction.About, actions[0])
    }
}
