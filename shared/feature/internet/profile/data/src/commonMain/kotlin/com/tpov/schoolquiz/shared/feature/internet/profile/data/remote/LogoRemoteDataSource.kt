package com.tpov.schoolquiz.shared.feature.internet.profile.data.remote

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileLogo

interface LogoRemoteDataSource {
    suspend fun catalog(): List<ProfileLogo>

    suspend fun buy(logo: String): Long
}
