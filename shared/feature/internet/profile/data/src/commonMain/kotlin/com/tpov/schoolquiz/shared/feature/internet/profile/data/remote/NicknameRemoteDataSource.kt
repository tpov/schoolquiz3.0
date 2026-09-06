package com.tpov.schoolquiz.shared.feature.internet.profile.data.remote

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameAvailability
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnedNickname

/**
 * The platform side of [com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.NicknameRepository].
 *
 * Taking a lot down is not here on purpose: it travels through the shared queue and its single
 * receiver (AD-6), so a direct callable for it would be a second transport around the idempotency
 * check.
 */
interface NicknameRemoteDataSource {
    suspend fun checkAvailability(nickname: String): NicknameAvailability

    suspend fun owned(): List<OwnedNickname>

    suspend fun setActive(nickname: String)

    suspend fun claim(nickname: String): Long

    suspend fun listings(limit: Int): List<NicknameListing>

    suspend fun listForSale(
        nickname: String,
        price: Long,
    )

    suspend fun buy(nickname: String): Long
}
