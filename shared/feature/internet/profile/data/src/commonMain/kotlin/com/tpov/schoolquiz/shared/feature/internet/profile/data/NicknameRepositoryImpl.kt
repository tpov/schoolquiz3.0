package com.tpov.schoolquiz.shared.feature.internet.profile.data

import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.NicknameRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameAvailability
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnedNickname
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.NicknameRepository

/**
 * Straight pass-through, and deliberately so: nothing here may be cached.
 *
 * Ownership and prices change from other accounts, and a stale answer would let somebody act on a
 * name that is no longer free or a lot that is already sold. The server refuses those anyway, but
 * showing them at all is a worse experience than one more round trip.
 */
class NicknameRepositoryImpl(
    private val remote: NicknameRemoteDataSource,
) : NicknameRepository {
    override suspend fun checkAvailability(nickname: String): NicknameAvailability =
        remote.checkAvailability(nickname)

    override suspend fun owned(): List<OwnedNickname> = remote.owned()

    override suspend fun setActive(nickname: String) = remote.setActive(nickname)

    override suspend fun claim(nickname: String): Long = remote.claim(nickname)

    override suspend fun listings(limit: Int): List<NicknameListing> = remote.listings(limit)

    override suspend fun listForSale(nickname: String, price: Long) =
        remote.listForSale(nickname, price)

    override suspend fun cancelListing(nickname: String) = remote.cancelListing(nickname)

    override suspend fun buy(nickname: String): Long = remote.buy(nickname)
}
