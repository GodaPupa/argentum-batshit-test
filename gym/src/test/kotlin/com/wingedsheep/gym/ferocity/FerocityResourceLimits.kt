package com.wingedsheep.gym.ferocity

import java.nio.file.Files
import java.nio.file.Path

internal const val FEROCITY_CHILD_FILE_LIMIT_BYTES = 128L * 1024 * 1024
internal const val FEROCITY_MINIMUM_FREE_BYTES = 768L * 1024 * 1024

internal data class FerocityFileSizeLimit(val soft: Long?, val hard: Long?)

/** Null denotes the OS word 'unlimited'; unknown, duplicated or malformed facts fail. */
internal fun parseFerocityFileSizeLimit(text: String): FerocityFileSizeLimit {
    val rows = text.lineSequence().filter { it.startsWith("Max file size") }.toList()
    require(rows.size == 1) { "Missing or duplicated process file-size limit" }
    val match = Regex("^Max file size\\s+(\\S+)\\s+(\\S+)\\s+bytes\\s*$").matchEntire(rows.single())
        ?: throw IllegalArgumentException("Malformed process file-size limit")
    fun value(raw: String): Long? = if (raw == "unlimited") null else {
        require(raw.matches(Regex("[0-9]+"))) { "Invalid process file-size limit" }
        raw.toLong()
    }
    return FerocityFileSizeLimit(value(match.groupValues[1]), value(match.groupValues[2]))
}

internal fun requireFerocityFileSizeLimit(limit: FerocityFileSizeLimit) {
    require(limit.soft == FEROCITY_CHILD_FILE_LIMIT_BYTES && limit.hard == FEROCITY_CHILD_FILE_LIMIT_BYTES) {
        "The actual child must inherit the exact 128 MiB soft and hard file-size limit"
    }
}

/** Actual inherited kernel limit; no caller-provided fact can admit a production child. */
internal fun verifyFerocityInheritedFileSizeLimit() {
    require(System.getProperty("os.name") == "Linux") { "Resource admission requires qualified Linux process metadata" }
    val path = Path.of("/proc/self/limits")
    require(Files.getFileStore(path).type() == "proc") { "Process limits must come from procfs" }
    val bytes = Files.newInputStream(path).use { it.readNBytes(65537) }
    require(bytes.isNotEmpty() && bytes.size <= 65536) { "Missing or oversized process limits" }
    requireFerocityFileSizeLimit(parseFerocityFileSizeLimit(bytes.toString(Charsets.UTF_8)))
}
