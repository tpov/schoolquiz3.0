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
}
