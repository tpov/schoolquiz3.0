package com.tpov.schoolquiz.android.feature.economy.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.tpov.schoolquiz.shared.core.analytics.AnalyticsEvent
import com.tpov.schoolquiz.shared.core.analytics.AnalyticsTracker
import com.tpov.schoolquiz.shared.core.network.SyncError
import com.tpov.schoolquiz.shared.core.network.syncErrorOrNull
import com.tpov.schoolquiz.shared.feature.economy.domain.model.PurchaseOutcome
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProduct
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreProductId
import com.tpov.schoolquiz.shared.feature.economy.domain.model.StoreShelf
import com.tpov.schoolquiz.shared.feature.economy.domain.model.storeProductId
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.BuyGoldPackUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.GetReferralProgramUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.GetShopCatalogUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.LoadStoreShelfUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.ObserveEconomyBalanceUseCase
import com.tpov.schoolquiz.shared.feature.economy.domain.use_case.PurchaseShopItemUseCase
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ListingCancelSync
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.LogoRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.NicknameRepository
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DefaultShopComponent(
    componentContext: ComponentContext,
    private val observeBalance: ObserveEconomyBalanceUseCase,
    private val getCatalog: GetShopCatalogUseCase,
    private val loadStoreShelf: LoadStoreShelfUseCase,
    private val purchaseItem: PurchaseShopItemUseCase,
    private val buyGoldPack: BuyGoldPackUseCase,
    private val analytics: AnalyticsTracker,
    private val logos: LogoRepository,
    private val profile: ProfileRepository,
    private val getReferralProgram: GetReferralProgramUseCase,
    private val nicknames: NicknameRepository,
) : ShopComponent, ComponentContext by componentContext {
    private val componentJob = SupervisorJob()
    private val scope = CoroutineScope(componentJob + Dispatchers.Main.immediate)

    /**
     * Ответ магазина о ценах, переживший перерисовки.
     *
     * Полка пересобирается на каждое движение баланса, и без этой копии каждый её проход стирал бы
     * цены паков до следующего вопроса магазину. Держится в памяти компонента, не в базе: магазин
     * переспрашивают при каждом открытии магазина, вчерашняя цена — цена, которую уже не берут.
     */
    private var storeProducts: List<StoreProduct> = emptyList()

    private val _state =
        MutableStateFlow(
            ShopViewState(
                items = getCatalog.execute(ShopViewState().balance),
            ),
        )
    override val state: StateFlow<ShopViewState> = _state

    init {
        lifecycle.doOnDestroy { componentJob.cancel() }
        // Верх воронки (история 1.5): экран открыт — знаменатель конверсии существует.
        analytics.track(AnalyticsEvent.ShopOpened)
        scope.launch {
            _state.update { it.copy(referralProgram = getReferralProgram.execute()) }
        }
        refreshStoreShelf()
        scope.launch {
            observeBalance.execute().collect { balance ->
                _state.update {
                    it.copy(
                        balance = balance,
                        items = getCatalog.execute(balance, storeProducts),
                        isLoading = false,
                    )
                }
            }
        }
        // Снятие лота уезжает очередью, и строка узнаёт о нём только отсюда: «в очереди» или
        // «отказано — уже продан». Спрашивается ровно про свои лоты, а не про всю витрину.
        scope.launch {
            cancelSyncs(_state.map { it.nicknames.ownNicknameLots }, nicknames::observeCancelSync).collect { marks ->
                _state.update { it.copy(nicknames = it.nicknames.copy(nicknameCancelSyncs = marks)) }
            }
        }
        scope.launch {
            cancelSyncs(_state.map { it.nicknames.ownLogoLots }, logos::observeCancelSync).collect { marks ->
                _state.update { it.copy(nicknames = it.nicknames.copy(logoCancelSyncs = marks)) }
            }
        }
    }

    /** По каждому ключу — что говорит очередь; ключи без ответа в карту не попадают. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun cancelSyncs(
        keys: Flow<Set<String>>,
        source: (String) -> Flow<ListingCancelSync?>,
    ): Flow<Map<String, ListingCancelSync>> =
        keys.distinctUntilChanged().flatMapLatest { set ->
            if (set.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(set.map { key -> source(key).map { key to it } }) { pairs ->
                    pairs.mapNotNull { (key, sync) -> sync?.let { key to it } }.toMap()
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
            is ShopViewEvent.SetActiveLogo ->
                runLogoAction(event.logo) {
                    logos.wear(event.logo)
                    _state.update {
                        it.copy(nicknames = it.nicknames.copy(activeLogo = event.logo))
                    }
                    // The avatar lives on the account: pull it home now, or the drawer keeps the
                    // old picture until the next scheduled sync.
                    profile.ensureCurrentProfile()
                    ShopMessage.LogoWorn(event.logo)
                }
            is ShopViewEvent.ListLogoForSale ->
                runLogoAction(event.logo) {
                    logos.listForSale(event.logo, event.price)
                    ShopMessage.LogoListed(event.price)
                }
            is ShopViewEvent.CancelLogoListing -> queueCancel { logos.cancelListing(event.logo) }
            is ShopViewEvent.BuyLogoListing ->
                runLogoAction(event.logo) {
                    val commission = logos.buyListed(event.logo)
                    ShopMessage.LogoBoughtListed(commission)
                }
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
                    ShopMessage.NicknameClaimed(charged)
                }
            is ShopViewEvent.SetActiveNickname ->
                runNicknameAction(event.nickname) {
                    nicknames.setActive(event.nickname)
                    ShopMessage.NicknameWorn(event.nickname)
                }
            is ShopViewEvent.ListNicknameForSale ->
                runNicknameAction(event.nickname) {
                    nicknames.listForSale(event.nickname, event.price)
                    ShopMessage.NicknameListed(event.price)
                }
            is ShopViewEvent.CancelNicknameListing -> queueCancel { nicknames.cancelListing(event.nickname) }
            is ShopViewEvent.BuyNickname ->
                runNicknameAction(event.nickname) {
                    val commission = nicknames.buy(event.nickname)
                    ShopMessage.NicknameBought(commission)
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
                    message =
                        result.exceptionOrNull()
                            ?.let { ShopMessage.Failure(it.errorDetail()) }
                            ?: current.message,
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
                            onSuccess = { charged -> ShopMessage.LogoPurchased(charged) },
                            onFailure = { error -> ShopMessage.Failure(error.errorDetail()) },
                        ),
                )
            }
            if (result.isSuccess) refreshLogos()
        }
    }

    private suspend fun loadLogos() {
        val catalog = runCatching { logos.catalog() }
        val listings = runCatching { logos.listings(LOGO_LISTING_LIMIT) }
        _state.update { current ->
            current.copy(
                nicknames =
                    current.nicknames.copy(
                        logos = catalog.getOrDefault(current.nicknames.logos),
                        logoListings = listings.getOrDefault(current.nicknames.logoListings),
                    ),
                // A shelf that says "loading" for ever is the one outcome worth interrupting for:
                // swallowing the failure leaves nothing to distinguish it from a slow network.
                message =
                    catalog.exceptionOrNull()?.let { ShopMessage.Failure(it.errorDetail()) }
                        ?: listings.exceptionOrNull()?.let { ShopMessage.Failure(it.errorDetail()) }
                        ?: current.message,
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
                        owned.exceptionOrNull()?.let { ShopMessage.Failure(it.errorDetail()) }
                            ?: listings.exceptionOrNull()?.let { ShopMessage.Failure(it.errorDetail()) }
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
        action: suspend () -> ShopMessage,
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
                    message = outcome.getOrElse { error -> ShopMessage.Failure(error.errorDetail()) },
                )
            }
            refreshNicknames()
        }
    }

    /**
     * Runs one logo request and reloads afterwards.
     *
     * The reload is not optional: ownership and prices are decided on the server, and after a buy
     * or a sale the local lists are wrong in ways the user would otherwise act on.
     */
    private fun runLogoAction(
        logo: String,
        action: suspend () -> ShopMessage,
    ) {
        if (_state.value.nicknames.processingNickname != null) return
        scope.launch {
            _state.update {
                it.copy(nicknames = it.nicknames.copy(processingNickname = logo), message = null)
            }
            val outcome = runCatching { action() }
            _state.update {
                it.copy(
                    nicknames = it.nicknames.copy(processingNickname = null),
                    message = outcome.getOrElse { error -> ShopMessage.Failure(error.errorDetail()) },
                )
            }
            refreshLogos()
        }
    }

    /**
     * Кладёт снятие лота в очередь.
     *
     * Не [runNicknameAction] и не [runLogoAction] намеренно: сервер здесь не зовётся, поэтому строка
     * не помечается занятой, и витрина не перечитывается — лот исчезнет из неё, когда его удалит
     * сервер и витрина обновится сама (AD-25). Что с записью дальше, строка читает из очереди.
     */
    private fun queueCancel(enqueue: suspend () -> Unit) {
        scope.launch {
            val outcome = runCatching { enqueue() }
            _state.update {
                it.copy(
                    message =
                        outcome.fold(
                            onSuccess = { ShopMessage.ListingCancelQueued },
                            onFailure = { error -> ShopMessage.Failure(error.errorDetail()) },
                        ),
                )
            }
        }
    }

    /**
     * Спрашивает магазин о товарах за реальные деньги и перестилает полку с его ответом.
     *
     * Цены — слова магазина, а не наши (NFR4), поэтому вопрос задаётся при каждом создании экрана.
     * Ответ складывается в [storeProducts] и в статус: закрытая строка пака обязана уметь сказать,
     * магазин ли молчит или товары не выставлены, а не просто стоять под замком.
     */
    private fun refreshStoreShelf() {
        scope.launch {
            val shelf = loadStoreShelf()
            storeProducts = (shelf as? StoreShelf.Ready)?.products ?: emptyList()
            _state.update {
                it.copy(
                    storeShelf =
                        when (shelf) {
                            is StoreShelf.Ready -> StoreShelfStatus.READY
                            StoreShelf.NotConfigured -> StoreShelfStatus.NOT_CONFIGURED
                            StoreShelf.Unreachable -> StoreShelfStatus.UNAVAILABLE
                        },
                    items = getCatalog.execute(it.balance, storeProducts),
                )
            }
        }
    }

    private fun purchase(itemId: ShopItemId) {
        if (_state.value.processingItemId != null) return
        // Пак продаёт магазин, а не серверная полка: его покупка — путь истории 1.2, с проверкой
        // чека и погашением после подтверждения, а не списание с баланса.
        val productId = itemId.storeProductId()
        if (productId != null) {
            purchaseInStore(itemId, productId)
            return
        }
        scope.launch {
            _state.update { it.copy(processingItemId = itemId, message = null) }
            val result = purchaseItem.execute(itemId)
            _state.update { current ->
                result.fold(
                    onSuccess = { purchase ->
                        current.copy(
                            balance = purchase.balance,
                            items = getCatalog.execute(purchase.balance, storeProducts),
                            processingItemId = null,
                            isLoading = false,
                            message = ShopMessage.Notice(purchase.message),
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            processingItemId = null,
                            isLoading = false,
                            message = ShopMessage.Failure(error.errorDetail()),
                        )
                    },
                )
            }
        }
    }

    /**
     * Покупает пак через магазин и пересказывает итог словами сообщения.
     *
     * Все четыре исхода [PurchaseOutcome] — не ошибки, у каждого свои слова: «начислено» называет
     * сумму, которую назвал сервер, «отменено» не делает из жеста игрока аварию. Баланс после
     * начисления приходит сам — расчёт перечитывает его с сервера, и поток баланса перестилает
     * полку. Событие завершения воронки отсюда не эмитится: его место — расчёт, где сервер уже
     * сказал своё слово; здесь остаются старт и отказ.
     */
    private fun purchaseInStore(
        itemId: ShopItemId,
        productId: StoreProductId,
    ) {
        scope.launch {
            _state.update { it.copy(processingItemId = itemId, message = null) }
            analytics.track(AnalyticsEvent.PurchaseStarted(productId.playSku))
            val result = buyGoldPack(productId)
            _state.update { current ->
                current.copy(
                    processingItemId = null,
                    message =
                        result.fold(
                            onSuccess = { outcome ->
                                outcome.failureEvent(productId)?.let(analytics::track)
                                outcome.shopMessage()
                            },
                            onFailure = { error ->
                                analytics.track(
                                    AnalyticsEvent.PurchaseFailed(
                                        productId = productId.playSku,
                                        reason = error.failureReason(),
                                    ),
                                )
                                ShopMessage.Failure(error.errorDetail())
                            },
                        ),
                )
            }
        }
    }

    /**
     * Отказ покупки, как его называет воронка: стабильный код, не фраза для игрока (AD-15).
     *
     * `Pending` и «начислено» — не отказы: первое дорешает сеттлер, у второго своё событие в
     * расчёте. Всё остальное — отказ: игрок закрыл окно, сервер назвал причину или ответ не пришёл.
     */
    private fun PurchaseOutcome.failureEvent(productId: StoreProductId): AnalyticsEvent.PurchaseFailed? =
        when (this) {
            is PurchaseOutcome.Cancelled ->
                AnalyticsEvent.PurchaseFailed(productId.playSku, CANCELLED_REASON)
            is PurchaseOutcome.Refused ->
                AnalyticsEvent.PurchaseFailed(productId.playSku, code.name)
            is PurchaseOutcome.Credited,
            PurchaseOutcome.Pending,
            -> null
        }

    private fun Throwable.failureReason(): String =
        when (syncErrorOrNull()) {
            SyncError.NoNetwork -> NO_NETWORK_REASON
            else -> ERROR_REASON
        }

    private fun PurchaseOutcome.shopMessage(): ShopMessage =
        when (this) {
            is PurchaseOutcome.Credited -> ShopMessage.GoldCredited(goldGranted)
            PurchaseOutcome.Pending -> ShopMessage.PurchasePending
            PurchaseOutcome.Cancelled -> ShopMessage.PurchaseCancelled
            is PurchaseOutcome.Refused -> ShopMessage.PurchaseRefused(code.name)
        }

    private companion object {
        /** Стабильные коды причин отказа в воронке: машинные, не фразы (AD-15). */
        const val CANCELLED_REASON = "cancelled"
        const val NO_NETWORK_REASON = "no_network"
        const val ERROR_REASON = "error"
    }

    /**
     * Что показать игроку.
     *
     * Раньше сюда уходило `message` как есть, поэтому «нет интернета» и «не хватает ноликов»
     * выглядели одинаково. Теперь ветвь ошибки решает текст: связи нет — говорим про связь,
     * сервер отказал — показываем его причину.
     */
    private fun Throwable.errorDetail(): String? {
        if (this is CancellationException) throw this
        return when (val error = syncErrorOrNull()) {
            SyncError.NoNetwork -> "Нет соединения. Покупка станет доступна, когда появится интернет."
            is SyncError.Refused -> error.reason
            null -> message?.takeIf { it.isNotBlank() }
            else -> "Не получилось. Попробуйте ещё раз."
        }
    }
}

/** The avatar window is capped like the names window: fifty lots is a list a phone sorts instantly. */
private const val LOGO_LISTING_LIMIT = 50
