package org.jetbrains.kotlin.native.interop.gen.metadata

import kotlinx.metadata.KmClassExtensionVisitor
import kotlinx.metadata.KmExtensionType
import kotlinx.metadata.impl.extensions.KmClassExtension

class NativeClassExtensions: KmClassExtension {
    override val type: KmExtensionType
        get() = TODO("not implemented")

    override fun accept(visitor: KmClassExtensionVisitor) {
        TODO("not implemented")
    }
}