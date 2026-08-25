package com.tpov.schoolquiz.shared.feature.internet.profile.data

import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.LogoRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileLogo
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.LogoRepository

/** Straight through: the eight logos and what the account owns are both the server's to say. */
class LogoRepositoryImpl(
    private val remote: LogoRemoteDataSource,
) : LogoRepository {
    override suspend fun catalog(): List<ProfileLogo> = remote.catalog()

    override suspend fun buy(logo: String): Long = remote.buy(logo)
}
