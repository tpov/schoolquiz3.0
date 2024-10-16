package com.tpov.common.data.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object Core {
    var token: String = ""
    var tpovId = 0
        set(value) {
            field = value
            _tpovIdFlow.value = value
        }

    private val _tpovIdFlow = MutableStateFlow(tpovId)
    val tpovIdFlow: StateFlow<Int> = _tpovIdFlow
}
