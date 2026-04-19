package com.tpov.common.domain.repository

import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.model.LockServerResult

interface RepositorySettingServer {

    fun isLockServer(event: EventQuiz): LockServerResult
    fun lockStructureData(event: EventQuiz): LockServerResult
    fun unlockStructureData(event: EventQuiz): LockServerResult
}
