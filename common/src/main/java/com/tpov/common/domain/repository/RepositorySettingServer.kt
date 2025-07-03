package com.tpov.common.domain.repository

import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.model.LockServerResult

interface RepositorySettingServer {

    fun isLockServer(event: EventQuiz): LockServerResult
}
