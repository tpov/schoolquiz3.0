package com.tpov.schoolquiz.shared.feature.app_shell.domain.model

/**
 * Badge content for NavigationBarItem and NavigationDrawerItem.
 *
 * Domain rule: badges API is future-proof; all values are null in MVP.
 * UI renders badge only when non-null.
 */
sealed interface BadgeContent {
    /** Numeric badge, e.g. unread message count. */
    data class Count(val value: Int) : BadgeContent

    /** Text badge, e.g. "NEW". */
    data class Text(val value: String) : BadgeContent
}
