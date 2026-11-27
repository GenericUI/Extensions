package com.nexustools.genericui.ext.ipc

/**
 * Deterministic 64-bit FNV-1a hashing and endpoint identifier sanitization helpers.
 */
internal object IpcHash {
    fun fnv1a64Hex(input: String): String {
        var hash = 0xcbf29ce484222325uL
        val bytes = input.encodeToByteArray()
        for (b in bytes) {
            hash = hash xor (b.toUInt() and 0xFFu).toULong()
            hash *= 0x100000001b3uL
        }
        return hash.toString(16).padStart(16, '0')
    }

    fun sanitizeChannelStem(channelName: String): String {
        val raw = channelName.trim().lowercase()
        if (raw.isEmpty()) return "ipc"
        val sb = StringBuilder(minOf(raw.length, 24))
        for (ch in raw) {
            val mapped = when {
                ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' -> ch
                else -> '_'
            }
            sb.append(mapped)
            if (sb.length >= 24) break
        }
        val cleaned = sb.toString().trim('.', '_')
        return if (cleaned.isEmpty()) "ipc" else cleaned
    }
}
