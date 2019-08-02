/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package org.jetbrains.kotlin.backend.konan

import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.ir.createDispatchReceiverParameter
import org.jetbrains.kotlin.backend.common.ir.createParameterDeclarations
import org.jetbrains.kotlin.backend.common.ir.simpleFunctions
import org.jetbrains.kotlin.backend.konan.descriptors.findPackage
import org.jetbrains.kotlin.builtins.functions.FunctionClassDescriptor
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.*
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrClassSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.util.IrProvider
import org.jetbrains.kotlin.ir.util.NaiveSourceBasedFileEntryImpl
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.util.OperatorNameConventions

internal object DECLARATION_ORIGIN_FUNCTION_CLASS : IrDeclarationOriginImpl("DECLARATION_ORIGIN_FUNCTION_CLASS")

internal class BuiltInFictitiousFunctionIrClassFactory(
        var symbolTable: SymbolTable?,
        val irBuiltIns: IrBuiltIns
) : IrProvider {

    override fun getDeclaration(symbol: IrSymbol) =
            (symbol.descriptor as? FunctionClassDescriptor)?.let { buildClass(it) }

    var module: IrModuleFragment? = null
        set(value) {
            if (value == null)
                error("Provide a valid non-null module")
            if (field != null)
                error("Module has already been set")
            field = value
            value.files += filesMap.values
        }

    fun function(n: Int) = buildClass(irBuiltIns.builtIns.builtInsModule.findClassAcrossModuleDependencies(
            ClassId.topLevel(FqName("kotlin.Function$n"))) as FunctionClassDescriptor)

    fun kFunction(n: Int) = buildClass(irBuiltIns.builtIns.builtInsModule.findClassAcrossModuleDependencies(
            ClassId.topLevel(FqName("kotlin.reflect.KFunction$n"))) as FunctionClassDescriptor)

    fun suspendFunction(n: Int) = buildClass(irBuiltIns.builtIns.builtInsModule.findClassAcrossModuleDependencies(
            ClassId.topLevel(FqName("kotlin.coroutines.SuspendFunction$n"))) as FunctionClassDescriptor)

    fun kSuspendFunction(n: Int) = buildClass(irBuiltIns.builtIns.builtInsModule.findClassAcrossModuleDependencies(
            ClassId.topLevel(FqName("kotlin.reflect.KSuspendFunction$n"))) as FunctionClassDescriptor)

    private val functionSymbol = symbolTable!!.referenceClass(
            irBuiltIns.builtIns.builtInsModule.findClassAcrossModuleDependencies(
                    ClassId.topLevel(FqName("kotlin.Function")))!!)

    private val kFunctionSymbol = symbolTable!!.referenceClass(
            irBuiltIns.builtIns.builtInsModule.findClassAcrossModuleDependencies(
                    ClassId.topLevel(FqName("kotlin.reflect.KFunction")))!!)

    private val filesMap = mutableMapOf<PackageFragmentDescriptor, IrFile>()

    private val builtClassesMap = mutableMapOf<FunctionClassDescriptor, IrClass>()

    val builtClasses get() = builtClassesMap.values

    private fun createTypeParameter(descriptor: TypeParameterDescriptor) =
            symbolTable?.declareGlobalTypeParameter(
                    SYNTHETIC_OFFSET, SYNTHETIC_OFFSET, DECLARATION_ORIGIN_FUNCTION_CLASS,
                    descriptor
            )
                    ?: IrTypeParameterImpl(
                            SYNTHETIC_OFFSET, SYNTHETIC_OFFSET, DECLARATION_ORIGIN_FUNCTION_CLASS,
                            descriptor
                    )

    private fun createSimpleFunction(
            descriptor: FunctionDescriptor,
            origin: IrDeclarationOrigin,
            returnType: IrType
    ) = symbolTable?.declareSimpleFunction(
            SYNTHETIC_OFFSET, SYNTHETIC_OFFSET, origin,
            descriptor
    ) {
        IrFunctionImpl(
                SYNTHETIC_OFFSET, SYNTHETIC_OFFSET, origin,
                it,
                returnType
        )
    }
            ?: IrFunctionImpl(
                    SYNTHETIC_OFFSET, SYNTHETIC_OFFSET, origin,
                    descriptor,
                    returnType
            )

    private fun createClass(descriptor: FunctionClassDescriptor) =
            symbolTable?.declareClass(
                    SYNTHETIC_OFFSET, SYNTHETIC_OFFSET, DECLARATION_ORIGIN_FUNCTION_CLASS,
                    descriptor
            )
                    ?: IrClassImpl(
                            SYNTHETIC_OFFSET, SYNTHETIC_OFFSET, DECLARATION_ORIGIN_FUNCTION_CLASS,
                            IrClassSymbolImpl(descriptor)
                    )

    private fun buildClass(descriptor: FunctionClassDescriptor): IrClass =
            builtClassesMap.getOrPut(descriptor) {
                createClass(descriptor).apply {
                    val functionClass = this
                    descriptor.declaredTypeParameters.mapTo(typeParameters) { typeParameterDescriptor ->
                        createTypeParameter(typeParameterDescriptor).also {
                            it.parent = this
                            it.superTypes += irBuiltIns.anyNType
                        }
                    }

                    val descriptorToIrParametersMap = typeParameters.map { it.descriptor to it }.toMap()
                    descriptor.typeConstructor.supertypes.mapTo(superTypes) { superType ->
                        val arguments = superType.arguments.map { argument ->
                            val argumentClassifierDescriptor = argument.type.constructor.declarationDescriptor
                            val argumentClassifierSymbol = argumentClassifierDescriptor?.let { descriptorToIrParametersMap[it] }
                                    ?: error("Unexpected super type argument: $argumentClassifierDescriptor")
                            makeTypeProjection(argumentClassifierSymbol.defaultType, argument.projectionKind)
                        }
                        val superTypeSymbol = when (val superTypeDescriptor = superType.constructor.declarationDescriptor) {
                            is FunctionClassDescriptor -> buildClass(superTypeDescriptor).symbol
                            functionSymbol.descriptor -> functionSymbol
                            kFunctionSymbol.descriptor -> kFunctionSymbol
                            else -> error("Unexpected super type: $superTypeDescriptor")
                        }
                        IrSimpleTypeImpl(superTypeSymbol, superType.isMarkedNullable, arguments, emptyList())
                    }

                    createParameterDeclarations()

                    val invokeFunctionDescriptor = descriptor.unsubstitutedMemberScope.getContributedFunctions(
                            OperatorNameConventions.INVOKE, NoLookupLocation.FROM_BACKEND).single()
                    val isFakeOverride = invokeFunctionDescriptor.kind == CallableMemberDescriptor.Kind.FAKE_OVERRIDE
                    val invokeFunctionOrigin =
                            if (isFakeOverride)
                                IrDeclarationOrigin.FAKE_OVERRIDE
                            else
                                DECLARATION_ORIGIN_FUNCTION_CLASS
                    declarations += createSimpleFunction(
                            invokeFunctionDescriptor, invokeFunctionOrigin,
                            typeParameters.last().defaultType
                    ).apply {
                        parent = functionClass
                        invokeFunctionDescriptor.valueParameters.mapTo(valueParameters) {
                            IrValueParameterImpl(
                                    SYNTHETIC_OFFSET, SYNTHETIC_OFFSET,
                                    invokeFunctionOrigin,
                                    it,
                                    functionClass.typeParameters[it.index].defaultType,
                                    null
                            ).also { it.parent = this }
                        }
                        if (!isFakeOverride)
                            createDispatchReceiverParameter(invokeFunctionOrigin)
                        else {
                            val overriddenFunction = superTypes
                                    .mapNotNull { it.classOrNull?.owner }
                                    .single { it.descriptor is FunctionClassDescriptor }
                                    .simpleFunctions()
                                    .single()
                            overriddenSymbols += overriddenFunction.symbol
                            dispatchReceiverParameter = overriddenFunction.dispatchReceiverParameter?.copyTo(this)
                        }
                    }

                    val packageFragmentDescriptor = descriptor.findPackage()
                    val file = filesMap.getOrPut(packageFragmentDescriptor) {
                        IrFileImpl(NaiveSourceBasedFileEntryImpl("[K][Suspend]Functions"), packageFragmentDescriptor).also {
                            this@BuiltInFictitiousFunctionIrClassFactory.module?.files?.add(it)
                        }
                    }
                    parent = file
                    file.declarations += this
                }
            }
}