package com.tpov.schoolquiz.apps.android_next.di

import com.tpov.schoolquiz.shared.feature.app_shell.domain.repository.UserStatsRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.ServerBalanceRefresher
import org.koin.dsl.module

/**
 * Where economy and the app shell meet.
 *
 * The balance a purchase changes lives with the profile, in another feature. Having economy depend
 * on that feature to re-read it would be a direct feature-to-feature edge, which the layer rules
 * forbid and which would grow into a cycle the moment the profile wanted to know about purchases.
 * So economy names what it needs ([ServerBalanceRefresher]) and the composition root — the one
 * place allowed to know about both — supplies it.
 */
val economyGlueModule =
    module {
        single<ServerBalanceRefresher> { UserStatsServerBalanceRefresher(userStats = get()) }
    }

/**
 * Pulls the balance from the server after a purchase has been credited.
 *
 * A pull, not an addition: the server has already moved the money, and adding `goldGranted` to the
 * local number would create a second source for a monetary value that then disagrees with the
 * server the first time a receipt is replayed or a refund lands (SYNC-AD-25).
 */
internal class UserStatsServerBalanceRefresher(
    private val userStats: UserStatsRepository,
) : ServerBalanceRefresher {
    override suspend fun refresh(): Result<Unit> = userStats.refreshProfile()
}
