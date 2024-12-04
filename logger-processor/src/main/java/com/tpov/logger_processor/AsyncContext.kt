package com.tpov.logger_processor

class AsyncContext {
    var isInsideCoroutine = false
    var depth = 0
    var parentFunction: String? = null
    var isMainThread = true
}