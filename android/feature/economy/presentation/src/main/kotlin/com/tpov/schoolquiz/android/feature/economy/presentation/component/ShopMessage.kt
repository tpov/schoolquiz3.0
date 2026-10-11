package com.tpov.schoolquiz.android.feature.economy.presentation.component

/**
 * What the shop may announce in its message line.
 *
 * Neutral data rather than finished sentences: wording is a screen concern, so the component only
 * says what happened and lets the composable pick the words — including which language they are.
 * [Notice] carries text handed over from the purchasing layer below because its wording is not
 * ours; [Failure] carries the raw platform detail, with the screen supplying the fallback line.
 */
sealed interface ShopMessage {
    /** A claimed name: gold was charged, or zero for the free first claim. */
    data class NicknameClaimed(val charged: Long) : ShopMessage

    data class NicknameWorn(val nickname: String) : ShopMessage

    data class NicknameListed(val price: Long) : ShopMessage

    /**
     * Taking a lot down was queued, not done: it travels on its own, and the lot leaves the window
     * once the server has removed it and the window is refreshed.
     */
    data object ListingCancelQueued : ShopMessage

    data class NicknameBought(val commission: Long) : ShopMessage

    data class LogoPurchased(val charged: Long) : ShopMessage

    data class LogoWorn(val logo: String) : ShopMessage

    data class LogoListed(val price: Long) : ShopMessage

    data class LogoBoughtListed(val commission: Long) : ShopMessage

    /** The shelf itself cannot be reached. */
    data object ShopUnavailable : ShopMessage

    /**
     * Магазин начислил золото за пак: [gold] — то, что назвал сервер.
     *
     * Сумма приходит из ответа сервера, а не из какой-то копии на устройстве, и баланс после неё
     * перечитан с сервера, так что сообщение и пилюля баланса не могут разойтись.
     */
    data class GoldCredited(val gold: Long) : ShopMessage

    /** Подписка премиума активна: сервер подтвердил и начислил срок. */
    data object PremiumActivated : ShopMessage

    /** Деньги ещё не двинулись: платёж доедет, покупка останется в очереди Play. */
    data object PurchasePending : ShopMessage

    /** Платёж подписки ещё не прошёл: премиум включится, когда Play подтвердит платёж. */
    data object PremiumPending : ShopMessage

    /** Игрок закрыл платёжное окно. Ничего не случилось — и притворяться ошибкой нечего. */
    data object PurchaseCancelled : ShopMessage

    /** Сервер отказал и назвал машинную причину [code]; текст человек выбирает на экране. */
    data class PurchaseRefused(val code: String) : ShopMessage

    data class Notice(val text: String) : ShopMessage

    data class Failure(val detail: String?) : ShopMessage
}
