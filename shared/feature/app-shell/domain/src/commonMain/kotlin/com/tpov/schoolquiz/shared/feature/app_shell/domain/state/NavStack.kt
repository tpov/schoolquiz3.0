package com.tpov.schoolquiz.shared.feature.app_shell.domain.state

/**
 * Pure domain navigation stack container.
 *
 * Replaces Decompose ChildStack for domain-layer purposes.
 * Decompose ChildStack is a framework type — it does not belong in domain.
 *
 * Invariants:
 * - [active] is always the current top screen (never absent).
 * - [backStack] contains screens under [active]; does NOT contain [active].
 * - [backStack].isEmpty() <=> we are on the root screen of the current section.
 */
data class NavStack<C>(
    val active: C,
    val backStack: List<C> = emptyList(),
) {
    /** True when there is nothing to pop — already at root. */
    val isAtRoot: Boolean get() = backStack.isEmpty()

    /**
     * Push a new config onto the stack.
     * [active] becomes the head of [backStack]; [next] becomes the new [active].
     */
    fun push(next: C): NavStack<C> = NavStack(
        active = next,
        backStack = backStack + active,
    )

    /**
     * Pop the top item.
     * Returns null if already at root (nothing to pop).
     */
    fun pop(): NavStack<C>? =
        if (backStack.isEmpty()) null
        else NavStack(
            active = backStack.last(),
            backStack = backStack.dropLast(1),
        )

    /**
     * Replace entire stack with a single root config.
     * Result: active=[root], backStack=[].
     */
    fun replaceWithRoot(root: C): NavStack<C> = NavStack(active = root)
}
