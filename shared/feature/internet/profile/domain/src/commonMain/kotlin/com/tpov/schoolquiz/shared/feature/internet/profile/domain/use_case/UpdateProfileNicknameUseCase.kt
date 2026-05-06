package com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.validateProfileNickname
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository

class UpdateProfileNicknameUseCase(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(nickname: String) =
        validateProfileNickname(nickname).fold(
            onSuccess = { repository.updateNickname(it) },
            onFailure = { Result.failure(it) },
        )
}
