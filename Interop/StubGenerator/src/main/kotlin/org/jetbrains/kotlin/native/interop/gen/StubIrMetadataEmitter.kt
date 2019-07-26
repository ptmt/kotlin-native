package org.jetbrains.kotlin.native.interop.gen

import kotlinx.metadata.*
import org.jetbrains.kotlin.native.interop.gen.metadata.annotations
import org.jetbrains.kotlin.utils.addIfNotNull

/**
 * Emits [kotlinx.metadata] which can be easily translated to protobuf.
 */
class StubIrMetadataEmitter(
        private val builderResult: StubIrBuilderResult
) {
    fun emit(): KmPackage =
            mapper.visitSimpleStubContainer(builderResult.stubs, null)

    private val mapper = object : StubIrVisitor<StubContainer?, Any> {
        override fun visitClass(element: ClassStub, data: StubContainer?) {
        }

        override fun visitTypealias(element: TypealiasStub, data: StubContainer?) {

        }

        override fun visitFunction(element: FunctionStub, data: StubContainer?): KmFunction =
            KmFunction(
                    flagsOf(*element.getFlags()),
                    element.name
            ).apply {
                returnType = element.returnType.map()
                valueParameters += element.parameters.map { mapValueParameter(it) }
                typeParameters += element.typeParameters.map { mapTypeParameter(it) }
                annotations += element.annotations.map { mapAnnotation(it) }
            }

        override fun visitProperty(element: PropertyStub, data: StubContainer?) {

        }

        override fun visitConstructor(constructorStub: ConstructorStub, data: StubContainer?) {

        }

        override fun visitPropertyAccessor(propertyAccessor: PropertyAccessor, data: StubContainer?) {

        }

        override fun visitSimpleStubContainer(simpleStubContainer: SimpleStubContainer, data: StubContainer?): KmPackage {
//            simpleStubContainer.classes.forEach {
//                it.accept(this, simpleStubContainer)
//            }
            val functions = simpleStubContainer.functions.map {
                it.accept(this, simpleStubContainer) as KmFunction
            }
//            simpleStubContainer.properties.forEach {
//                it.accept(this, simpleStubContainer)
//            }
//            simpleStubContainer.typealiases.forEach {
//                it.accept(this, simpleStubContainer)
//            }
//            simpleStubContainer.simpleContainers.forEach {
//                it.accept(this, simpleStubContainer)
//            }
            return KmPackage().apply {
                this.functions += functions
            }
        }

        private fun StubType.map(): KmType = when (this) {
            is WrapperStubType -> {
                KmType(flags = flagsOf(*getFlags())).apply {
                    val fqName = this@map.kotlinType.classifier.fqName.convertFqName()
                    classifier = KmClassifier.Class(fqName)
                }
            }
            is ClassifierStubType -> {
                KmType(flags = flagsOf(*getFlags())).apply {
                    val fqName = this@map.classifier.fqName.convertFqName()
                    classifier = KmClassifier.Class(fqName)
                    arguments += this@map.typeArguments.map { mapTypeArgument(it) }
                }
            }
            is RuntimeStubType -> {
                KmType(flags = flagsOf(*getFlags())).apply {
                    classifier = KmClassifier.Class(this@map.fqName.convertFqName())
                }
            }
            is TypeParameterStubType -> {
                KmType(flags = flagsOf(*getFlags())).apply {
                    classifier = KmClassifier.TypeParameter(id = 0)
                }
            }
            is NestedStubType -> {
                KmType(flags = flagsOf(*getFlags())).apply {
                    classifier = KmClassifier.Class(this@map.fqName.convertFqName())
                }
            }
        }

        private fun String.convertFqName() = replace('.', '/')

        private fun FunctionStub.getFlags(): Array<Flag> = listOfNotNull(
                Flag.Common.IS_PUBLIC,
                Flag.Function.IS_EXTERNAL,
                Flag.HAS_ANNOTATIONS
        ).toTypedArray()

        private fun StubType.getFlags(): Array<Flag> = listOfNotNull(
                if (nullable) Flag.Type.IS_NULLABLE else null
        ).toTypedArray()


        private fun mapValueParameter(parameter: FunctionParameterStub): KmValueParameter =
                KmValueParameter(
                        flags = 0,
                        name = parameter.name
                ).apply {
                    parameter.type.map().let {
                        if (parameter.isVararg) {
                            varargElementType = it
                        } else {
                            type = it
                        }
                    }

                }

        private fun mapTypeArgument(typeArgumentStub: TypeArgument): KmTypeProjection = when (typeArgumentStub) {
            is TypeArgumentStub -> KmTypeProjection(KmVariance.INVARIANT, typeArgumentStub.type.map())
            is TypeArgumentStub.StarProjection -> KmTypeProjection.STAR
            else -> error("Unexpected type projection: $typeArgumentStub")
        }


        private fun mapTypeParameter(typeParameterStub: TypeParameterStub): KmTypeParameter =
                KmTypeParameter(
                        flags = 0,
                        name = typeParameterStub.name,
                        id = 0,
                        variance = KmVariance.INVARIANT
                ).apply {
                    upperBounds.addIfNotNull(typeParameterStub.upperBound?.map())
                }

        private fun mapAnnotation(annotationStub: AnnotationStub): KmAnnotation = when (annotationStub) {
            AnnotationStub.ObjC.ConsumesReceiver -> TODO()
            AnnotationStub.ObjC.ReturnsRetained -> TODO()
            is AnnotationStub.ObjC.Method -> TODO()
            is AnnotationStub.ObjC.Factory -> TODO()
            AnnotationStub.ObjC.Consumed -> TODO()
            is AnnotationStub.ObjC.Constructor -> TODO()
            is AnnotationStub.ObjC.ExternalClass -> TODO()
            AnnotationStub.CCall.CString -> TODO()
            AnnotationStub.CCall.WCString -> TODO()
            is AnnotationStub.CCall.Symbol ->
                KmAnnotation(
                        "kotlinx/cinterop/internal/CCall",
                        mapOf(
                              "id" to KmAnnotationArgument.StringValue(annotationStub.symbolName)
                        )
                )
            is AnnotationStub.CCall.GetMemberAt -> TODO()
            is AnnotationStub.CCall.SetMemberAt -> TODO()
            is AnnotationStub.CStruct -> TODO()
            is AnnotationStub.CNaturalStruct -> TODO()
            is AnnotationStub.CLength -> TODO()
            is AnnotationStub.Deprecated -> TODO()
            is AnnotationStub.ReadBits -> TODO()
            is AnnotationStub.WriteBits -> TODO()
        }
    }
}