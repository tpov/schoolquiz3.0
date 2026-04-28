package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.fake

import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthRepository : AuthRepository {
    var uid: String? = "user1"
    var callCount = 0
        private set

    override suspend fun currentUid(): String? {
        callCount++
        return uid
    }

    override fun observeUid(): Flow<String?> = MutableStateFlow(uid)
}
