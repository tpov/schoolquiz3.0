package com.tpov.schoolquiz.android.feature.quest.presentation.fake

import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAuthRepository(initialUid: String? = null) : AuthRepository {

    private val uidFlow = MutableStateFlow<String?>(initialUid)

    override suspend fun currentUid(): String? = uidFlow.value

    override fun observeUid(): Flow<String?> = uidFlow.asStateFlow()

    fun setUid(uid: String?) {
        uidFlow.value = uid
    }
}
