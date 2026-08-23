package com.tpov.schoolquiz.shared.feature.internet.profile.domain.model

/**
 * A name this account holds.
 *
 * An account owns several and wears one. [generated] marks the name handed out at first sign-in
 * rather than chosen, which is why it does not spend the one free choice everybody gets.
 * [listedPrice] is set only while the name is on sale.
 */
data class OwnedNickname(
    val nickname: String,
    val active: Boolean,
    val generated: Boolean,
    val listedPrice: Long? = null,
) {
    val isForSale: Boolean get() = listedPrice != null
}
