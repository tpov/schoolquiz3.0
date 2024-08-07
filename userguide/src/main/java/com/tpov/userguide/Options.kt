package com.tpov.userguide

data class Options(
    var numberDot: String? = null,
    var countRepeat: Int = 1,
    var showDot: Boolean = true,
    var exactMatchKey: Int? = null,
    var minValueKey: Int? = null,
    var idGroupGuide: Int = 1,
    var showWithoutOptions: Boolean = false
)