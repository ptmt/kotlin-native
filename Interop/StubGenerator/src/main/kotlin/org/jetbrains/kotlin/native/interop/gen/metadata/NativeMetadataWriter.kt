package org.jetbrains.kotlin.native.interop.gen.metadata

import kotlinx.metadata.impl.PackageWriter
import org.jetbrains.kotlin.konan.CURRENT
import org.jetbrains.kotlin.konan.KonanVersion
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.konan.library.defaultResolver
import org.jetbrains.kotlin.konan.library.impl.KonanLibraryWriterImpl
import org.jetbrains.kotlin.konan.properties.Properties
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.library.KonanLibraryVersioning
import org.jetbrains.kotlin.library.KotlinAbiVersion
import org.jetbrains.kotlin.library.SerializedMetadata
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.konan.KonanProtoBuf
import org.jetbrains.kotlin.native.interop.gen.StubIrContext
import org.jetbrains.kotlin.serialization.StringTableImpl

/**
 * Idea:
 * StubIR -> kotlinx.metadata -> protobuf -> klib
 */

class NativePackageWriter(
        private val context: StubIrContext,
        private val stringTable: StringTableImpl = StringTableImpl()
) : PackageWriter(stringTable) {
    fun write(): SerializedMetadata {
        val libraryProto = KonanProtoBuf.LinkDataLibrary.newBuilder()
        libraryProto.moduleName = "<hello>"

        // empty root
        val root = ""
        val rootFragments = listOf(buildFragment(null, "").toByteArray())
        libraryProto.addPackageFragmentName(root)

        val packageName = if (context.configuration.pkgName.isEmpty()) "lib" else context.createPackageName(context.configuration.pkgName)
        val packageFragments = listOf(buildFragment(t.build(), packageName).toByteArray())
        libraryProto.addPackageFragmentName(packageName)

        val packages = listOf(rootFragments, packageFragments)
        val packageNames = listOf(root, packageName)

        val libraryProtoBytes = libraryProto.build().toByteArray()
        return SerializedMetadata(libraryProtoBytes, packages, packageNames)
    }

    private fun buildFragment(
            packageProto: ProtoBuf.Package?,
            fqName: String
    ): KonanProtoBuf.LinkDataPackageFragment {
        val (stringTableProto, nameTableProto) = stringTable.buildProto()
        val classesProto = KonanProtoBuf.LinkDataClasses.newBuilder().build()
        return KonanProtoBuf.LinkDataPackageFragment.newBuilder()
                .setFqName(fqName)
                .setClasses(classesProto)
                .setPackage(packageProto ?: ProtoBuf.Package.newBuilder().build())
                .setStringTable(stringTableProto)
                .setNameTable(nameTableProto)
                .setIsEmpty(packageProto == null)
                .build()
    }

//    private fun buildPackageProto(packageFqName: String, packageProto: ProtoBuf.Package) {
////        setExtension(protocol.packageFqName, stringTable.getPackageFqNameIndex(packageFqName))
//    }
}

fun buildInteropKlib(
        outputDir: String,
        metadata: SerializedMetadata,
        manifest: Properties,
        moduleName: String,
        target: KonanTarget,
        bitcodeFile: String
) {
    val version = KonanLibraryVersioning(
            null,
            abiVersion = KotlinAbiVersion.CURRENT,
            compilerVersion = KonanVersion.CURRENT
    )
    val klibFile = File(outputDir, moduleName)

    val repositories: List<String> = listOf("stdlib")
    val resolver = defaultResolver(repositories, target)
    val defaultLinks = resolver.defaultLinks(false, true)
    println("Link deps: ${defaultLinks.joinToString { it.libraryName }}")
    KonanLibraryWriterImpl(klibFile, moduleName, version, target).apply {
        addLinkDependencies(defaultLinks)
        addMetadata(metadata)
        addManifestAddend(manifest)
        addNativeBitcode(bitcodeFile)
        commit()
    }
}

