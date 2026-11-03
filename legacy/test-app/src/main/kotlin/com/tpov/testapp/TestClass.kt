package com.tpov.testapp

import com.tpov.log_api.logger.Logger

@Logger
class TestClass {
    fun testMethod(param: String): String {
        println("Running testMethod with param: $param")
        return "Result: $param"
    }
    
    fun anotherMethod() {
        println("Running anotherMethod")
    }
} 