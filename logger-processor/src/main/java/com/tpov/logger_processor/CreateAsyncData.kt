package com.tpov.logger_processor

import com.tpov.logger_processor.Core.asyncFunctionNames
import com.tpov.logger_processor.Core.asyncList
import com.tpov.logger_processor.Core.getFunctionFullName
import com.tpov.logger_processor.Core.isRootFunction
import com.tpov.logger_processor.Core.maxCountThread
import com.tpov.logger_processor.ReadCodeUtils.getPathWithRootFunction
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid

class CreateAsyncData {
    fun initFunction(declaration: IrFunction) {
        val pathList = getPathWithRootFunction(declaration)
        pathList.forEach { path ->
            addAsyncFunctionCalls(declaration, path, getANDAddCountThread(path))
        }
    }

    private fun addAsyncFunctionCalls(declaration: IrFunction, path: String, currentThreadId: Pair<Int, Int>) {
        val body = declaration.body

        body?.accept(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildren(this, null)
            }

            override fun visitCall(expression: IrCall) {
                val calledFunction = expression.symbol.owner
                val calledFunctionName = calledFunction.name.asString()
                val calledFunctionPath = "$path"

                if (calledFunctionName in asyncFunctionNames) {
                    val newThreadId = maxCountThread + 1
                    maxCountThread = newThreadId
                    visitCallFunction(expression, calledFunctionPath, newThreadId)

                } else getANDAddCountThread(calledFunctionPath, currentThreadId)

                super.visitCall(expression)
            }
        }, null)
    }

    private fun visitCallFunction(expression: IrCall, calledFunctionPath: String, newThreadId: Int) {
        expression.acceptChildren(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildren(this, null)
            }

            override fun visitCall(expression: IrCall) {
                val nestedFunction = expression.symbol.owner
                val nestedFunctionPath = "$calledFunctionPath->${getFunctionFullName(nestedFunction)}"
                getANDAddCountThread(nestedFunctionPath, Pair(newThreadId, 0))
            }
        }, null)
    }

    private fun getANDAddCountThread(
        path: String,
        newThreadId: Pair<Int, Int>? = null
    ): Pair<Int, Int> {
        val pathList = path.split("->")
        val lastPath = pathList.lastOrNull() ?: ""
        if (newThreadId != null) {
            asyncList[path] = newThreadId
            return newThreadId
        } else {
            if (isRootFunction(path)) {
                val thread = Pair(0, 0)
                asyncList[path] = thread
                return thread
            } else {
                val thread = getIdAsyncOrNew(path)
                asyncList[path] = thread
                return thread
            }
        }
    }

    private fun getIdAsyncOrNew(fullPath: String): Pair<Int, Int> {
        asyncList.forEach {
            if (it.key == fullPath) return Pair(it.value.first, it.value.second)
        }
        maxCountThread++
        return Pair(maxCountThread, 0)
    }
}