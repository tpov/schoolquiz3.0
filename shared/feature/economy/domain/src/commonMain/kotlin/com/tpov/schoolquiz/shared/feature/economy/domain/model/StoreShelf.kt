package com.tpov.schoolquiz.shared.feature.economy.domain.model

/**
 * What the store said about the products it sells, as one answer for the whole shelf.
 *
 * Three endings, all named: the store quoted its products, the store has none configured, or the
 * store could not be asked. The distinction matters to the shelf — a locked row that cannot say
 * which of the two lock reasons holds is the "empty space without an explanation" the shop was
 * forbidden to show.
 */
sealed interface StoreShelf {
    /** The store quoted every requested product. */
    data class Ready(val products: List<StoreProduct>) : StoreShelf

    /** The store answered, but no products are configured in the console — an owner action. */
    data object NotConfigured : StoreShelf

    /** The store could not be reached, or did not return every requested product. */
    data object Unreachable : StoreShelf
}
