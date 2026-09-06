package com.tpov.schoolquiz.shared.feature.internet.profile.data

import com.tpov.schoolquiz.shared.feature.internet.profile.data.outbox.ListingCancelOutbox
import com.tpov.schoolquiz.shared.feature.internet.profile.data.outbox.ListingCancelQueue
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.NicknameRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ListingCancelSync
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameAvailability
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnedNickname
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.NicknameRepository
import kotlinx.coroutines.flow.Flow

/**
 * Straight pass-through, and deliberately so: nothing here may be cached.
 *
 * Ownership and prices change from other accounts, and a stale answer would let somebody act on a
 * name that is no longer free or a lot that is already sold. The server refuses those anyway, but
 * showing them at all is a worse experience than one more round trip.
 *
 * Taking a lot down is the one thing that does not go straight through: it is queued (AD-3) and
 * reaches the server through the shared outbox (AD-6). The listing itself is server-owned, so no
 * local row changes on the way (AD-25) — the window catches up on its next refresh.
 */
class NicknameRepositoryImpl(
    private val remote: NicknameRemoteDataSource,
    private val cancelQueue: ListingCancelQueue,
) : NicknameRepository {
    override suspend fun checkAvailability(nickname: String): NicknameAvailability = remote.checkAvailability(nickname)

    override suspend fun owned(): List<OwnedNickname> = remote.owned()

    override suspend fun setActive(nickname: String) = remote.setActive(nickname)

    override suspend fun claim(nickname: String): Long = remote.claim(nickname)

    override suspend fun listings(limit: Int): List<NicknameListing> = remote.listings(limit)

    override suspend fun listForSale(
        nickname: String,
        price: Long,
    ) = remote.listForSale(nickname, price).also {
        // Выставленный заново лот есть: отказ прежнего снятия больше ничего не значит.
        cancelQueue.forgetRefusal(ListingCancelOutbox.nicknameRef(nickname))
    }

    override suspend fun cancelListing(nickname: String) =
        cancelQueue.enqueue(ListingCancelOutbox.nicknameRef(nickname)) { uid, nowMs ->
            ListingCancelOutbox.cancelNicknameRow(ownerUid = uid, nickname = nickname, nowMs = nowMs)
        }

    override fun observeCancelSync(nickname: String): Flow<ListingCancelSync?> =
        cancelQueue.observe(ListingCancelOutbox.nicknameRef(nickname))

    override suspend fun buy(nickname: String): Long = remote.buy(nickname)
}
