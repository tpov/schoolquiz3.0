package com.tpov.schoolquiz.shared.feature.internet.profile.data

import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.LogoRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.LogoListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileLogo
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.LogoRepository

/** Straight through: the eight logos and what the account owns are both the server's to say. */
class LogoRepositoryImpl(
    private val remote: LogoRemoteDataSource,
) : LogoRepository {
    override suspend fun catalog(): List<ProfileLogo> = remote.catalog()

    override suspend fun buy(logo: String): Long = remote.buy(logo)

    override suspend fun wear(logo: String): String = remote.wear(logo)

    override suspend fun listings(limit: Int): List<LogoListing> = remote.listings(limit)

    override suspend fun listForSale(logo: String, price: Long) = remote.listForSale(logo, price)

    override suspend fun cancelListing(logo: String) = remote.cancelListing(logo)

    override suspend fun buyListed(logo: String): Long = remote.buyListed(logo)
}
