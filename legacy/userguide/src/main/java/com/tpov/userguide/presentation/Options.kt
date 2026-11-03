package com.tpov.userguide.presentation

data class Options(
    var dotText: String? = null,
    var countRepeat: Int = 1,
    var showDot: Boolean = true,
    var showDialog: Boolean = true,
    var exactMatchKey: Int? = null,
    var minValueKey: Int? = null,
    var isInfinityCount: Boolean = false
)