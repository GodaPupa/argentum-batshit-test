package com.wingedsheep.gym.ferocity

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.net.URLClassLoader
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

private const val MAX_BUNDLE_BYTES = 32L * 1024 * 1024

/** Exclusive durable files; a partial failed write remains evidence and is never overwritten. */
internal fun ferocityWriteNew(path: Path, bytes: ByteArray) {
    require(path.isAbsolute && Files.isDirectory(path.parent, LinkOption.NOFOLLOW_LINKS)) {
        "An existing absolute evidence directory is required"
    }
    requireNoSymbolicPath(path.parent)
    FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) require(channel.write(buffer) > 0) { "Incomplete evidence write" }
        channel.force(true)
    }
    FileChannel.open(path.parent, StandardOpenOption.READ).use { it.force(true) }
}

internal fun <T> ferocityWriteNewJson(path: Path, serializer: KSerializer<T>, value: T): String {
    val raw = FerocityJournalCodec.canonical(serializer, value)
    ferocityWriteNew(path, raw.toByteArray(Charsets.UTF_8))
    return FerocityJournalCodec.sha(raw)
}

internal fun <T> ferocityReadExactJson(path: Path, expectedSha256: String, serializer: KSerializer<T>): T {
    requireSha256(expectedSha256)
    require(path.isAbsolute)
    requireNoSymbolicPath(path)
    require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) in 1..MAX_BUNDLE_BYTES) {
        "Missing, nonregular or oversized exact runtime artifact"
    }
    val raw = Files.readString(path, Charsets.UTF_8)
    require(FerocityJournalCodec.sha(raw) == expectedSha256) { "Exact runtime artifact digest mismatch: $path" }
    val value = FerocityJournalCodec.json.decodeFromString(serializer, raw)
    require(FerocityJournalCodec.canonical(serializer, value) == raw) { "Noncanonical or migrated runtime artifact" }
    return value
}

internal fun writeFerocityBundle(path: Path, bundle: FerocityDefinitionBundle): String =
    ferocityWriteNewJson(path, FerocityDefinitionBundle.serializer(), bundle)

internal fun readFerocityBundle(path: Path, expectedSha256: String, pins: FerocitySourcePins): FerocityRestoredDefinitions {
    require(pins.dependencySha256[FEROCITY_BUNDLE_DEPENDENCY] == expectedSha256) { "Bundle file is not admitted" }
    return restoreFerocityDefinitions(ferocityReadExactJson(path, expectedSha256, FerocityDefinitionBundle.serializer()), pins)
}

@Serializable
internal data class FerocityRuntimePathPin(
    val path: String,
    val directory: Boolean,
    val sha256: String,
    /** Relative path -> exact bytes; present only for directories, never normalized class files. */
    val files: Map<String, String>,
)

internal fun ferocityFileSha256(path: Path): String {
    val hash = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(65536)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            hash.update(buffer, 0, count)
        }
    }
    return hash.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}

internal fun captureFerocityRuntimePath(path: Path): FerocityRuntimePathPin {
    require(path.isAbsolute)
    requireNoSymbolicPath(path)
    require(path.normalize() == path) { "Classpath paths must already be absolute and normalized" }
    if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        return FerocityRuntimePathPin(path.toString(), false, ferocityFileSha256(path), emptyMap())
    require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) { "Missing or unsupported classpath entry: $path" }
    val files = sortedMapOf<String, String>()
    Files.walk(path).use { stream ->
        stream.forEach { item ->
            require(!Files.isSymbolicLink(item)) { "Symbolic classpath content is not admitted: $item" }
            when {
                Files.isRegularFile(item, LinkOption.NOFOLLOW_LINKS) -> {
                    require(files.size < 250000) { "Classpath directory exceeds bounded manifest size" }
                    files[path.relativize(item).toString().replace('\\', '/')] = ferocityFileSha256(item)
                }
                !Files.isDirectory(item, LinkOption.NOFOLLOW_LINKS) -> error("Unsupported classpath object: $item")
            }
        }
    }
    val digest = FerocityJournalCodec.sha(FerocityJournalCodec.canonical(MapSerializer(String.serializer(), String.serializer()), files))
    return FerocityRuntimePathPin(path.toString(), true, digest, files)
}

/** Preserve actual classpath ordering; URL ancestry covers Gradle's isolated test loader. */
internal fun currentFerocityClassPath(): List<Path> {
    val result = linkedSetOf<Path>()
    val system = requireNotNull(System.getProperty("java.class.path"))
    system.split(java.io.File.pathSeparator).forEach { text ->
        require(text.isNotBlank()) { "Implicit working-directory classpath entry is not admitted" }
        result.add(Path.of(text).toAbsolutePath().normalize())
    }
    val visited = mutableSetOf<ClassLoader>()
    listOf(Thread.currentThread().contextClassLoader, FerocityRuntimePathPin::class.java.classLoader).forEach { root ->
        var loader: ClassLoader? = root
        while (loader != null && visited.add(loader)) {
            val current = loader
            if (current is URLClassLoader) current.urLs.forEach { url ->
                require(url.protocol == "file") { "Non-file classpath URL is not admitted: $url" }
                result.add(Path.of(url.toURI()).toAbsolutePath().normalize())
            }
            loader = current.parent
        }
    }
    require(result.isNotEmpty())
    return result.toList()
}

internal fun ferocityClassPathDigest(paths: List<FerocityRuntimePathPin>): String =
    FerocityJournalCodec.sha(FerocityJournalCodec.canonical(ListSerializer(FerocityRuntimePathPin.serializer()), paths))

internal fun verifyFerocityClassPath(paths: List<FerocityRuntimePathPin>, pins: FerocitySourcePins) {
    require(paths.isNotEmpty() && paths.size <= 1024 && paths.map { it.path }.distinct().size == paths.size)
    require(pins.dependencySha256[FEROCITY_CLASSPATH_DEPENDENCY] == ferocityClassPathDigest(paths)) {
        "Runtime classpath manifest is not admitted"
    }
    require(currentFerocityClassPath().map(Path::toString) == paths.map { it.path }) { "Actual ordered classpath differs from admission" }
    paths.forEach { expected ->
        require(captureFerocityRuntimePath(Path.of(expected.path)) == expected) { "Classpath artifact changed: ${expected.path}" }
    }
}

private fun requireNoSymbolicPath(path: Path) {
    var current: Path? = path
    while (current != null) {
        require(!Files.isSymbolicLink(current)) { "Symbolic runtime artifact path is not admitted: $current" }
        current = current.parent
    }
}
