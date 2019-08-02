package org.jetbrains.kotlin.backend.konan.serialization

import org.jetbrains.kotlin.backend.common.serialization.*
import org.jetbrains.kotlin.builtins.functions.FunctionClassDescriptor
import org.jetbrains.kotlin.builtins.functions.FunctionInvokeDescriptor
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.name.FqName

// This is all information needed to find a descriptor in the
// tree of deserialized descriptors. Think of it as base + offset.
// packageFqName + classFqName + index allow to localize some deserialized descriptor.
// Then the rest of the fields allow to find the needed descriptor relative to the one with index.
class KonanDescriptorReferenceDeserializer(
    currentModule: ModuleDescriptor,
    mangler: KotlinMangler,
    val builtIns: IrBuiltIns,
    resolvedForwardDeclarations: MutableMap<UniqIdKey, UniqIdKey>)
    : DescriptorReferenceDeserializer(currentModule, mangler, resolvedForwardDeclarations),
      DescriptorUniqIdAware by KonanDescriptorUniqIdAware{

    override fun resolveSpecialDescriptor(fqn: FqName) = builtIns.builtIns.getBuiltInClassByFqName(fqn)

    override fun checkIfSpecialDescriptorId(id: Long) =
            with (mangler) { id.isSpecial }

    override fun getDescriptorIdOrNull(descriptor: DeclarationDescriptor) =
            if (isBuiltInFunction(descriptor))
            {
                val uniqName = when (descriptor) {
                    is FunctionClassDescriptor -> KotlinMangler.functionClassSymbolName(descriptor.name)
                    is FunctionInvokeDescriptor -> KotlinMangler.functionInvokeSymbolName(descriptor.containingDeclaration.name)
                    else -> error("Unexpected descriptor type: $descriptor")
                }
                with (mangler) { uniqName.hashMangle }
            }
            else null

}
