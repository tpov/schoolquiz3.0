package com.tpov.schoolquiz.shared.feature.internet.profile.domain.model

private const val MIN_NICKNAME_LENGTH = 3
private const val MAX_NICKNAME_LENGTH = 24

fun sanitizeProfileNickname(value: String): String =
    value.trim()
        .replace(Regex("\\s+"), " ")

fun validateProfileNickname(value: String): Result<String> {
    val nickname = sanitizeProfileNickname(value)
    return when {
        nickname.length < MIN_NICKNAME_LENGTH ->
            Result.failure(IllegalArgumentException("Nickname must contain at least $MIN_NICKNAME_LENGTH characters"))
        nickname.length > MAX_NICKNAME_LENGTH ->
            Result.failure(IllegalArgumentException("Nickname must contain at most $MAX_NICKNAME_LENGTH characters"))
        nickname.any { it.isISOControl() || it == '/' || it == '\\' } ->
            Result.failure(IllegalArgumentException("Nickname contains unsupported characters"))
        else -> Result.success(nickname)
    }
}
