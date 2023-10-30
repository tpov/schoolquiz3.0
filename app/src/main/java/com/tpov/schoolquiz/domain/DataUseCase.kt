package com.tpov.schoolquiz.domain

import com.tpov.schoolquiz.domain.repository.RepositoryData
import javax.inject.Inject

class DataUseCase @Inject constructor(private val repositoryData: RepositoryData) {

    fun getTpovIdByEmail(email: String) = repositoryData.getTpovIdByEmail(email)

    fun downloadTpovId() = repositoryData.downloadTpovId()

}