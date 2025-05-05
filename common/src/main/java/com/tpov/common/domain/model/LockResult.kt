package com.tpov.common.domain.model

sealed class LockServerResult {
    data object Success : LockServerResult()
    data object AlreadyLocked : LockServerResult()
    data class Error(val exception: Throwable) : LockServerResult()
}
