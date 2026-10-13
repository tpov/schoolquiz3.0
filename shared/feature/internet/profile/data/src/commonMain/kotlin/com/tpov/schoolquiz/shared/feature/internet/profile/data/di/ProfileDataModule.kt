package com.tpov.schoolquiz.shared.feature.internet.profile.data.di

import com.tpov.schoolquiz.shared.feature.internet.profile.data.ActivityRepositoryImpl
import com.tpov.schoolquiz.shared.feature.internet.profile.data.InMemoryPendingReferralStore
import com.tpov.schoolquiz.shared.feature.internet.profile.data.LogoRepositoryImpl
import com.tpov.schoolquiz.shared.feature.internet.profile.data.NicknameRepositoryImpl
import com.tpov.schoolquiz.shared.feature.internet.profile.data.PendingReferralStore
import com.tpov.schoolquiz.shared.feature.internet.profile.data.ProfileLocalDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.data.ProfileRepositoryImpl
import com.tpov.schoolquiz.shared.feature.internet.profile.data.RoomProfileLocalDataSource
import com.tpov.schoolquiz.shared.feature.internet.profile.data.VerificationRepositoryImpl
import com.tpov.schoolquiz.shared.feature.internet.profile.data.outbox.ListingCancelQuarantine
import com.tpov.schoolquiz.shared.feature.internet.profile.data.outbox.ListingCancelQueue
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ActivityRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.LogoRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.NicknameRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.VerificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.module.Module
import org.koin.dsl.module

@Suppress("unused")
fun profileDataModule(
    currentUidFlow: () -> Flow<String?> = { flowOf(null) },
    pendingReferralStore: PendingReferralStore = InMemoryPendingReferralStore(),
): Module =
    module {
        // Один экземпляр на процесс: пишет ловец deep-link, читает bootstrap профиля.
        single<PendingReferralStore> { pendingReferralStore }
        single<ProfileLocalDataSource> { RoomProfileLocalDataSource(get()) }
        // Снятие лота уезжает общей очередью (AD-3, AD-6); её движок и таблица — в composition
        // root, а операции `profile.CANCEL_*` описаны здесь, у владеющей фичи (NFR4, NFR5).
        single { ListingCancelQueue(outbox = get(), currentUidFlow = currentUidFlow) }
        // Реакция на карантин снятия — тоже за фичей (AD-28): откатывать нечего, локальной
        // половины нет (AD-25), пометку строка читает из очереди. Composition root только маршрутизирует.
        single { ListingCancelQuarantine() }
        single<NicknameRepository> { NicknameRepositoryImpl(remote = get(), cancelQueue = get()) }
        single<LogoRepository> { LogoRepositoryImpl(remote = get(), cancelQueue = get()) }
        single<VerificationRepository> { VerificationRepositoryImpl(remote = get()) }
        single<ActivityRepository> {
            ActivityRepositoryImpl(
                attemptDao = get(),
                currentUidFlow = currentUidFlow,
            )
        }
        single<ProfileRepository> {
            ProfileRepositoryImpl(
                local = get(),
                remote = get(),
                currentUidFlow = currentUidFlow,
                pendingReferral = get(),
            )
        }
    }
