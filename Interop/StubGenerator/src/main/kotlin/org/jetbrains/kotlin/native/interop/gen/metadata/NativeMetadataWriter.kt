package org.jetbrains.kotlin.native.interop.gen.metadata

import kotlinx.metadata.impl.PackageWriter
import org.jetbrains.kotlin.serialization.StringTableImpl

/**
 * Idea:
 * StubIR -> kotlinx.metadata -> protobuf -> klib
 */

class NativePackageWriter : PackageWriter(StringTableImpl()) {

}