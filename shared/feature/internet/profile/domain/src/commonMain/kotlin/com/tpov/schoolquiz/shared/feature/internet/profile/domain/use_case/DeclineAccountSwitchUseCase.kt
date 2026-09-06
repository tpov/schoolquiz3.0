package com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.GoogleSignInRepository

/** Игрок отказался — прежний аккаунт остаётся, ничего не удаляется (AD-8). */
class DeclineAccountSwitchUseCase(
    private val repository: GoogleSignInRepository,
) {
    operator fun invoke() = repository.declineAccountSwitch()
}
