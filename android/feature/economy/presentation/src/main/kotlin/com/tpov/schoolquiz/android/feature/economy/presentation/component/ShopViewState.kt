package com.tpov.schoolquiz.android.feature.economy.presentation.component

import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ReferralProgram
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopCatalogItem
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameAvailability
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
    /** What is being typed into the claim field. */
    val draft: String = "",
    /**
     * The server's answer about [draft], or null while nothing has been asked.
     *
     * Carries the name it answered about, so a reply that arrives after the text moved on can be
     * ignored instead of labelling the wrong word.
     */
    val availability: NicknameAvailability? = null,
    val isCheckingAvailability: Boolean = false,
    /**
     * Set when the check itself could not be made — no network, or the function is not deployed.
     *
     * Distinct from a refusal: "we could not ask" and "the answer is no" lead to different next
     * moves, and a field that says "проверяем…" forever is the worst of both.
     */
    val availabilityUnreachable: Boolean = false,
    /** What is typed into the window's search box. */
    val listingQuery: String = "",
    val listingSort: NicknameListingSort = NicknameListingSort.DATE,
    /** Newest, dearest and Z-first are all "descending"; the same flag serves all three. */
    val listingDescending: Boolean = true,
) {
    /**
     * The window as it should be drawn: searched, then sorted.
     *
     * Done here rather than on the server. The window is capped at fifty lots, which is a list a
     * phone sorts instantly, and paying a round trip per keystroke to reorder fifty rows would be
     * slower and would break the moment the connection did.
     */
    val visibleListings: List<NicknameListing>
        get() {
            val needle = listingQuery.trim()
            val found =
                if (needle.isEmpty()) {
                    listings
                } else {
                    listings.filter { it.nickname.contains(needle, ignoreCase = true) }
                }
            val ordered =
                when (listingSort) {
                    // Case-insensitive, or every capitalised name would sort ahead of every
                    // lowercase one and the alphabet would read as two alphabets.
                    NicknameListingSort.NAME -> found.sortedBy { it.nickname.lowercase() }
                    NicknameListingSort.PRICE -> found.sortedBy { it.price }
                    NicknameListingSort.DATE -> found.sortedBy { it.listedAtMs }
                }
            return if (listingDescending) ordered.reversed() else ordered
        }

    /** Only worth showing when it still describes what is on screen. */
    val draftAvailability: NicknameAvailability?
        get() = availability?.takeIf { it.nickname.equals(draft.trim(), ignoreCase = true) }

    val canClaimDraft: Boolean
        get() = draftAvailability?.available == true && processingNickname == null
}

/** How the window is ordered. */
enum class NicknameListingSort {
    NAME,
    PRICE,
    DATE,
}
