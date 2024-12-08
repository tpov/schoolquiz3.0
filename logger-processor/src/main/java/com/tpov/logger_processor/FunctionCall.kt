package com.tpov.logger_processor

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction

typealias FunParam = Pair<String, String>
typealias FunContext = Pair<Int, Int>

data class FunctionCall(
    val className: String,
    val functionName: String,
    val moduleName: String,
    val parameters: List<FunParam>,
    val funContext: FunContext,
    val architectureLayer: Int,
    val pathFunctionFromRootFunction: String
) {
    constructor(declaration: IrFunction, funContext: FunContext, pathFunction: String) : this(
        className = (declaration.parent as? IrClass)?.name?.asString() ?: "null",
        functionName = declaration.name.toString(),
        moduleName = "",
        parameters = declaration.valueParameters.map {
            FunParam(it.name.asString(), it.type.toString())
        },
        funContext = funContext,
        architectureLayer =,
        pathFunctionFromRootFunction = pathFunction
    )

}