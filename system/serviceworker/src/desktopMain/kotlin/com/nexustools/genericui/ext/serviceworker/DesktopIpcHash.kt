package com.nexustools.genericui.ext.serviceworker

/**
 * Deterministic 64-bit FNV-1a hashing helper for desktop IPC endpoint naming.
 */
internal object DesktopIpcHash {
    fun fnv1a64Hex(input: String): String {
        var hash = 0xcbf29ce484222325uL
        val bytes = input.encodeToByteArray()
        for (b in bytes) {
            hash = hash xor (b.toUInt() and 0xFFu).toULong()
            hash *= 0x100000001b3uL
        }
        return hash.toString(16).padStart(16, '0')
    }
}
