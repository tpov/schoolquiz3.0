package com.tpov.common.domain.usecase

import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.model.LockServerResult
import com.tpov.common.domain.repository.RepositorySettingServer
import javax.inject.Inject

class SettingServerDBUseCase @Inject constructor(private val repositorySettingServer: RepositorySettingServer) {

    fun lockStructureData(event: EventQuiz): LockServerResult {
        if (event == EventQuiz.QUIZ_BY_USER) return LockServerResult.Success
        return repositorySettingServer.lockStructureData(event)
    }

    fun unlockStructureData(event: EventQuiz): LockServerResult {
        if (event == EventQuiz.QUIZ_BY_USER) return LockServerResult.Success
        return repositorySettingServer.unlockStructureData(event)
    }

    fun isLockServer(event: EventQuiz): LockServerResult {
        if (event == EventQuiz.QUIZ_BY_USER) return LockServerResult.Success
        return repositorySettingServer.isLockServer(event)
    }
}
