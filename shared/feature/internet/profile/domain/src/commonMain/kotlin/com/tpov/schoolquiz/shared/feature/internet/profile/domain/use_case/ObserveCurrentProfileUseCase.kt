package com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository

class ObserveCurrentProfileUseCase(
    private val repository: ProfileRepository,
) {
    operator fun invoke() = repository.observeCurrentProfile()
}
