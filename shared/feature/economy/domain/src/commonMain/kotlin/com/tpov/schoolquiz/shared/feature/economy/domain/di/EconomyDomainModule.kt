package com.tpov.schoolquiz.shared.feature.economy.domain.di

import com.tpov.schoolquiz.shared.feature.economy.domain.repository.EconomyConstantsRepository
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.BuyGoldPackUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.GetReferralProgramUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.GetShopCatalogUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.ObserveEconomyBalanceUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.OpenGiftBoxUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.PurchaseShopItemUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.SettlePurchaseUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Экономика, доменный слой.
 *
 * Стала функцией ради [currentUidFlow] — тем же способом, что и `economyDataModule`: покупка
 * помечается аккаунтом покупателя, и брать этот аккаунт нужно потоком, а не снимком, иначе после
 * смены пользователя покупка уедет с чужой меткой.
 */
fun economyDomainModule(
    currentUidFlow: () -> Flow<String?> = { flowOf(null) },
    /**
     * How an account id becomes the tag the store carries. Required, with no default on purpose:
     * a tag that quietly stopped being hashed would make the server refuse every real purchase,
     * and a default here is exactly how that would go unnoticed.
     */
    buyerTag: (String) -> String,
    /** Where the two deliberately swallowed failures go. Silence is the only thing worse. */
    log: (String, Throwable?) -> Unit = { _, _ -> },
): Module =
    module {
        factory { ObserveEconomyBalanceUseCase(get()) }
        factory { GetShopCatalogUseCase(constants = get<EconomyConstantsRepository>()::current) }
        factory { PurchaseShopItemUseCase(get()) }
        factory { GetReferralProgramUseCase(get()) }
        factory { OpenGiftBoxUseCase(get()) }
        // Single, not factory: it owns the one-settlement-per-token guard, and a guard handed out
        // fresh to each caller guards nothing.
        single {
            SettlePurchaseUseCase(
                billing = get(),
                verifier = get(),
                balanceRefresher = get(),
                log = log,
            )
        }
        factory {
            BuyGoldPackUseCase(
                billing = get(),
                networkMonitor = get(),
                settlePurchase = get(),
                currentUidFlow = currentUidFlow,
                buyerTag = buyerTag,
            )
        }
    }
