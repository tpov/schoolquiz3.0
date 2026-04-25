package com.tpov.schoolquiz.shared.feature.app_shell.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Domain boundary for accessing the authenticated user's identity.
 *
 * Production implementation wraps the platform auth SDK (Firebase Auth on Android).
 * Tests use [FakeAuthRepository] (in-memory fake, lives in test source set).
 *
 * Domain rule: signatures use only domain types — no SDK / platform classes.
 * Domain rule: no DI annotations — DI wiring is data-layer concern.
 *
 * Used by:
 * - `MyQuestsViewModel` — to load only the current user's quests via `QuestRepository.observeMyQuests(uid)`
 * - Future create-quest feature — to set `Quest.authorUid` on creation
 * - Future profile / sign-in features — to react to auth state changes
 *
 * Spec: home-and-my-quests `0-spec.md` Decision #42 (uid acquisition path).
 */
interface AuthRepository {

    /**
     * One-shot snapshot of the current authenticated user's UID.
     *
     * Returns `null` if the user is not authenticated (guest state).
     * Returns the Firebase Auth UID (stable, opaque string) otherwise.
     *
     * Domain invariant: returned string (when non-null) is non-blank.
     */
    suspend fun currentUid(): String?

    /**
     * Continuously observes the authenticated user's UID.
     *
     * Emits a new value whenever the auth state changes:
     * - Sign in: emits the new UID
     * - Sign out: emits `null`
     * - Token refresh (no UID change): no emission
     *
     * The first emission represents the current state (may be `null`).
     */
    fun observeUid(): Flow<String?>
}
