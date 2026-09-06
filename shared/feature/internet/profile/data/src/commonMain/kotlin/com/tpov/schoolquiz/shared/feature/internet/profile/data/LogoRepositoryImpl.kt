package com.tpov.schoolquiz.shared.feature.internet.profile.data

import com.tpov.schoolquiz.shared.feature.internet.profile.data.outbox.ListingCancelOutbox
import com.tpov.schoolquiz.shared.feature.internet.profile.data.outbox.ListingCancelQueue
import com.tpov.schoolquiz.shared.feature.internet.profile.data.remote.LogoRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ListingCancelSync
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.LogoListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileLogo
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.LogoRepository
import kotlinx.coroutines.flow.Flow

/**
 * Straight through: the eight logos and what the account owns are both the server's to say.
 *
 * Except taking a lot down, which is queued (AD-3) and delivered through the shared outbox (AD-6);
 * the listing is server-owned, so nothing local changes on the way (AD-25).
 */
class LogoRepositoryImpl(
    private val remote: LogoRemoteDataSource,
    private val cancelQueue: ListingCancelQueue,
) : LogoRepository {
    override suspend fun catalog(): List<ProfileLogo> = remote.catalog()

    override suspend fun buy(logo: String): Long = remote.buy(logo)

    override suspend fun wear(logo: String): String = remote.wear(logo)

    override suspend fun listings(limit: Int): List<LogoListing> = remote.listings(limit)

    override suspend fun listForSale(
        logo: String,
        price: Long,
    ) = remote.listForSale(logo, price).also {
        // Выставленный заново лот есть: отказ прежнего снятия больше ничего не значит.
        cancelQueue.forgetRefusal(ListingCancelOutbox.logoRef(logo))
    }

    override suspend fun cancelListing(logo: String) =
        cancelQueue.enqueue(ListingCancelOutbox.logoRef(logo)) { uid, nowMs ->
            ListingCancelOutbox.cancelLogoRow(ownerUid = uid, logo = logo, nowMs = nowMs)
        }

    override fun observeCancelSync(logo: String): Flow<ListingCancelSync?> =
        cancelQueue.observe(ListingCancelOutbox.logoRef(logo))

    override suspend fun buyListed(logo: String): Long = remote.buyListed(logo)
}
