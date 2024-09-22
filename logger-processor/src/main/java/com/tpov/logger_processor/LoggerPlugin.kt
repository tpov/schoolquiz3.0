//package com.tpov.logger_processor
//
//import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
//import org.jetbrains.kotlin.config.CompilerConfiguration
//import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
//import org.jetbrains.kotlin.ir.declarations.IrFunction
//import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
//import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
//import org.jetbrains.kotlin.ir.visitors.acceptVoid
//import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
//import org.jetbrains.kotlin.backend.jvm.codegen.AnnotationCodegen.Companion.annotationClass
//import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
//import org.jetbrains.kotlin.ir.expressions.IrCall
//import org.jetbrains.kotlin.ir.expressions.IrLoop
//import org.jetbrains.kotlin.ir.expressions.IrWhen
//import org.jetbrains.kotlin.psi.KtCallExpression
//import org.jetbrains.kotlin.psi.KtForExpression
//import org.jetbrains.kotlin.psi.KtIfExpression
//import org.jetbrains.kotlin.psi.KtNamedFunction
//import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
//import org.jetbrains.kotlin.psi.KtVisitorVoid
//
//@OptIn(ExperimentalCompilerApi::class)
//class LoggerPlugin : CompilerPluginRegistrar() {
//    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
//        IrGenerationExtension.registerExtension(LoggerIrGenerationExtension())
//    }
//
//    override val supportsK2: Boolean = true
//}
//
//class LoggerIrGenerationExtension : IrGenerationExtension {
//    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
//        moduleFragment.acceptVoid(object : IrElementVisitorVoid {
//            override fun visitFunction(declaration: IrFunction) {
//                if (declaration.annotations.any { it.annotationClass.name.asString() == "Logger" }) {
//                    println("Аннотированная функция: ${declaration.name}")
//
//                    declaration.body?.acceptVoid(object : IrElementVisitorVoid {
//                        override fun visitCall(expression: IrCall) {
//                            println("Вызов функции: ${expression.symbol.owner.name}")
//                            super.visitCall(expression)
//                        }
//
//                        override fun visitWhen(expression: IrWhen) {
//                            println("Условие (if): ${expression.type}")
//                            super.visitWhen(expression)
//                        }
//
//                        override fun visitLoop(loop: IrLoop) {
//                            println("Цикл найден: ${loop.startOffset}")
//                            super.visitLoop(loop)
//                        }
//                    })
//                }
//            }
//        })
//    }
//}
//class TreeLoggerVisitor : KtVisitorVoid() {
//    private var indent = ""
//
//    override fun visitNamedFunction(function: KtNamedFunction) {
//        println("${indent}Function: ${function.name}")
//        indent += "  "
//
//        // Логируем параметры
//        function.valueParameters.forEach { param ->
//            println("${indent}Param: ${param.name}")
//        }
//
//        function.bodyExpression?.accept(object : KtTreeVisitorVoid() {
//            override fun visitIfExpression(expression: KtIfExpression) {
//                println("${indent}If condition: ${expression.condition?.text}")
//                super.visitIfExpression(expression)
//            }
//
//            override fun visitForExpression(expression: KtForExpression) {
//                println("${indent}For loop: ${expression.loopParameter?.text}")
//                super.visitForExpression(expression)
//            }
//
//            override fun visitCallExpression(expression: KtCallExpression) {
//                println("${indent}Call: ${expression.calleeExpression?.text}")
//                super.visitCallExpression(expression)
//            }
//        })
//        indent = indent.dropLast(2) // Уменьшаем отступ
//    }
//}