package com.tpov.schoolquiz.shared.feature.app_shell.domain.state

import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.DrawerSection

/**
 * Per-tab state: active drawer section + navigation stack.
 *
 * Domain rule: for Tab.SHOP, [activeSection] is always null.
 * Preserved during tab switch so returning to a tab restores the exact state.
 */
data class TabState<C>(
    val activeSection: DrawerSection?,
    val stack: NavStack<C>,
)
