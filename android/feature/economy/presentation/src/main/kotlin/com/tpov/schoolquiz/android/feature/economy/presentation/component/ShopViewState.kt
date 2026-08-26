package com.tpov.schoolquiz.android.feature.economy.presentation.component

import com.tpov.schoolquiz.shared.feature.economy.domain.model.EconomyResourceBalance
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ReferralProgram
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopCatalogItem
import com.tpov.schoolquiz.shared.feature.economy.domain.model.ShopItemId
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.LogoListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameAvailability
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnedNickname
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileLogo

data class ShopViewState(
    val balance: EconomyResourceBalance = EconomyResourceBalance.guest(),
    val items: List<ShopCatalogItem> = emptyList(),
    val referralProgram: ReferralProgram = ReferralProgram(link = "", invitedUsers = emptyList()),
    val selectedTab: ShopTab = ShopTab.STORE,
    val isLoading: Boolean = true,
    val processingItemId: ShopItemId? = null,
    val message: ShopMessage? = null,
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
    /** Which shelf the market is showing. */
    val marketTab: NicknameMarketTab = NicknameMarketTab.NAMES,
    val logos: List<ProfileLogo> = emptyList(),
    /** Avatars other accounts listed; the LOGOS shelf sells from it. */
    val logoListings: List<LogoListing> = emptyList(),
    /**
     * The emblem on the account right now, kept by the client.
     *
     * The catalogue has no active flag — the server does not send one — so the worn logo is
     * remembered here: set when a wear is asked for and left untouched by refreshes, which only
     * re-ask for what is owned and listed. For the UI the name is all it takes; the avatarUrl the
     * server returns is for other screens.
     */
    val activeLogo: String? = null,
    /**
     * What a second tap would buy.
     *
     * Spending gold asks twice, and this holds what the first tap armed — a name or a logo. One
     * field rather than two because only ever one thing is armed, and two could disagree.
     */
    val armed: String? = null,
) {
    /**
     * The names you hold, worn one first.
     *
     * A stable sort, so everything else keeps the order the server sent. The worn name is the one
     * being asked about — it is the answer to "who am I right now" — and it should not have to be
     * hunted for down a list.
     */
    val ownedWornFirst: List<OwnedNickname>
        get() = owned.sortedByDescending { it.active }

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

    /** The logos you hold, the worn one first, mirroring [ownedWornFirst]. */
    val ownedLogosWornFirst: List<ProfileLogo>
        get() = logos.filter { it.owned }.sortedByDescending { it.name == activeLogo }

    /** Each listed logo's lot by name, so a row answers "is it on sale?" in one lookup. */
    val logoListingsByName: Map<String, LogoListing>
        get() = logoListings.associateBy { it.logo }

    /** "Is this the emblem on the account right now?" — the answer the catalogue does not carry. */
    fun isWorn(logo: String): Boolean = activeLogo == logo
}

/** The two shelves the market sells from. */
enum class NicknameMarketTab {
    NAMES,
    LOGOS,
}

/** How the window is ordered. */
enum class NicknameListingSort {
    NAME,
    PRICE,
    DATE,
}
