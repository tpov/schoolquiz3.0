package com.tpov.log_api.logger

class LoggerDepth {
    companion object {
        @JvmStatic
        val depth: ThreadLocal<Int> by lazy {
            ThreadLocal.withInitial { 0 }
        }

        @JvmStatic
        fun getDepth(): Int = depth.get()

        @JvmStatic
        fun setDepth(value: Int) {
            depth.set(value)
        }
    }
}