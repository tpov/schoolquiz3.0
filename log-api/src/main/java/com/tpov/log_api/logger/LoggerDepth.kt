package com.tpov.log_api.logger

class LoggerDepth {
    companion object {
        val depth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }
    }
}
