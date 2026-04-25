package com.tpov.schoolquiz.shared.feature.app_shell.domain.model

/**
 * Sealed destination type for the Navigator API.
 *
 * Every user/system navigation action is expressed as one of these destinations.
 * The single Navigator method is: goTo(destination: Destination).
 */
sealed interface Destination {
    /** Navigate back according to the back-policy FSM. */
    data object Back : Destination

    /** Switch to the given tab, preserving all TabStates. */
    data class SwitchTab(val tab: Tab) : Destination

    /**
     * Select a drawer section.
     * Cross-tab: if section.tab != activeTab, auto-switches tab first.
     */
    data class SelectSection(val section: DrawerSection) : Destination

    /** Open the side drawer. No-op if activeTab == SHOP. */
    data object OpenDrawer : Destination

    /** Close the side drawer. */
    data object CloseDrawer : Destination

    /**
     * Open the design catalog dev tool.
     * Result: activeTab=LOCAL, activeSection=null, stack.active=LocalConfig.DesignCatalogRoot,
     * backStack=[], isDrawerOpen=false.
     * Visible only in debug builds (controlled by [visibleFooterActions]).
     */
    data object OpenDesignCatalog : Destination

    /**
     * Open the create-quest screen (FAB on "Мои квесты").
     *
     * Result: pushes [LocalConfig.QuestCreateRoot] onto the LOCAL tab's stack,
     * preserving [LocalConfig.MyQuestsRoot] in backStack so Back returns to "Мои квесты".
     *
     * Differs from [OpenDesignCatalog] (which replaces the entire LOCAL stack) — this is a
     * push, not a replace. See spec: home-and-my-quests `0-spec.md` Decision #41 + #45.
     *
     * Re-tap guard (Decision #47): if `localState.stack.active == LocalConfig.QuestCreateRoot`
     * already — no-op (avoids duplicate entries in backStack).
     *
     * Atomic constraint (Decision #48): adding this case forces an exhaustive when in
     * [com.tpov.schoolquiz.shared.feature.app_shell.domain.logic.navigate]. Both files
     * are modified together.
     */
    data object OpenQuestCreate : Destination
}
