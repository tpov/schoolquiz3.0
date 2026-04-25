package com.tpov.schoolquiz.shared.core.sync.fake

import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAuthRepository(initialUid: String? = null) : AuthRepository {

    private val _uid = MutableStateFlow(initialUid)

    override suspend fun currentUid(): String? = _uid.value
    override fun observeUid(): Flow<String?> = _uid.asStateFlow()

    fun signIn(uid: String) {
        require(uid.isNotBlank())
        _uid.value = uid
    }

    fun signOut() { _uid.value = null }
}
