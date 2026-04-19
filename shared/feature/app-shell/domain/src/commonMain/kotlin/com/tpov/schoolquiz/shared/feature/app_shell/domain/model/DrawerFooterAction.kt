package com.tpov.schoolquiz.shared.feature.app_shell.domain.model

/**
 * Footer actions shown at the bottom of the navigation drawer.
 *
 * These are not [DrawerSection]s — they do not own a navigation stack and are not
 * subject to progressive unlock (no [requiredRoles]).
 *
 * Visibility of footer actions is controlled by build-type (see [visibleFooterActions]).
 *
 * Spec codex fix #3: DesignCatalog removed from [DrawerSection.LocalSection] and promoted
 * to a footer action to clarify its semantics as a dev tool, not a user-facing section.
 */
sealed interface DrawerFooterAction {
    /** Opens the internal design catalog. Visible in debug builds only. */
    data object DesignCatalog : DrawerFooterAction

    /** Opens the About / credits screen. Always visible. */
    data object About : DrawerFooterAction
}
