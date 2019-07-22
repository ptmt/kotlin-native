package org.jetbrains.kotlin.native.interop.gen.metadata

import kotlinx.metadata.*
import kotlinx.metadata.impl.PackageWriter
import kotlinx.metadata.impl.ReadContext
import kotlinx.metadata.impl.WriteContext
import kotlinx.metadata.impl.extensions.*
import org.jetbrains.kotlin.konan.CURRENT
import org.jetbrains.kotlin.konan.KonanVersion
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.konan.properties.Properties
import org.jetbrains.kotlin.library.KonanLibraryVersioning
import org.jetbrains.kotlin.library.KotlinAbiVersion
import org.jetbrains.kotlin.library.SerializedMetadata
import org.jetbrains.kotlin.library.impl.KoltinLibraryWriterImpl
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.serialization.StringTableImpl

/**
 * Idea:
 * StubIR -> kotlinx.metadata -> protobuf -> klib
 */

class NativePackageWriter : PackageWriter(StringTableImpl()) {
    fun write(): SerializedMetadata =
            SerializedMetadata(t.build().toByteArray(), emptyList(), emptyList())
}

fun buildKlib(
        outputDir: String,
        metadata: SerializedMetadata,
        manifest: Properties,
        moduleName: String) {
    val version = KonanLibraryVersioning(
            "META_INTEROP",
            abiVersion = KotlinAbiVersion.CURRENT,
            compilerVersion = KonanVersion.CURRENT
    )
    val klibFile = File(outputDir, "test.klib")
    val writer = KoltinLibraryWriterImpl(klibFile, moduleName, version)
    writer.addMetadata(metadata)
    writer.addManifestAddend(manifest)
    writer.commit()
}

