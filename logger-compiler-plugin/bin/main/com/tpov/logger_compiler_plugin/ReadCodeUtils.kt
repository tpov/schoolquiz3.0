package com.tpov.logger_compiler_plugin

import com.tpov.logger_compiler_plugin.Core.getFunctionFullName
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid

object ReadCodeUtils {

    fun getPathWithRootFunction(declaration: IrFunction): Set<String> {
        val paths = mutableSetOf<String>()
        var parent = declaration.parent
        var currentPath = mutableListOf<String>()

        if (parent is IrClass &&
            parent.annotations.any { it.type.classFqName == Core.loggerAnnotationFqName }) {
            paths.add("${parent.packageFqName}.${parent.name}_${declaration.name}")
        }

        while (parent is IrClass) {
            val currentClass = parent

            currentClass.declarations.forEach { decl ->
                if (decl is IrFunction) {
                    decl.body?.accept(object : IrElementVisitorVoid {
                        override fun visitElement(element: IrElement) {
                            element.acceptChildren(this, null)
                        }

                        override fun visitCall(expression: IrCall) {
                            if (expression.symbol.owner == declaration) {
                                val newPath = currentPath.toMutableList()
                                newPath.add(getFunctionFullName(decl))
                                paths.add(newPath.joinToString("->"))
                            }
                            super.visitCall(expression)
                        }
                    }, null)
                }
            }

            if (!currentClass.annotations.any { it.type.classFqName == Core.loggerAnnotationFqName }) {
                break
            }

            currentPath.add(getFunctionFullName(declaration))
            parent = parent.parent
        }

        return paths
    }
}