package com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.GoogleLinkOutcome
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.GoogleSignInRepository

/** Игрок согласился потерять неотправленное — переключение доводится до конца (AD-8). */
class ConfirmAccountSwitchUseCase(
    private val repository: GoogleSignInRepository,
) {
    suspend operator fun invoke(): Result<GoogleLinkOutcome> = repository.confirmAccountSwitch()
}
