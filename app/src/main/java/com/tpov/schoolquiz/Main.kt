package com.tpov.schoolquiz

import kotlinx.coroutines.delay
import java.lang.Thread.sleep

class Main {

    fun main() {
        one()
        two()
        three()
    }

    private fun one() {
        sleep(1000)
    }

    private fun two() {
        sleep(2000)
    }

    private fun three() {
        sleep(500)
    }
}