package com.tpov.schoolquiz.shared.feature.app_shell.domain.logic

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerFooterAction
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.EventsConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.InternetConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.LocalConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Role
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.ShopConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Tab
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.TabConfig
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats

/**
 * Pure visibility functions for progressive section unlock (FR #20).
 *
 * Business Rule #16: AND semantics — section is visible iff ALL requiredRoles entries satisfied.
 * Business Rule #17: Hidden sections are not rendered (no lock icon, no disabled state).
 * Business Rule #18: Bottom-tabs are always visible; unlock applies only to DrawerSection.
 */

/**
 * Returns the current level the user holds for [role].
 *
 * Mapping (spec FR #20 / Terms / actualLevel):
 * - Role.USER       → stats.currentSkill
 * - Role.TESTER     → stats.qualification.tester
 * - Role.MODERATOR  → stats.qualification.moderator
 * - Role.SPONSOR    → stats.qualification.sponsor
 * - Role.TRANSLATOR → stats.qualification.translator
 * - Role.ADMIN      → stats.qualification.admin
 * - Role.DEVELOPER  → stats.qualification.developer
 */
fun actualLevel(role: Role, stats: UserStats): Int = when (role) {
    Role.USER -> stats.currentSkill
    Role.TESTER -> stats.qualification.tester
    Role.MODERATOR -> stats.qualification.moderator
    Role.SPONSOR -> stats.qualification.sponsor
    Role.TRANSLATOR -> stats.qualification.translator
    Role.ADMIN -> stats.qualification.admin
    Role.DEVELOPER -> stats.qualification.developer
}

/**
 * Returns true if [section] is visible for the given [stats].
 *
 * Business Rule #16: isVisible = requiredRoles.all { (role, minLevel) -> actualLevel(role, stats) >= minLevel }
 * Domain Test Scenario 35: empty requiredRoles → all() on empty collection = true → always visible.
 */
fun isVisible(section: DrawerSection, stats: UserStats): Boolean =
    section.requiredRoles.all { (role, minLevel) ->
        actualLevel(role, stats) >= minLevel
    }

/**
 * Returns the ordered list of visible [DrawerSection]s for [tab] given [stats].
 *
 * Output order preserves the declaration order within each sealed interface,
 * consistent with the Section Visibility Rules table order.
 *
 * Business Rule #17: only visible sections are returned; callers render exactly this list.
 * Business Rule #18: bottom-tabs themselves are NOT filtered here — call site decides tab visibility.
 *
 * Note: [DrawerFooterAction.DesignCatalog] is NOT a DrawerSection and is NOT included here.
 * Use [visibleFooterActions] for footer action visibility.
 */
fun visibleSections(tab: Tab, stats: UserStats): List<DrawerSection> = when (tab) {
    Tab.LOCAL -> listOf(
        DrawerSection.LocalSection.MyQuests,
        DrawerSection.LocalSection.MyCourses,
        DrawerSection.LocalSection.Settings,
    ).filter { isVisible(it, stats) }

    Tab.INTERNET -> listOf(
        DrawerSection.InternetSection.Arena,
        DrawerSection.InternetSection.Catalog,
        DrawerSection.InternetSection.Qualifications,
        DrawerSection.InternetSection.Profile,
        DrawerSection.InternetSection.Social,
        DrawerSection.InternetSection.Leaderboard,
    ).filter { isVisible(it, stats) }

    Tab.EVENTS -> listOf(
        DrawerSection.EventsSection.ActiveEvents,
        DrawerSection.EventsSection.Minigames,
    ).filter { isVisible(it, stats) }

    Tab.SHOP -> emptyList()
}

/**
 * Returns the default [DrawerSection] for [tab] given [stats].
 *
 * Spec FR #7 / BR #3: defaultSection = visibleSections(tab, stats).firstOrNull()
 * Returns null if no sections are visible for the tab (e.g. guest on EVENTS tab).
 * In this case the caller must use [emptyRootFor] as the navigation target.
 */
fun defaultSection(tab: Tab, stats: UserStats): DrawerSection? =
    visibleSections(tab, stats).firstOrNull()

/**
 * Returns the root [TabConfig] for the given [DrawerSection].
 *
 * Used by [initialTabState] to map the default visible section to its root navigation config.
 */
fun rootOf(section: DrawerSection): TabConfig = when (section) {
    DrawerSection.LocalSection.MyQuests -> LocalConfig.MyQuestsRoot
    DrawerSection.LocalSection.MyCourses -> LocalConfig.MyCoursesRoot
    DrawerSection.LocalSection.Settings -> LocalConfig.SettingsRoot
    DrawerSection.InternetSection.Arena -> InternetConfig.ArenaRoot
    DrawerSection.InternetSection.Catalog -> InternetConfig.CatalogRoot
    DrawerSection.InternetSection.Qualifications -> InternetConfig.QualificationsRoot
    DrawerSection.InternetSection.Profile -> InternetConfig.ProfileRoot
    DrawerSection.InternetSection.Social -> InternetConfig.SocialRoot
    DrawerSection.InternetSection.Leaderboard -> InternetConfig.LeaderboardRoot
    DrawerSection.EventsSection.ActiveEvents -> EventsConfig.ActiveEventsRoot
    DrawerSection.EventsSection.Minigames -> EventsConfig.MinigamesRoot
}

/**
 * Returns the empty-state sentinel [TabConfig] for the given [tab].
 *
 * Used when [defaultSection] returns null (no visible sections) to provide
 * a valid non-null navigation target.
 *
 * SHOP has no empty state — ShopRoot is always valid.
 */
fun emptyRootFor(tab: Tab): TabConfig = when (tab) {
    Tab.LOCAL -> LocalConfig.EmptyRoot
    Tab.INTERNET -> InternetConfig.EmptyRoot
    Tab.EVENTS -> EventsConfig.EmptyRoot
    Tab.SHOP -> ShopConfig.ShopRoot
}

/**
 * Returns the list of [DrawerFooterAction]s visible for the current build type.
 *
 * Domain Test Scenarios 42-43:
 * - Debug build: [DesignCatalog, About]
 * - Release build: [About]
 */
fun visibleFooterActions(isDebugBuild: Boolean): List<DrawerFooterAction> =
    if (isDebugBuild) listOf(DrawerFooterAction.DesignCatalog, DrawerFooterAction.About)
    else listOf(DrawerFooterAction.About)
