package com.tpov.common.domain.repository

import com.tpov.common.domain.model.LockServerResult

interface RepositorySettingLocal {
    fun lockStructureData(): LockServerResult
    fun unlockStructureData(): LockServerResult

    fun isLockServer(): LockServerResult
}