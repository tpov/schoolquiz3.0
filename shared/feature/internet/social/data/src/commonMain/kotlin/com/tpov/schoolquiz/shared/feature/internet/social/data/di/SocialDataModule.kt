package com.tpov.schoolquiz.shared.feature.internet.social.data.di

import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.feature.internet.social.data.PollsRepositoryImpl
import com.tpov.schoolquiz.shared.feature.internet.social.data.provider.DefaultPollsIdProvider
import com.tpov.schoolquiz.shared.feature.internet.social.data.provider.DefaultPollsTimestampProvider
import com.tpov.schoolquiz.shared.feature.internet.social.data.remote.PollsRemoteDataSource
import com.tpov.schoolquiz.shared.feature.internet.social.domain.provider.PollsIdProvider
import com.tpov.schoolquiz.shared.feature.internet.social.domain.provider.PollsTimestampProvider
import com.tpov.schoolquiz.shared.feature.internet.social.domain.repository.PollsRepository
import org.koin.dsl.module

val socialDataModule =
    module {
        // Чтение — view-транспорт (Firebase), запись — общая очередь (AD-6); transportBinding
        // подключается в platform-модуле.
        single<PollsRepository> {
            PollsRepositoryImpl(remote = get<PollsRemoteDataSource>(), outbox = get<OutboxStore>())
        }
        single<PollsIdProvider> { DefaultPollsIdProvider() }
        single<PollsTimestampProvider> { DefaultPollsTimestampProvider() }
    }
