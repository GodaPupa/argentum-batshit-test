package com.wingedsheep.gym.ferocity

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path

/** Kernel process identity, independent of the PID namespace used by ProcessHandle.current().pid(). */
internal data class FerocityProcIdentity(
    val pid: Long,
    val parentPid: Long,
    val namespacePids: List<Long>,
    val userIds: List<Long>,
    val startTicks: Long,
)

internal data class FerocityOwnedProcessFacts(
    val identity: FerocityProcIdentity,
    val executable: String,
    val executableSha256: String,
    /** Complete NUL-delimited OS argv, including argv[0]; no shell reconstruction or truncation. */
    val argv: List<String>,
)

internal data class FerocitySupervisorProcessFacts(
    val child: FerocityOwnedProcessFacts,
    val parent: FerocityOwnedProcessFacts,
)

/** Optional JVM facts remain binding whenever present; absence never excuses a mismatch. */
internal data class FerocityJvmProcessFacts(
    val pid: Long,
    val command: String?,
    val arguments: List<String>?,
    val parentPid: Long?,
    val parentCommand: String?,
    val parentArguments: List<String>?,
)

private fun procBytes(path: Path, limit: Int): ByteArray = Files.newInputStream(path).use {
    val bytes = it.readNBytes(limit + 1)
    require(bytes.isNotEmpty() && bytes.size <= limit) { "Missing or oversized owned-process metadata: $path" }
    bytes
}

private fun procText(path: Path, limit: Int): String = Charsets.UTF_8.newDecoder()
    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    .decode(ByteBuffer.wrap(procBytes(path, limit))).toString()

private fun parseFerocityProcIdentity(status: String, stat: String): FerocityProcIdentity {
    fun statusNumbers(key: String): List<Long> {
        val line = status.lineSequence().filter { it.startsWith("$key:") }.single()
        return line.substringAfter(':').trim().split(Regex("\\s+")).map(String::toLong)
    }
    val pid = statusNumbers("Pid").single()
    val parent = statusNumbers("PPid").single()
    val namespacePids = statusNumbers("NSpid")
    val userIds = statusNumbers("Uid")
    // comm is parenthesized and may itself contain spaces or ')'. The remaining numeric/stat
    // fields begin after its final ')'; starttime is field22, index19 after comm (field2).
    val close = stat.lastIndexOf(')')
    require(close > 0 && stat.substringBefore(' ').toLong() == pid) { "Inconsistent proc PID" }
    val fields = stat.substring(close + 1).trim().split(Regex("\\s+"))
    require(fields.size >= 20 && fields[0] !in setOf("Z", "X", "x") && fields[0].length == 1) {
        "Owned process has departed or has malformed state"
    }
    require(fields[1].toLong() == parent) { "Parent changed while reading process identity" }
    val start = fields[19].toLong()
    require(pid > 0 && parent >= 0 && start >= 0 && namespacePids.isNotEmpty() &&
        namespacePids.first() == pid && namespacePids.all { it > 0 } &&
        userIds.size == 4 && userIds.all { it >= 0 }) { "Invalid owned-process identity" }
    return FerocityProcIdentity(pid, parent, namespacePids, userIds, start)
}

private fun procIdentity(directory: Path): FerocityProcIdentity = parseFerocityProcIdentity(
    procText(directory.resolve("status"), 65536), procText(directory.resolve("stat"), 65536))

private fun readOwnedProc(directory: Path): FerocityOwnedProcessFacts {
    val before = procIdentity(directory)
    require(directory.fileName.toString() == before.pid.toString()) { "Proc directory identity changed" }
    val executable = directory.resolve("exe")
    val resolvedExecutable = executable.toRealPath()
    require(resolvedExecutable.isAbsolute && Files.isRegularFile(resolvedExecutable))
    // Hash the executable actually attached to this process, then also verify its canonical
    // named file. A replaced/deleted executable must not be credited to the pathname's new bytes.
    val actualHash = ferocityFileSha256(executable)
    require(ferocityFileSha256(resolvedExecutable) == actualHash) { "Live executable differs from its named file" }
    val rawArgv = procText(directory.resolve("cmdline"), 4 * 1024 * 1024)
    require(rawArgv.endsWith('\u0000')) { "Incomplete owned-process argv" }
    val argv = rawArgv.dropLast(1).split('\u0000')
    require(argv.isNotEmpty() && argv[0].isNotEmpty()) { "Empty owned-process argv" }
    require(procIdentity(directory) == before && executable.toRealPath() == resolvedExecutable) {
        "Owned process changed while reading executable and argv"
    }
    return FerocityOwnedProcessFacts(before, resolvedExecutable.toString(), actualHash, argv)
}

