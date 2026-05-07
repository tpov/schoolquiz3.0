package com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.di

import com.tpov.schoolquiz.shared.feature.internet.leaderboard.data.repository.TournamentLeaderboardRepositoryImpl
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.repository.TournamentLeaderboardRepository
import com.tpov.schoolquiz.shared.feature.internet.leaderboard.domain.use_case.FetchTournamentOverviewUseCase
import org.koin.dsl.module

val leaderboardDataModule =
    module {
        single<TournamentLeaderboardRepository> {
            TournamentLeaderboardRepositoryImpl(remote = get())
        }
        factory { FetchTournamentOverviewUseCase(repository = get()) }
    }
