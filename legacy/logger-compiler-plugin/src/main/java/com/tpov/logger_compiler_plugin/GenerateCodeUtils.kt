package com.tpov.logger_compiler_plugin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.impl.IrValueParameterImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrExpressionBodyImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.name.Name

class GenerateCodeUtils {

    fun addIsRootParameter(declaration: IrFunction, context: IrPluginContext) {
        val booleanType = context.irBuiltIns.booleanType
        val isRootParameter = IrValueParameterImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            origin = IrDeclarationOrigin.DEFINED,
            symbol = IrValueParameterSymbolImpl(),
            name = Name.identifier("isRoot"),
            index = declaration.valueParameters.size,
            type = booleanType,
            varargElementType = null,
            isCrossinline = false,
            isNoinline = false,
            isHidden = false,
            isAssignable = false
        ).apply {
            parent = declaration
            defaultValue = IrExpressionBodyImpl(
                IrConstImpl.boolean(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    booleanType,
                    true
                )
            )
        }
        declaration.valueParameters += isRootParameter
    }

}