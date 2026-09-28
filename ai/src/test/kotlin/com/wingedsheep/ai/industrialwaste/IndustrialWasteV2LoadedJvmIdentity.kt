package com.wingedsheep.ai.industrialwaste

import java.security.MessageDigest

/**
 * Prospective preclaim JVM receiver: fingerprint bytes actually loaded by this class loader.
 * A repository SHA or launcher path alone cannot prove these executable classes.
 * This does not create a claim or declare an R1 permit.
 */
internal object IndustrialWasteV2LoadedJvmIdentity {
    data class Entry(val binaryName: String, val classSha256: String, val codeSource: String)

    fun capture(types: List<Class<*>>): List<Entry> {
        require(types.isNotEmpty() && types.map { it.name }.distinct().size == types.size)
        return types.sortedBy { it.name }.map { type ->
            val resource = type.name.replace('.', '/') + ".class"
            val bytes = requireNotNull(type.classLoader?.getResourceAsStream(resource)
                ?: ClassLoader.getSystemResourceAsStream(resource)) {
                "Loaded JVM bytecode unavailable: ${type.name}"
            }.use { it.readBytes() }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            Entry(type.name, digest, type.protectionDomain?.codeSource?.location?.toString()
                ?: "NO_CODE_SOURCE")
        }
    }

    fun verify(expectedSha256: Map<String, String>, types: List<Class<*>>): List<Entry> {
        val actual = capture(types)
        require(expectedSha256.keys == actual.map { it.binaryName }.toSet()) {
            "Loaded JVM class set differs from preclaim manifest"
        }
        check(actual.all { expectedSha256[it.binaryName] == it.classSha256 }) {
            "Loaded JVM bytecode differs from preclaim manifest"
        }
        return actual
    }
}
