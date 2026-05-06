package com.tpov.schoolquiz.shared.feature.internet.profile.data.sync

import com.tpov.schoolquiz.shared.core.sync.Syncable
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository

class ProfileBootstrapSync(
    private val repository: ProfileRepository,
    private val currentUidProvider: suspend () -> String?,
) : Syncable {
    override suspend fun sync(): Result<Unit> {
        if (currentUidProvider().isNullOrBlank()) return Result.success(Unit)
        return repository.ensureCurrentProfile().map { Unit }
    }
}
