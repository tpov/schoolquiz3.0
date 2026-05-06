package com.tpov.schoolquiz.shared.feature.app_shell.domain.model

import com.tpov.schoolquiz.shared.core.foundation.QualificationLevel

/**
 * Sealed hierarchy for per-tab drawer sections.
 * Each concrete type knows which Tab it belongs to via [tab]
 * and declares its visibility requirements via [requiredRoles].
 *
 * Domain rule: Tab.SHOP has no DrawerSection — activeSection is always null for Shop.
 *
 * Domain rule (FR #20, Business Rule #16):
 * A section is visible iff all entries in [requiredRoles] are satisfied:
 *   requiredRoles.all { (role, minLevel) -> actualLevel(role, stats) >= minLevel }
 * Empty map → always visible.
 *
 * Thresholds are sourced from the Section Visibility Rules table (State Matrix).
 * Legacy origin: MenuList.kt + TitleUserValue.kt.
 */
sealed interface DrawerSection {
    val tab: Tab
    val requiredRoles: Map<Role, Int>

    /**
     * Sections for the LOCAL tab.
     *
     * Note: DesignCatalog is no longer a DrawerSection (removed per spec codex fix #3).
     * It is now a [DrawerFooterAction.DesignCatalog] — a footer action, not a nav section.
     * [LocalConfig.DesignCatalogRoot] remains as the target navigation config for OpenDesignCatalog.
     */
    sealed interface LocalSection : DrawerSection {
        override val tab: Tab get() = Tab.LOCAL

        // emptyMap (always visible) — visibleSections output order: HomeQuests, MyQuests, Settings
        data object MyQuests : LocalSection {
            override val requiredRoles: Map<Role, Int> get() = emptyMap()
        }

        // emptyMap (always visible)
        data object HomeQuests : LocalSection {
            override val requiredRoles: Map<Role, Int> get() = emptyMap()
        }

        // emptyMap (always visible)
        data object Archive : LocalSection {
            override val requiredRoles: Map<Role, Int> get() = emptyMap()
        }

        data object ReviewQueue : LocalSection {
            override val requiredRoles: Map<Role, Int> get() = emptyMap()
        }

        // emptyMap (always visible)
        data object Settings : LocalSection {
            override val requiredRoles: Map<Role, Int> get() = emptyMap()
        }
    }

    /**
     * Sections for the INTERNET tab.
     * Declaration order matches visibleSections output order (spec requirement).
     */
    sealed interface InternetSection : DrawerSection {
        override val tab: Tab get() = Tab.INTERNET

        // Row 5 — requires USER >= TEACHINGS.first (3000)
        data object Arena : InternetSection {
            override val requiredRoles: Map<Role, Int>
                get() = mapOf(Role.USER to Title.TEACHINGS.first)
        }

        // Row 6 — requires USER >= TEACHINGS.first (3000)
        data object Catalog : InternetSection {
            override val requiredRoles: Map<Role, Int>
                get() = mapOf(Role.USER to Title.TEACHINGS.first)
        }

        // Row 8 — emptyMap (always visible)
        data object Profile : InternetSection {
            override val requiredRoles: Map<Role, Int> get() = emptyMap()
        }

        // Row 7 — emptyMap (always visible)
        data object Qualifications : InternetSection {
            override val requiredRoles: Map<Role, Int> get() = emptyMap()
        }

        // Row 9 — requires USER >= PLAYER.first (10000)
        data object Social : InternetSection {
            override val requiredRoles: Map<Role, Int>
                get() = mapOf(Role.USER to Title.PLAYER.first)
        }

        // Row 10 — requires USER >= TEACHINGS.first (3000)
        data object Leaderboard : InternetSection {
            override val requiredRoles: Map<Role, Int>
                get() = mapOf(Role.USER to Title.TEACHINGS.first)
        }
    }

    /**
     * Sections for the EVENTS tab.
     */
    sealed interface EventsSection : DrawerSection {
        override val tab: Tab get() = Tab.EVENTS

        // Public event — every user can enter the qualifier.
        data object QualifierTournament : EventsSection {
            override val requiredRoles: Map<Role, Int> get() = emptyMap()
        }

        // Public entry point; final eligibility is enforced by tournament server state.
        data object WorldChampionship : EventsSection {
            override val requiredRoles: Map<Role, Int> get() = emptyMap()
        }

        // Row 11 — requires TESTER AND MODERATOR AND ADMIN AND DEVELOPER all >= QualificationLevel.LEVEL_1.points
        data object ActiveEvents : EventsSection {
            override val requiredRoles: Map<Role, Int>
                get() = mapOf(
                    Role.TESTER to QualificationLevel.LEVEL_1.points,
                    Role.MODERATOR to QualificationLevel.LEVEL_1.points,
                    Role.ADMIN to QualificationLevel.LEVEL_1.points,
                    Role.DEVELOPER to QualificationLevel.LEVEL_1.points,
                )
        }

        // Row 12 — requires USER >= PLAYER.first (10000)
        data object Minigames : EventsSection {
            override val requiredRoles: Map<Role, Int>
                get() = mapOf(Role.USER to Title.PLAYER.first)
        }
    }
}
