package com.tpov.schoolquiz.android.feature.economy.presentation.component

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.LogoListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameListing
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.OwnedNickname
import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.ProfileLogo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NicknameShopStateTest {
    private val listings =
        listOf(
            listing("zephyr", price = 5, listedAtMs = 300),
            listing("Alpha", price = 50, listedAtMs = 100),
            listing("mid", price = 20, listedAtMs = 200),
        )

    @Test
    fun sortsByDateNewestFirstByDefault() {
        val state = NicknameShopState(listings = listings)

        assertEquals(listOf("zephyr", "mid", "Alpha"), state.visibleListings.map { it.nickname })
    }

    @Test
    fun sortsByPriceCheapestFirst() {
        val state =
            NicknameShopState(
                listings = listings,
                listingSort = NicknameListingSort.PRICE,
                listingDescending = false,
            )

        assertEquals(listOf(5L, 20L, 50L), state.visibleListings.map { it.price })
    }

    /** Case matters to a sort and not to a person: "Alpha" belongs before "mid", not after "zephyr". */
    @Test
    fun sortsByNameIgnoringCase() {
        val state =
            NicknameShopState(
                listings = listings,
                listingSort = NicknameListingSort.NAME,
                listingDescending = false,
            )

        assertEquals(listOf("Alpha", "mid", "zephyr"), state.visibleListings.map { it.nickname })
    }

    @Test
    fun reversesWhenDescending() {
        val ascending =
            NicknameShopState(
                listings = listings,
                listingSort = NicknameListingSort.PRICE,
                listingDescending = false,
            ).visibleListings
        val descending =
            NicknameShopState(
                listings = listings,
                listingSort = NicknameListingSort.PRICE,
                listingDescending = true,
            ).visibleListings

        assertEquals(ascending.reversed(), descending)
    }

    @Test
    fun searchIgnoresCaseAndMatchesAnywhere() {
        val state = NicknameShopState(listings = listings, listingQuery = "PH")

        assertEquals(listOf("zephyr", "Alpha"), state.visibleListings.map { it.nickname })
    }

    @Test
    fun blankSearchKeepsEverything() {
        val state = NicknameShopState(listings = listings, listingQuery = "   ")

        assertEquals(listings.size, state.visibleListings.size)
    }

    @Test
    fun searchWithNoMatchesReturnsNothing() {
        val state = NicknameShopState(listings = listings, listingQuery = "нетакого")

        assertEquals(emptyList<NicknameListing>(), state.visibleListings)
    }

    /** The name being worn answers "who am I right now", so it never has to be hunted for. */
    @Test
    fun wornNameComesFirst() {
        val state =
            NicknameShopState(
                owned =
                    listOf(
                        OwnedNickname(nickname = "spare", active = false, generated = false),
                        OwnedNickname(nickname = "worn", active = true, generated = false),
                        OwnedNickname(nickname = "another", active = false, generated = true),
                    ),
            )

        assertEquals(listOf("worn", "spare", "another"), state.ownedWornFirst.map { it.nickname })
    }

    /** Sorting is stable: with nothing worn, the server's order survives untouched. */
    @Test
    fun keepsServerOrderWhenNothingIsWorn() {
        val owned =
            listOf(
                OwnedNickname(nickname = "one", active = false, generated = false),
                OwnedNickname(nickname = "two", active = false, generated = false),
            )

        assertEquals(owned, NicknameShopState(owned = owned).ownedWornFirst)
    }

    /** The logo being worn answers "what am I wearing", so it never has to be hunted for. */
    @Test
    fun wornLogoComesFirst() {
        val state =
            NicknameShopState(
                logos =
                    listOf(
                        logo("Golden Crown"),
                        logo("Diamond Star"),
                        logo("Phoenix Wings"),
                    ),
                activeLogo = "Diamond Star",
            )

        assertEquals(listOf("Diamond Star", "Golden Crown", "Phoenix Wings"), state.ownedLogosWornFirst.map { it.name })
    }

    /** With nothing worn the server's logo order survives untouched. */
    @Test
    fun keepsServerLogoOrderWhenNothingIsWorn() {
        val logos = listOf(logo("Golden Crown"), logo("Diamond Star"))

        assertEquals(logos, NicknameShopState(logos = logos).ownedLogosWornFirst)
    }

    /** Only held logos belong to the owned shelf: the rest are still on the store grid. */
    @Test
    fun ownedLogosDropsUnowned() {
        val state =
            NicknameShopState(
                logos = listOf(logo("Golden Crown", owned = false), logo("Diamond Star", owned = true)),
            )

        assertEquals(listOf("Diamond Star"), state.ownedLogosWornFirst.map { it.name })
    }

    /** The lot lookup answers "is this one already on sale?" in one step, keyed by name. */
    @Test
    fun logoListingsAreIndexedByName() {
        val state = NicknameShopState(logoListings = listOf(logoListing("Golden Crown", price = 10)))

        assertEquals(10L, state.logoListingsByName["Golden Crown"]?.price)
        assertEquals(emptyMap<String, LogoListing>(), NicknameShopState().logoListingsByName)
    }

    @Test
    fun isWornFollowsActiveLogo() {
        val state = NicknameShopState(activeLogo = "Diamond Star")

        assertTrue(state.isWorn("Diamond Star"))
        assertFalse(state.isWorn("Golden Crown"))
        assertFalse(NicknameShopState().isWorn("Diamond Star"))
    }

    private fun logo(
        name: String,
        owned: Boolean = true,
    ) = ProfileLogo(name = name, price = 100, owned = owned)

    private fun logoListing(
        logo: String,
        price: Long,
    ) = LogoListing(
        logo = logo,
        price = price,
        sellerNickname = "seller",
        listedAtMs = 1,
    )

    private fun listing(
        nickname: String,
        price: Long,
        listedAtMs: Long,
    ) = NicknameListing(
        nickname = nickname,
        price = price,
        sellerNickname = "seller",
        listedAtMs = listedAtMs,
    )
}
