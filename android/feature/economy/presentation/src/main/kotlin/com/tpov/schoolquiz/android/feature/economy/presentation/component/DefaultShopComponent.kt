package com.tpov.schoolquiz.android.feature.economy.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.GetReferralProgramUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.GetShopCatalogUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.ObserveEconomyBalanceUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.PurchaseShopItemUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.NicknameRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DefaultShopComponent(
    componentContext: ComponentContext,
    private val observeBalance: ObserveEconomyBalanceUseCase,
    private val getCatalog: GetShopCatalogUseCase,
    private val purchaseItem: PurchaseShopItemUseCase,
    private val getReferralProgram: GetReferralProgramUseCase,
    private val nicknames: NicknameRepository,
) : ShopComponent, ComponentContext by componentContext {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + Dispatchers.Main.immediate)

    private val _state =
        MutableStateFlow(
            ShopViewState(
                items = getCatalog.execute(ShopViewState().balance),
            ),
        )
    override val state: StateFlow<ShopViewState> = _state

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }
        scope.launch {
            _state.update { it.copy(referralProgram = getReferralProgram.execute()) }
        }
        scope.launch {
            observeBalance.execute().collect { balance ->
                _state.update {
                    it.copy(
                        balance = balance,
                        items = getCatalog.execute(balance),
                        isLoading = false,
                    )
                }
            }
        }
    }

    override fun obtainEvent(event: ShopViewEvent) {
        when (event) {
            is ShopViewEvent.SelectTab -> {
                _state.update { it.copy(selectedTab = event.tab) }
                // Names and lots move from other accounts, so the tab reloads on every visit
                // rather than trusting whatever was fetched last time.
                if (event.tab == ShopTab.NICKNAMES) refreshNicknames()
            }
            is ShopViewEvent.Purchase -> purchase(event.itemId)
            ShopViewEvent.MessageShown -> _state.update { it.copy(message = null) }
            ShopViewEvent.RefreshNicknames -> refreshNicknames()
            is ShopViewEvent.ClaimNickname ->
                runNicknameAction(event.nickname) {
                    val charged = nicknames.claim(event.nickname)
                    if (charged > 0) "Имя куплено за $charged" else "Имя занято за вами"
                }
            is ShopViewEvent.SetActiveNickname ->
                runNicknameAction(event.nickname) {
                    nicknames.setActive(event.nickname)
                    "Активное имя — ${event.nickname}"
                }
            is ShopViewEvent.ListNicknameForSale ->
                runNicknameAction(event.nickname) {
                    nicknames.listForSale(event.nickname, event.price)
                    "Выставлено за ${event.price}"
                }
            is ShopViewEvent.CancelNicknameListing ->
                runNicknameAction(event.nickname) {
                    nicknames.cancelListing(event.nickname)
                    "Снято с продажи"
                }
            is ShopViewEvent.BuyNickname ->
                runNicknameAction(event.nickname) {
                    val commission = nicknames.buy(event.nickname)
                    "Имя куплено, комиссия $commission"
                }
        }
    }

    private fun refreshNicknames() {
        scope.launch {
            _state.update { it.copy(nicknames = it.nicknames.copy(isLoading = true)) }
            val owned = runCatching { nicknames.owned() }
            val listings = runCatching { nicknames.listings() }
            _state.update { current ->
                current.copy(
                    nicknames =
                        current.nicknames.copy(
                            owned = owned.getOrDefault(current.nicknames.owned),
                            listings = listings.getOrDefault(current.nicknames.listings),
                            isLoading = false,
                        ),
                    // Either half may fail on its own; show whichever complaint arrived.
                    message =
                        owned.exceptionOrNull()?.readableMessage()
                            ?: listings.exceptionOrNull()?.readableMessage()
                            ?: current.message,
                )
            }
        }
    }

    /**
     * Runs one nickname request and reloads afterwards.
     *
     * The reload is not optional: ownership and prices are decided on the server, and after a buy
     * or a sale the local lists are wrong in ways the user would otherwise act on.
     */
    private fun runNicknameAction(
        nickname: String,
        action: suspend () -> String,
    ) {
        if (_state.value.nicknames.processingNickname != null) return
        scope.launch {
            _state.update {
                it.copy(nicknames = it.nicknames.copy(processingNickname = nickname), message = null)
            }
            val outcome = runCatching { action() }
            _state.update {
                it.copy(
                    nicknames = it.nicknames.copy(processingNickname = null),
                    message = outcome.getOrElse { error -> error.readableMessage() },
                )
            }
            refreshNicknames()
        }
    }

    private fun purchase(itemId: ShopItemId) {
        if (_state.value.processingItemId != null) return
        scope.launch {
            _state.update { it.copy(processingItemId = itemId, message = null) }
            val result = purchaseItem.execute(itemId)
            _state.update { current ->
                result.fold(
                    onSuccess = { purchase ->
                        current.copy(
                            balance = purchase.balance,
                            items = getCatalog.execute(purchase.balance),
                            processingItemId = null,
                            isLoading = false,
                            message = purchase.message,
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            processingItemId = null,
                            isLoading = false,
                            message = error.readableMessage(),
                        )
                    },
                )
            }
        }
    }

    private fun Throwable.readableMessage(): String {
        if (this is CancellationException) throw this
        return message?.takeIf { it.isNotBlank() } ?: "Не удалось выполнить действие"
    }
}
