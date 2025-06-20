package com.tpov.shop.domain

data class ReferralUser(
    val id: String, // Or some other unique identifier
    val name: String, // Or some display name if available
    var allOpenBox: Int,
    var newBonusBox: Int,
    val tpovId: String // User's TPOV ID, if needed for display or other logic
)
