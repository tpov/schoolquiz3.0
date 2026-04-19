package com.tpov.schoolquiz.shared.feature.app_shell.domain.model

/**
 * Outcome returned by onActiveTabRetap().
 *
 * Domain rule:
 * - POP_TO_ROOT: backStack was not empty; domain cleared it, active is now the root config.
 * - NO_OP:       backStack was already empty; domain state unchanged.
 *
 * UI consumes this to decide whether to trigger scroll-to-top (NO_OP case).
 */
enum class RetapOutcome {
    POP_TO_ROOT,
    NO_OP,
}
