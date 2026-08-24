package com.tpov.schoolquiz.shared.feature.economy.domain.model

enum class ShopItemId(val wireName: String) {
    STANDARD_HEART_SLOT("STANDARD_HEART_SLOT"),
    GOLD_HEART("GOLD_HEART"),
    QUIZ_SLOT("QUIZ_SLOT"),
    AD_REWARD_BOX("AD_REWARD_BOX"),
    DONATE_GOOGLE_PLAY("DONATE_GOOGLE_PLAY"),
    REFERRAL_PROGRAM("REFERRAL_PROGRAM"),

    /** Opens the nickname market. Nothing is bought by tapping it, so it never reaches the server. */
    NICKNAME_MARKET("NICKNAME_MARKET"),
    ;

    companion object {
        fun fromWireName(value: String): ShopItemId? =
            entries.firstOrNull { it.wireName == value }
    }
}
