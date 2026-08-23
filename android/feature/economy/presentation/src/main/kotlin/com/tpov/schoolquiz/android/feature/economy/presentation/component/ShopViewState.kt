package com.tpov.schoolquiz.android.feature.economy.presentation.component

import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ReferralProgram
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopCatalogItem
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnedNickname

data class ShopViewState(
    val balance: EconomyResourceBalance = EconomyResourceBalance.guest(),
    val items: List<ShopCatalogItem> = emptyList(),
    val referralProgram: ReferralProgram = ReferralProgram(link = "", invitedUsers = emptyList()),
    val selectedTab: ShopTab = ShopTab.STORE,
    val isLoading: Boolean = true,
    val processingItemId: ShopItemId? = null,
    val message: String? = null,
    val nicknames: NicknameShopState = NicknameShopState(),
)

/**
 * The nickname tab.
 *
 * Kept apart from the rest of the shop state because it is fetched separately and can be busy on
 * its own: buying a heart should not blank the list of names, and refreshing names should not make
 * the store look like it is loading.
 */
data class NicknameShopState(
    val owned: List<OwnedNickname> = emptyList(),
    val listings: List<NicknameListing> = emptyList(),
    val isLoading: Boolean = false,
    /** The name a request is in flight for, so only that row shows as busy. */
    val processingNickname: String? = null,
)
