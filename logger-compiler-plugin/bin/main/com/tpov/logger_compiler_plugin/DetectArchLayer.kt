package com.tpov.logger_compiler_plugin

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.util.fileOrNull
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable

object DetectArchLayer {
    fun getIdLayer(declaration: IrFunction): Int {
        // Get the parent class name if exists
        val parentClass = declaration.parent as? IrClass
        val className = parentClass?.name?.asString() ?: ""

        // Get the file that contains this declaration
        val containingFile = declaration.fileOrNull

        // If the class name contains "ViewModel", it's layer 2
        if (className.contains("ViewModel", ignoreCase = true)) {
            return 2
        }

        // For other cases, let's try to find package info from parent declarations
        var currentParent: IrDeclarationParent? = declaration.parent
        while (currentParent != null) {
            val fqName = when (currentParent) {
                is IrPackageFragment -> currentParent.packageFqName.asString() // Используем fqName вместо packageFqName
                is IrDeclarationWithName -> currentParent.fqNameWhenAvailable?.asString()
                else -> null
            }

            if (fqName != null) {
                when {
                    fqName.contains("presentation") -> return 1
                    fqName.contains("domain") -> return 3
                    fqName.contains("data") -> return 4
                }
            }

            currentParent = when (currentParent) {
                is IrDeclaration -> currentParent.parent
                else -> null
            }
        }

        // Default return if no conditions are met
        return 0
    }
}