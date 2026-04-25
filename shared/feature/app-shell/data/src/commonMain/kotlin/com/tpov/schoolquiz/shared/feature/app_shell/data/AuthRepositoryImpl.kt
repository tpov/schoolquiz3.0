package com.tpov.schoolquiz.shared.feature.app_shell.data

import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Production implementation of [AuthRepository] — Decision #42 (home-and-my-quests).
 *
 * Wraps the `currentUidFlow` lambda passed via DI. In production
 * (`AppApplication.kt`) this lambda returns a **shared hot Flow** (`shareIn` with
 * `WhileSubscribed` and replay=1), so all consumers — [UserStatsRepositoryImpl],
 * [AuthRepositoryImpl], future `MyQuestsViewModel` — share a single underlying
 * `FirebaseAuth.AuthStateListener` regardless of how many parallel collectors exist.
 * Codex Round 4 N2: this is the corrected design; cold flow per-collect would
 * register N listeners.
 *
 * Returns the **real** Firebase Auth UID (or `null` for unauthenticated guests),
 * with no synthetic substitution. A `null` uid means no active session.
 */
class AuthRepositoryImpl(
    private val currentUidFlow: () -> Flow<String?>,
) : AuthRepository {

    override suspend fun currentUid(): String? = currentUidFlow().first()

    override fun observeUid(): Flow<String?> = currentUidFlow()
}
