package com.tpov.schoolquiz.shared.feature.economy.data.di

import com.tpov.schoolquiz.shared.feature.economy.data.EconomyConstantsRepositoryImpl
import com.tpov.schoolquiz.shared.feature.economy.data.EconomyConstantsSync
import com.tpov.schoolquiz.shared.feature.economy.data.EconomyLocalDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.EconomyRepositoryImpl
import com.tpov.schoolquiz.shared.feature.economy.data.EconomyTermsConsentGateImpl
import com.tpov.schoolquiz.shared.feature.economy.data.GiftBoxIntentStore
import com.tpov.schoolquiz.shared.feature.economy.data.GiftBoxRepositoryImpl
import com.tpov.schoolquiz.shared.feature.economy.data.GiftClaimIntentStore
import com.tpov.schoolquiz.shared.feature.economy.data.GiftClaimRepositoryImpl
import com.tpov.schoolquiz.shared.feature.economy.data.OutboxGiftBoxDelivery
import com.tpov.schoolquiz.shared.feature.economy.data.RoomEconomyLocalDataSource
import com.tpov.schoolquiz.shared.feature.economy.data.RoomGiftBoxIntentStore
import com.tpov.schoolquiz.shared.feature.economy.data.RoomGiftBoxLocalStreakStore
import com.tpov.schoolquiz.shared.feature.economy.data.RoomGiftClaimIntentStore
import com.tpov.schoolquiz.shared.feature.economy.data.UnsettledPurchaseSettler
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyConstantsRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyTermsConsentGate
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftBoxLocalStreakStore
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftBoxRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.repository.GiftClaimRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun economyDataModule(currentUidFlow: () -> Flow<String?> = { flowOf(null) }): Module =
    module {
        single<EconomyLocalDataSource> { RoomEconomyLocalDataSource(get()) }
        single<EconomyRepository> {
            EconomyRepositoryImpl(
                local = get(),
                remote = get(),
                receipts = get(),
                currentUidFlow = currentUidFlow,
            )
        }
        // Таблица настроек одна на всех и от аккаунта не зависит: цены не меняются от того,
        // кто вошёл. Поэтому не auth-scoped и переживает смену аккаунта целиком.
        single<EconomyConstantsRepository> {
            EconomyConstantsRepositoryImpl(remote = get(), store = get())
        }
        single { EconomyConstantsSync(get()) }
        // Согласие с платными условиями, как и сама таблица, не auth-scoped: цены от того, кто
        // вошёл, не зависят. Хранилище согласия платформенное, биндится в composition root рядом
        // с EconomyConstantsStore. Принятие согласия снимает ожидание очереди (CAP-16): возврат
        // ждущих записей — контракт ядра outbox.
        single<EconomyTermsConsentGate> {
            EconomyTermsConsentGateImpl(constants = get(), store = get(), requeue = get())
        }
        single<GiftBoxIntentStore> { RoomGiftBoxIntentStore(get()) }
        // Заявки подарков (7.4): намерение и местный счёт серии живут в Room, транспорт — прямой
        // вызываемый. Тело хранится до запроса, поэтому повтор после смерти процесса едет тем же
        // operationId, и второй эффект исключён забором box_operations на сервере.
        single<GiftClaimIntentStore> { RoomGiftClaimIntentStore(get()) }
        single<GiftBoxLocalStreakStore> { RoomGiftBoxLocalStreakStore(get()) }
        single<GiftClaimRepository> {
            GiftClaimRepositoryImpl(
                remote = get(),
                profileRepository = get(),
                intents = get(),
                network = get(),
                currentUidFlow = currentUidFlow,
                newOperationId = { Uuid.random().toString() },
                clock = { Clock.System.now().toEpochMilliseconds() },
            )
        }
        single<GiftBoxRepository> {
            GiftBoxRepositoryImpl(
                remote = get(),
                profileRepository = get(),
                intents = get(),
                receipts = get(),
                engine = OutboxGiftBoxDelivery(get()),
                sessions = get(),
                network = get(),
                scheduler = get(),
                currentUidFlow = currentUidFlow,
                newOpeningId = { Uuid.random().toString() },
                clock = { Clock.System.now().toEpochMilliseconds() },
            )
        }
        // Живёт столько же, сколько процесс: покупка, оплаченная и не начисленная, должна
        // дорешаться и при headless-старте. Scope — общий app scope из composition root.
        single {
            UnsettledPurchaseSettler(
                currentUidFlow = currentUidFlow,
                networkMonitor = get(),
                billing = get(),
                settlePurchase = get(),
                scope = get<CoroutineScope>(),
            )
        }
    }
