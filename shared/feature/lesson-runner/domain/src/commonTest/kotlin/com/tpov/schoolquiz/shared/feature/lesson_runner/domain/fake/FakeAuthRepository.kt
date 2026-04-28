package com.tpov.schoolquiz.shared.feature.lesson_runner.domain.fake

import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthRepository(private var uid: String? = "test-user-uid") : AuthRepository {

    private val uidFlow = MutableStateFlow(uid)

    override suspend fun currentUid(): String? = uid

    override fun observeUid(): Flow<String?> = uidFlow

    fun setUid(newUid: String?) {
        uid = newUid
        uidFlow.value = newUid
    }
}
