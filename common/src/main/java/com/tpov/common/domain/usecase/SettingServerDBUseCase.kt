package com.tpov.common.domain.usecase

import com.tpov.common.domain.repository.RepositorySettingServer
import javax.inject.Inject

class SettingServerDBUseCase @Inject constructor(private val repositorySettingServer: RepositorySettingServer) {

    fun lockStructureData() = repositorySettingServer.lockStructureData()
    fun unlockStructureData() = repositorySettingServer.unlockStructureData()

    fun isLockServer() = repositorySettingServer.isLockServer()
}