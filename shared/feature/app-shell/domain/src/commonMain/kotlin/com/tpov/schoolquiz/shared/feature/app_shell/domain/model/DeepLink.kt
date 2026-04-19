package com.tpov.schoolquiz.shared.feature.app_shell.domain.model

/**
 * Platform-neutral deep link container.
 *
 * Android adapter in apps/android-next maps Intent -> DeepLink(intent.dataString ?: "").
 * In MVP, no URL patterns are registered — onDeepLink() is a stub architectural hook.
 */
data class DeepLink(val uri: String)
