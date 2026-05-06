package com.tpov.schoolquiz.shared.feature.economy.data

import com.tpov.schoolquiz.shared.feature.economy.data.remote.GiftBoxRemoteDataSource
import com.tpov.schoolquiz.shared.feature.economy.domain.model.GiftBoxOpening
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftBoxRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException

class GiftBoxRepositoryImpl(
    private val remote: GiftBoxRemoteDataSource,
    private val profileRepository: ProfileRepository,
) : GiftBoxRepository {
    override suspend fun openGiftBox(): Result<GiftBoxOpening> =
        runCatchingCancellable {
            val reward = remote.openGiftBox()
            val syncResult = profileRepository.ensureCurrentProfile()
            GiftBoxOpening(
                reward = reward,
                profileSynced = syncResult.isSuccess,
                remainingBoxCount = syncResult.getOrNull()?.boxCount,
            )
        }
}

private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
