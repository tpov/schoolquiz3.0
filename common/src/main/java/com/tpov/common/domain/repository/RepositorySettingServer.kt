package com.tpov.common.domain.repository

import com.tpov.common.domain.model.LockServerResult

interface RepositorySettingServer {
    fun lockStructureData(): LockServerResult
    fun unlockStructureData(): LockServerResult

    fun isLockServer(): LockServerResult
}