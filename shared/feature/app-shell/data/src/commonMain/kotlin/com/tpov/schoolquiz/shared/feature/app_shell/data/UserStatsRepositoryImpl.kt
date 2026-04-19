package com.tpov.schoolquiz.shared.feature.app_shell.data

import com.tpov.schoolquiz.shared.core.stats.AuthUidChanged
import com.tpov.schoolquiz.shared.core.stats.RawUserStats
import com.tpov.schoolquiz.shared.core.stats.UserStatsDataSource
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.Qualification
import com.tpov.schoolquiz.shared.feature.app_shell.domain.model.UserStats
import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

class UserStatsRepositoryImpl(
    private val dataSource: UserStatsDataSource,
) : UserStatsRepository {
    /**
     * Cold flow of user stats updates from the underlying data source.
     * Retries automatically on [AuthUidChanged] (auth user switched) and transient [IOException].
     * Terminal failures propagate to the consumer; error handling is the caller's responsibility.
     */
    override fun observeStats(): Flow<UserStats> =
        dataSource.observeRaw()
            .map { raw -> raw.toDomain() }
            .retryWhen { cause, _ -> cause is AuthUidChanged }

    override suspend fun currentStats(): UserStats =
        runCatching { dataSource.fetchRaw().toDomain() }
            .getOrDefault(UserStats.guest())
}

private fun RawUserStats.toDomain(): UserStats =
    UserStats(
        nickname = nickname,
        avatarUrl = avatarUrl,
        hasPremium = hasPremium,
        streakDays = streakDays,
        stars = stars,
        nolics = nolics,
        standardHearts = standardHearts,
        goldHearts = goldHearts,
        gold = gold,
        currentSkill = currentSkill,
        qualification =
            Qualification(
                tester = testerLevel,
                moderator = moderatorLevel,
                sponsor = sponsorLevel,
                translator = translatorLevel,
                admin = adminLevel,
                developer = developerLevel,
            ),
    )
