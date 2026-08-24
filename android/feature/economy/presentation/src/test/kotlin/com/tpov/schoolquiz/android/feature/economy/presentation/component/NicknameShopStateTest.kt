package com.tpov.schoolquiz.android.feature.economy.presentation.component

import com.tpov.schoolquiz.shared.feature.internet.profile.domain.model.NicknameListing
import org.junit.Assert.assertEquals
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
