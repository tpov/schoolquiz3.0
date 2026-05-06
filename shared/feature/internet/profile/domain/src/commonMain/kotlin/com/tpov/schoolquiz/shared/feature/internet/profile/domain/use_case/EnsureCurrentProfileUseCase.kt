package com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository

class EnsureCurrentProfileUseCase(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke() = repository.ensureCurrentProfile()
}
