package com.tpov.schoolquiz.shared.feature.internet.profile.domain.use_case

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.LeagueStanding
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository

/** Where this account stands among all players, or null when the ranking could not be read. */
class GetLeagueStandingUseCase(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(): LeagueStanding? = repository.leagueStanding()
}
