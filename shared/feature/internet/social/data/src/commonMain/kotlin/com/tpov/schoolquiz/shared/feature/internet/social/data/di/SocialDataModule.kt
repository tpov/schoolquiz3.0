package com.tpov.schoolquiz.shared.feature.internet.social.data.di

import com.tpov.schoolquiz.shared.core.outbox.OutboxKeyRepair
import com.tpov.schoolquiz.shared.core.outbox.OutboxStore
import com.tpov.schoolquiz.shared.feature.internet.social.data.PollsRepositoryImpl
import com.tpov.schoolquiz.shared.feature.internet.social.data.outbox.DefaultPollOutboxRepository
import com.tpov.schoolquiz.shared.feature.internet.social.data.outbox.PollOutboxRepository
import com.tpov.schoolquiz.shared.feature.internet.social.data.outbox.PollVoteKeyRepair
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
        // Наблюдение очереди поверхностью опросов (ретро эпика 8, находка 7): замки/бейдж/отказ
        // из OutboxStore.observeEntity, а не из памяти компонента.
        single<PollOutboxRepository> {
            DefaultPollOutboxRepository(outbox = get<OutboxStore>())
        }
        // Ремонт ключей недоехавших голосов (ретро эпика 8, находка 4); механика — ядерная,
        // биндинг её реализации — в composition root, рядом с самой таблицей.
        single<PollVoteKeyRepair> {
            PollVoteKeyRepair(repair = get<OutboxKeyRepair>())
        }
        single<PollsIdProvider> { DefaultPollsIdProvider() }
        single<PollsTimestampProvider> { DefaultPollsTimestampProvider() }
    }
