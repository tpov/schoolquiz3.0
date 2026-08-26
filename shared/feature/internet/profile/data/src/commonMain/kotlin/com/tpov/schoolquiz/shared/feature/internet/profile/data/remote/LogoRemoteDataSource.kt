package com.tpov.schoolquiz.shared.feature.internet.profile.data.remote

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.LogoListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileLogo

interface LogoRemoteDataSource {
    suspend fun catalog(): List<ProfileLogo>

    suspend fun buy(logo: String): Long

    suspend fun wear(logo: String): String

    suspend fun listings(limit: Int): List<LogoListing>

    suspend fun listForSale(logo: String, price: Long)

    suspend fun cancelListing(logo: String)

    suspend fun buyListed(logo: String): Long
}