/** Reads only this JVM and the parent identified by its kernel PPid. No process enumeration. */
internal fun readFerocitySupervisorProcessFacts(): FerocitySupervisorProcessFacts {
    require(System.getProperty("os.name") == "Linux") { "Development supervision requires qualified Linux process metadata" }
    val selfLink = Files.readSymbolicLink(Path.of("/proc/self")).toString()
    require(selfLink.matches(Regex("[1-9][0-9]*"))) { "Unsupported /proc/self identity" }
    val selfDirectory = Path.of("/proc", selfLink)
    require(Path.of("/proc/self").toRealPath() == selfDirectory &&
        Files.getFileStore(selfDirectory).type() == "proc") { "Owned-process facts must come from procfs" }
    val child = readOwnedProc(selfDirectory)
    require(child.identity.parentPid > 0) { "The supervising parent is absent" }
    val parent = readOwnedProc(Path.of("/proc", child.identity.parentPid.toString()))
    require(procIdentity(selfDirectory) == child.identity &&
        procIdentity(Path.of("/proc", parent.identity.pid.toString())) == parent.identity) {
        "Parent ownership changed during process inspection"
    }
    return FerocitySupervisorProcessFacts(child, parent).also(::validateFerocityProcessOwnership)
}

internal fun validateFerocityProcessOwnership(facts: FerocitySupervisorProcessFacts) {
    val child = facts.child.identity
    val parent = facts.parent.identity
    require(child.pid != parent.pid && child.parentPid == parent.pid &&
        child.namespacePids.size == parent.namespacePids.size && child.userIds == parent.userIds &&
        parent.startTicks <= child.startTicks) { "The same-user live parent must own this JVM" }
}

internal fun captureFerocityJvmProcessFacts(): FerocityJvmProcessFacts {
    val current = ProcessHandle.current()
    val info = current.info()
    val parent = current.parent().orElse(null)
    val parentInfo = parent?.info()
    return FerocityJvmProcessFacts(current.pid(), info.command().orElse(null), info.arguments().orElse(null)?.toList(),
        parent?.pid(), parentInfo?.command()?.orElse(null), parentInfo?.arguments()?.orElse(null)?.toList())
}

/** No fallback from conflicting available Java facts to a more permissive representation. */
internal fun validateFerocityJvmProcessFacts(facts: FerocitySupervisorProcessFacts, jvm: FerocityJvmProcessFacts) {
    require(jvm.pid == facts.child.identity.namespacePids.last()) { "Current JVM and proc namespace identities differ" }
    fun agrees(command: String?, arguments: List<String>?, actual: FerocityOwnedProcessFacts) {
        command?.let { require(Path.of(it).toRealPath().toString() == actual.executable) { "Available JVM executable metadata conflicts with procfs" } }
        arguments?.let { require(it == actual.argv.drop(1)) { "Available JVM arguments conflict with procfs" } }
    }
    agrees(jvm.command, jvm.arguments, facts.child)
    jvm.parentPid?.let { require(it == facts.parent.identity.namespacePids.last()) { "Available JVM parent conflicts with kernel ownership" } }
    agrees(jvm.parentCommand, jvm.parentArguments, facts.parent)
}

internal fun requireSameFerocitySupervisorProcesses(before: FerocitySupervisorProcessFacts, after: FerocitySupervisorProcessFacts) {
    validateFerocityProcessOwnership(after)
    require(before == after) { "Executable, argv, UID, namespace, starttime or parent changed during supervision verification" }
}
