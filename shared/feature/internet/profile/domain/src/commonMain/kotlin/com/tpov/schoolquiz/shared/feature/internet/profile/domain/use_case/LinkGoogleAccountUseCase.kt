package com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.AccountChooserHost
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.GoogleLinkOutcome
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.GoogleSignInRepository

class LinkGoogleAccountUseCase(
    private val repository: GoogleSignInRepository,
) {
    suspend operator fun invoke(host: AccountChooserHost): Result<GoogleLinkOutcome> = repository.linkGoogleAccount(host)
}
