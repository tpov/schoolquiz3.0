package com.tpov.schoolquiz.android.feature.economy.presentation.component

import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId

sealed interface ShopViewEvent {
    data class SelectTab(val tab: ShopTab) : ShopViewEvent

    data class Purchase(val itemId: ShopItemId) : ShopViewEvent

    data object MessageShown : ShopViewEvent

    /** Pulls both the owned names and the shop window; they change from other accounts. */
    data object RefreshNicknames : ShopViewEvent

    /** Typing in the claim field. The check is fired by the view once the typing settles. */
    data class NicknameDraftChanged(val value: String) : ShopViewEvent

    data class CheckNicknameAvailability(val nickname: String) : ShopViewEvent

    data class ClaimNickname(val nickname: String) : ShopViewEvent

    data class SetActiveNickname(val nickname: String) : ShopViewEvent

    data class ListNicknameForSale(val nickname: String, val price: Long) : ShopViewEvent

    data class CancelNicknameListing(val nickname: String) : ShopViewEvent

    data class BuyNickname(val nickname: String) : ShopViewEvent
}
