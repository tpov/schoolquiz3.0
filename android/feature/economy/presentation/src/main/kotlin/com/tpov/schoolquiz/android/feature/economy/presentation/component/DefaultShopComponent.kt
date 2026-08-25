package com.tpov.schoolquiz.android.feature.economy.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.GetReferralProgramUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.GetShopCatalogUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.ObserveEconomyBalanceUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.PurchaseShopItemUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.LogoRepository
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
    private val logos: LogoRepository,
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
            is ShopViewEvent.NicknameDraftChanged ->
                _state.update { it.copy(nicknames = it.nicknames.copy(draft = event.value)) }
            is ShopViewEvent.CheckNicknameAvailability -> checkAvailability(event.nickname)
            is ShopViewEvent.BuyLogo -> buyLogo(event.logo)
            is ShopViewEvent.ClaimNickname ->
                runNicknameAction(event.nickname) {
                    val charged = nicknames.claim(event.nickname)
                    _state.update {
                        it.copy(
                            nicknames =
                                it.nicknames.copy(
                                    draft = "",
                                    availability = null,
                                    availabilityUnreachable = false,
                                ),
                        )
                    }
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
            else -> browse(event)
        }
    }

    /**
     * Asks whether a name is free.
     *
     * The reply is stored with the name it is about rather than as a bare yes/no, so a slow answer
     * about an older draft cannot end up labelling whatever is in the field by then.
     */
    private fun checkAvailability(nickname: String) {
        val trimmed = nickname.trim()
        if (trimmed.isEmpty()) {
            _state.update {
                it.copy(
                    nicknames =
                        it.nicknames.copy(
                            availability = null,
                            isCheckingAvailability = false,
                            availabilityUnreachable = false,
                        ),
                )
            }
            return
        }
        scope.launch {
            _state.update {
                it.copy(
                    nicknames =
                        it.nicknames.copy(isCheckingAvailability = true, availabilityUnreachable = false),
                )
            }
            val result = runCatching { nicknames.checkAvailability(trimmed) }
            _state.update { current ->
                current.copy(
                    nicknames =
                        current.nicknames.copy(
                            availability = result.getOrNull() ?: current.nicknames.availability,
                            isCheckingAvailability = false,
                            availabilityUnreachable = result.isFailure,
                        ),
                    // A failed check is not a refusal; say so rather than leaving a silent field.
                    message = result.exceptionOrNull()?.readableMessage() ?: current.message,
                )
            }
        }
    }

    /**
     * Events that only rearrange what is on screen — tabs, search, sort, arming a purchase.
     *
     * Split from the main dispatch because they share nothing with it: none of them call the
     * server, and together they were half the branches in one function.
     */
    private fun browse(event: ShopViewEvent) {
        when (event) {
            is ShopViewEvent.MarketTabPicked ->
                _state.update {
                    // Arming does not survive a shelf change: the confirm sitting on a name would
                    // otherwise still be live behind the logos.
                    it.copy(nicknames = it.nicknames.copy(marketTab = event.tab, armed = null))
                }
            is ShopViewEvent.ArmPurchase ->
                _state.update { it.copy(nicknames = it.nicknames.copy(armed = event.key)) }
            is ShopViewEvent.ListingQueryChanged ->
                _state.update { it.copy(nicknames = it.nicknames.copy(listingQuery = event.value)) }
            is ShopViewEvent.ListingSortPicked ->
                _state.update { current ->
                    val names = current.nicknames
                    current.copy(
                        nicknames =
                            if (names.listingSort == event.sort) {
                                names.copy(listingDescending = !names.listingDescending)
                            } else {
                                // A fresh column starts the way people expect to read it: names
                                // from A, prices from cheapest, dates from newest.
                                names.copy(
                                    listingSort = event.sort,
                                    listingDescending = event.sort == NicknameListingSort.DATE,
                                )
                            },
                    )
                }
            else -> Unit
        }
    }

    private fun buyLogo(logo: String) {
        scope.launch {
            _state.update { it.copy(nicknames = it.nicknames.copy(processingNickname = logo, armed = null)) }
            val result = runCatching { logos.buy(logo) }
            _state.update { current ->
                current.copy(
                    nicknames = current.nicknames.copy(processingNickname = null),
                    message =
                        result.fold(
                            onSuccess = { charged -> "Логотип ваш · −$charged" },
                            onFailure = { error -> error.readableMessage() },
                        ),
                )
            }
            if (result.isSuccess) refreshLogos()
        }
    }

    private suspend fun loadLogos() {
        val result = runCatching { logos.catalog() }
        _state.update { current ->
            current.copy(
                nicknames = current.nicknames.copy(logos = result.getOrDefault(current.nicknames.logos)),
                // A shelf that says "загрузка…" for ever is the one outcome worth interrupting for:
                // swallowing the failure leaves nothing to distinguish it from a slow network.
                message = result.exceptionOrNull()?.readableMessage() ?: current.message,
            )
        }
    }

    private fun refreshLogos() {
        scope.launch { loadLogos() }
    }

    private fun refreshNicknames() {
        scope.launch {
            _state.update { it.copy(nicknames = it.nicknames.copy(isLoading = true)) }
            val owned = runCatching { nicknames.owned() }
            val listings = runCatching { nicknames.listings() }
            loadLogos()
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
