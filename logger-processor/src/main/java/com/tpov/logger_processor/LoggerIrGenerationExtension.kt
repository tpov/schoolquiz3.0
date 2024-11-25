package com.tpov.logger_processor

import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockBodyImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.name.FqName

class LoggerIrGenerationExtension : IrGenerationExtension {
    private val loggerAnnotationFqName = FqName("com.tpov.log_api.logger.Logger")

    @OptIn(FirIncompatiblePluginAPI::class)
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val logClass = pluginContext.referenceClass(FqName("android.util.Log"))
            ?: throw IllegalStateException("Cannot find android.util.Log class in Android SDK")

        val logDSymbol = logClass.owner.functions.firstOrNull {
            it.name.asString() == "d" && it.valueParameters.size == 2
        }?.symbol
            ?: throw IllegalStateException("Cannot find Log.d function with two parameters in Android SDK")

        val loggerAnnotatedClasses = mutableSetOf<IrClass>()

        moduleFragment.accept(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildren(this, null)
            }

            override fun visitClass(declaration: IrClass) {
                if (declaration.annotations.any { it.type.classFqName == loggerAnnotationFqName }) {
                    loggerAnnotatedClasses.add(declaration)
                }
                super.visitClass(declaration)
            }
        }, null)

        // Затем обрабатываем функции внутри этих классов
        moduleFragment.accept(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildren(this, null)
            }

            override fun visitFunction(declaration: IrFunction) {
                val parentClass = declaration.parent as? IrClass
                if (parentClass != null && loggerAnnotatedClasses.contains(parentClass)) {
                    instrumentFunction(declaration, pluginContext, logDSymbol)
                }
                super.visitFunction(declaration)
            }
        }, null)
    }

    private fun instrumentFunction(
        declaration: IrFunction,
        pluginContext: IrPluginContext,
        logDSymbol: IrSimpleFunctionSymbol
    ) {
        // Создаем вызов для увеличения счетчика глубины
        val incrementDepth = createDepthIncrement(pluginContext)

        // Создаем вызов для уменьшения счетчика глубины
        val decrementDepth = createDepthDecrement(pluginContext)

        // Создаем логирование при входе в функцию
        val enterMessage = createIndentedMessage(
            pluginContext,
            "Entering function: ${declaration.name}"
        )
        val logEnter = createLogCall(
            pluginContext,
            logDSymbol,
            "LoggerTag",
            enterMessage
        )

        // Создаем логирование при выходе из функции
        val exitMessage = createIndentedMessage(
            pluginContext,
            "Exiting function: ${declaration.name}"
        )
        val logExit = createLogCall(
            pluginContext,
            logDSymbol,
            "LoggerTag",
            exitMessage
        )

        val body = declaration.body
        when (body) {
            is IrBlockBody -> {
                body.statements.add(0, incrementDepth)
                body.statements.add(1, logEnter)
                body.statements.add(logExit)
                body.statements.add(decrementDepth)
            }
            is IrExpressionBody -> {
                declaration.body = IrBlockBodyImpl(
                    body.startOffset, body.endOffset,
                    listOf(
                        incrementDepth,
                        logEnter,
                        body.expression,
                        logExit,
                        decrementDepth
                    )
                )
            }
        }
    }

    @OptIn(FirIncompatiblePluginAPI::class)
    private fun createDepthDecrement(pluginContext: IrPluginContext): IrStatement {
        val threadLocalClass = pluginContext.referenceClass(FqName("java.lang.ThreadLocal"))
            ?: throw IllegalStateException("Cannot find java.lang.ThreadLocal class")

        val getMethodSymbol = threadLocalClass.owner.functions.first { it.name.asString() == "get" }.symbol
        val setMethodSymbol = threadLocalClass.owner.functions.first { it.name.asString() == "set" }.symbol

        val loggerDepthClass = pluginContext.referenceClass(FqName("com.tpov.log_api.logger.LoggerDepth"))
            ?: throw IllegalStateException("Cannot find LoggerDepth class")

        val depthFieldSymbol = loggerDepthClass.owner.declarations.filterIsInstance<IrProperty>().firstOrNull {
            it.name.asString() == "depth"
        }?.backingField?.symbol
            ?: throw IllegalStateException("Cannot find depth field in LoggerDepth class")

        // Получаем LoggerDepth.depth
        val depthFieldAccess = IrGetFieldImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = threadLocalClass.defaultType,
            symbol = depthFieldSymbol,
            receiver = null
        )

        // Вызываем depth.get()
        val getDepthCall = IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = pluginContext.irBuiltIns.anyNType,
            symbol = getMethodSymbol,
            typeArgumentsCount = 0,
            valueArgumentsCount = 0
        ).apply {
            dispatchReceiver = depthFieldAccess
        }

        // Приводим к Int
        val currentDepth = IrTypeOperatorCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = pluginContext.irBuiltIns.intType,
            operator = IrTypeOperator.IMPLICIT_CAST,
            typeOperand = pluginContext.irBuiltIns.intType,
            argument = getDepthCall
        )
        val intMinusFunction = pluginContext.referenceFunctions(FqName("kotlin.Int.minus"))
            .firstOrNull { it.owner.valueParameters.size == 1 }
            ?: throw IllegalStateException("Cannot find kotlin.Int.minus function")

        // Уменьшаем currentDepth на 1
        val newDepth = IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = pluginContext.irBuiltIns.intType,
            symbol = intMinusFunction,
            typeArgumentsCount = 0,
            valueArgumentsCount = 1
        ).apply {
            dispatchReceiver = currentDepth
            putValueArgument(0, IrConstImpl.int(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                type = pluginContext.irBuiltIns.intType,
                value = 1
            ))
        }

        // Вызываем depth.set(newDepth)
        val setDepthCall = IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = pluginContext.irBuiltIns.unitType,
            symbol = setMethodSymbol,
            typeArgumentsCount = 0,
            valueArgumentsCount = 1
        ).apply {
            dispatchReceiver = depthFieldAccess
            putValueArgument(0, newDepth)
        }

        return setDepthCall
    }

    val depth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }
    @OptIn(FirIncompatiblePluginAPI::class)
    private fun createDepthIncrement(pluginContext: IrPluginContext): IrStatement {
        val threadLocalClass = pluginContext.referenceClass(FqName("java.lang.ThreadLocal"))
            ?: throw IllegalStateException("Cannot find java.lang.ThreadLocal class")

        val getMethodSymbol = threadLocalClass.owner.functions.first { it.name.asString() == "get" }.symbol
        val setMethodSymbol = threadLocalClass.owner.functions.first { it.name.asString() == "set" }.symbol


        val loggerDepthClass = pluginContext.referenceClass(FqName("com.tpov.log_api.logger.LoggerDepth"))
            ?: throw IllegalStateException("Cannot find LoggerDepth class")

        val companionObject = loggerDepthClass.owner.declarations.filterIsInstance<IrClass>().firstOrNull {
            it.isCompanion
        } ?: throw IllegalStateException("Cannot find companion object in LoggerDepth class")

        val depthFieldSymbol = companionObject.declarations.filterIsInstance<IrProperty>().firstOrNull {
            it.name.asString() == "depth"
        }?.backingField?.symbol
            ?: throw IllegalStateException("Cannot find depth field in LoggerDepth class")


        // Получаем LoggerDepth.depth
        val depthFieldAccess = IrGetFieldImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = threadLocalClass.defaultType,
            symbol = depthFieldSymbol,
            receiver = null // Так как поле статическое (в объекте)
        )

        // Вызываем depth.get()
        val getDepthCall = IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = pluginContext.irBuiltIns.anyNType, // ThreadLocal.get(): Any?
            symbol = getMethodSymbol,
            typeArgumentsCount = 0,
            valueArgumentsCount = 0
        ).apply {
            dispatchReceiver = depthFieldAccess
        }

        // Приводим к Int
        val currentDepth = IrTypeOperatorCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = pluginContext.irBuiltIns.intType,
            operator = IrTypeOperator.IMPLICIT_CAST,
            typeOperand = pluginContext.irBuiltIns.intType,
            argument = getDepthCall
        )

        // Увеличиваем currentDepth на 1
        val newDepth = IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = pluginContext.irBuiltIns.intType,
            symbol = pluginContext.irBuiltIns.intPlusSymbol,
            typeArgumentsCount = 0,
            valueArgumentsCount = 1
        ).apply {
            dispatchReceiver = currentDepth
            putValueArgument(0, IrConstImpl.int(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                type = pluginContext.irBuiltIns.intType,
                value = 1
            ))
        }

        // Вызываем depth.set(newDepth)
        val setDepthCall = IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = pluginContext.irBuiltIns.unitType,
            symbol = setMethodSymbol,
            typeArgumentsCount = 0,
            valueArgumentsCount = 1
        ).apply {
            dispatchReceiver = depthFieldAccess
            putValueArgument(0, newDepth)
        }

        return setDepthCall
    }


    private fun createLogCall(
        pluginContext: IrPluginContext,
        logDSymbol: IrSimpleFunctionSymbol,
        tag: String,
        messageExpression: IrExpression
    ): IrCall {
        return IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = pluginContext.irBuiltIns.unitType,
            symbol = logDSymbol,
            typeArgumentsCount = 0,
            valueArgumentsCount = 2
        ).apply {
            putValueArgument(0, IrConstImpl.string(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                type = pluginContext.irBuiltIns.stringType,
                value = tag
            ))
            putValueArgument(1, messageExpression)
        }
    }
    @OptIn(FirIncompatiblePluginAPI::class)
    private fun createIndentedMessage(
        pluginContext: IrPluginContext,
        message: String
    ): IrExpression {
        val threadLocalClass = pluginContext.referenceClass(FqName("java.lang.ThreadLocal"))
            ?: throw IllegalStateException("Cannot find java.lang.ThreadLocal class")
        val getMethodSymbol = threadLocalClass.owner.functions.first { it.name.asString() == "get" }.symbol

        val loggerDepthClass = pluginContext.referenceClass(FqName("com.tpov.log_api.logger.LoggerDepth"))
            ?: throw IllegalStateException("Cannot find LoggerDepth class")

        val depthFieldSymbol = loggerDepthClass.owner.declarations.filterIsInstance<IrProperty>().firstOrNull {
            it.name.asString() == "depth"
        }?.backingField?.symbol
            ?: throw IllegalStateException("Cannot find depth field in LoggerDepth class")

        // Получаем LoggerDepth.depth.get()
        val getDepthCall = IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = pluginContext.irBuiltIns.anyNType,
            symbol = getMethodSymbol,
            typeArgumentsCount = 0,
            valueArgumentsCount = 0
        ).apply {
            dispatchReceiver = IrGetFieldImpl(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                type = threadLocalClass.defaultType,
                symbol = depthFieldSymbol,
                receiver = null
            )
        }

        // Приводим к Int
        val currentDepth = IrTypeOperatorCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = pluginContext.irBuiltIns.intType,
            operator = IrTypeOperator.IMPLICIT_CAST,
            typeOperand = pluginContext.irBuiltIns.intType,
            argument = getDepthCall
        )

        // Вызываем "  ".repeat(depth)
        val repeatFunction = pluginContext.referenceFunctions(FqName("kotlin.text.StringsKt.repeat")).first {
            it.owner.valueParameters.size == 2
        }

        val repeatedSpaces = IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = pluginContext.irBuiltIns.stringType,
            symbol = repeatFunction,
            typeArgumentsCount = 0,
            valueArgumentsCount = 2
        ).apply {
            putValueArgument(0, IrConstImpl.string(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                type = pluginContext.irBuiltIns.stringType,
                value = "  "
            ))
            putValueArgument(1, currentDepth)
        }
        val plusFunctionSymbol = pluginContext.referenceFunctions(FqName("kotlin.text.StringsKt.plus"))
            .firstOrNull { it.owner.valueParameters.size == 1 }
            ?: throw IllegalStateException("Cannot find kotlin.text.StringsKt.plus function")

        // Объединяем отступы и сообщение
        val messageExpression = IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = pluginContext.irBuiltIns.stringType,
            symbol = plusFunctionSymbol,
            typeArgumentsCount = 0,
            valueArgumentsCount = 1
        ).apply {
            dispatchReceiver = repeatedSpaces
            putValueArgument(0, IrConstImpl.string(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                type = pluginContext.irBuiltIns.stringType,
                value = message
            ))
        }

        return messageExpression
    }


}
