package com.tpov.shop.domain

data class ReferralUser(
    val id: String,                 // Unique identifier
    var nickname: String,           // User's nickname for display
    var allOpenBox: Int,            // Total boxes opened by this referred user
    var seasonBoxCount: Int,        // Boxes opened by this user in the current season
    val tpovIdInternal: String      // Referred user's TPOV ID (optional, for internal use/debugging)
) {
    companion object {
        fun placeholder(idSuffix: String): ReferralUser {
            return ReferralUser(
                id = "placeholder_$idSuffix",
                nickname = "Empty Slot", // Placeholder text
                allOpenBox = 0,
                seasonBoxCount = 0,
                tpovIdInternal = ""
            )
        }

        fun isPlaceholder(user: ReferralUser): Boolean {
            return user.id.startsWith("placeholder_")
        }
    }
}
