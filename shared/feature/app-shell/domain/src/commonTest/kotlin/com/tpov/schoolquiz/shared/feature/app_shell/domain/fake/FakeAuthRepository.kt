package com.tpov.schoolquiz.shared.feature.app_shell.domain.fake

import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake implementation of [AuthRepository] for use case testing.
 *
 * Backed by [MutableStateFlow] — tests can push new UID values (sign in / sign out)
 * and verify that downstream consumers react correctly.
 *
 * This is a full fake (not a mock): it implements the interface contract completely
 * without any test framework dependencies.
 */
class FakeAuthRepository(
    initialUid: String? = null,
) : AuthRepository {

    private val _uid = MutableStateFlow(initialUid)

    override suspend fun currentUid(): String? = _uid.value

    override fun observeUid(): Flow<String?> = _uid.asStateFlow()

    // ----- Test helpers -----

    /** Simulates user sign-in with the given non-blank uid. */
    fun signIn(uid: String) {
        require(uid.isNotBlank()) { "uid must be non-blank, was: '$uid'" }
        _uid.value = uid
    }

    /** Simulates user sign-out — emits null. */
    fun signOut() {
        _uid.value = null
    }
}
