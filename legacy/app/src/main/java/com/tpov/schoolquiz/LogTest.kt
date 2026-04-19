package com.tpov.schoolquiz

import com.tpov.log_api.logger.Logger

@Logger
class LogTest {
    fun testMethod() {
        println("This is a test method")
    }
    
    fun anotherTestMethod(param: String): String {
        println("Testing with parameter: $param")
        return "Result: $param"
    }
} 