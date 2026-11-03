package com.tpov.common.domain.usecase

import com.tpov.common.domain.model.EventQuiz
import com.tpov.common.domain.repository.RepositorySettingLocal
import javax.inject.Inject

class SettingLocalDBUseCase @Inject constructor(private val repositorySettingLocal: RepositorySettingLocal) {

    fun rollbackStructureData(event: EventQuiz) {

    }
}
