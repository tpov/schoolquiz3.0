package com.tpov.schoolquiz.shared.feature.app_shell.domain.model

/**
 * Common supertype for all per-tab config sealed hierarchies.
 *
 * Allows functions like [emptyRootFor] and [rootOf] to return a unified type
 * without losing per-tab type safety at the call sites that know the tab.
 */
sealed interface TabConfig

/**
 * Per-tab config sealed hierarchies.
 * These represent the "screens" within each tab's navigation stack.
 *
 * Note: @Serializable annotations are intentionally absent — serialization is a data-layer concern.
 * Design phase will add kotlinx-serialization in the Decompose integration layer.
 *
 * [EmptyRoot] sentinel: rendered when no visible sections exist for the tab (spec FR #7 / BR #3).
 * EVENTS now has public tournament sections, so ordinary users land on the qualifier root.
 */

sealed interface LocalConfig : TabConfig {
    data object MyQuestsRoot : LocalConfig
    data object HomeQuestsRoot : LocalConfig
    data object ArchiveRoot : LocalConfig
    data object ReviewQueueRoot : LocalConfig
    data object SettingsRoot : LocalConfig
    /** Always exists as type; rendered as UnderConstructionScreen("Недоступно") in release. */
    data object DesignCatalogRoot : LocalConfig
    /** Sentinel shown when no visible sections exist for this tab. */
    data object EmptyRoot : LocalConfig

    /**
     * Create-quest screen (FAB destination on "Мои квесты").
     * Pushed onto LOCAL stack on top of [MyQuestsRoot].
     * Phase-01 implementation: rendered as UnderConstructionScreen("Создание квеста в разработке").
     * Spec: home-and-my-quests `0-spec.md` Decision #41.
     */
    data object QuestCreateRoot : LocalConfig
}

sealed interface InternetConfig : TabConfig {
    data object ArenaRoot : InternetConfig
    data object CatalogRoot : InternetConfig
    data object QualificationsRoot : InternetConfig
    data object ProfileRoot : InternetConfig
    data object SocialRoot : InternetConfig
    data object LeaderboardRoot : InternetConfig
    /** Sentinel shown when no visible sections exist for this tab. */
    data object EmptyRoot : InternetConfig
}

sealed interface EventsConfig : TabConfig {
    data object QualifierTournamentRoot : EventsConfig
    data object WorldChampionshipRoot : EventsConfig
    data object ActiveEventsRoot : EventsConfig
    data object MinigamesRoot : EventsConfig
    /** Sentinel shown when no visible sections exist for this tab. */
    data object EmptyRoot : EventsConfig
}

sealed interface ShopConfig : TabConfig {
    /** The single element in Shop tab — Shop has no navigation stack. */
    data object ShopRoot : ShopConfig
}
