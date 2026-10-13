package com.tpov.schoolquiz.shared.feature.app_shell.domain.model

/**
 * Platform-neutral deep link container.
 *
 * Android adapter in apps/android-next maps Intent -> DeepLink(intent.dataString ?: "").
 * First registered pattern: referral registration links (see logic/ReferralDeepLink,
 * history 5.3) — captured by the shell, spent by the profile bootstrap at account creation.
 */
data class DeepLink(val uri: String)
