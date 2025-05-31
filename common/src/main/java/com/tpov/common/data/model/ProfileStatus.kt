package com.tpov.common.data.model

enum class ProfileStatus(val statusCode: Int) {
    OFFLINE(1),
    ANONYMOUS(2),
    STANDARD(3),
    VERIFIED(4);

    companion object {
        fun fromStatusCode(statusCode: Int): ProfileStatus? =
            values().find { it.statusCode == statusCode }
    }
} 