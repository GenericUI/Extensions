package com.nexustools.genericui.ext.media

internal expect fun readLocalMediaFileBytes(pathOrUri: String, maxBytes: Int = 32 * 1024 * 1024): ByteArray?

internal fun normalizeLocalMediaPath(pathOrUri: String): String? {
    val trimmed = pathOrUri.trim()
    if (trimmed.isEmpty()) return null
    val lower = trimmed.lowercase()
    if (lower.startsWith("file://")) {
        var rest = trimmed.substring(7)
        if (rest.startsWith("localhost/", ignoreCase = true)) {
            rest = rest.substring(9)
        }
        if (rest.length >= 3 && rest[0] == '/' && rest[2] == ':' && rest[1].isLetter()) {
            rest = rest.substring(1)
        }
        return percentDecodePath(rest)
    }
    val schemeIdx = trimmed.indexOf("://")
    if (schemeIdx > 0 || lower.startsWith("data:") || lower.startsWith("blob:")) {
        return null
    }
    return trimmed
}

private fun percentDecodePath(input: String): String {
    if (!input.contains('%')) return input
    val out = ByteArray(input.length)
    var outIdx = 0
    var i = 0
    while (i < input.length) {
        val ch = input[i]
        if (ch == '%' && i + 2 < input.length) {
            val hi = input[i + 1].digitToIntOrNull(16)
            val lo = input[i + 2].digitToIntOrNull(16)
            if (hi != null && lo != null) {
                out[outIdx++] = ((hi shl 4) or lo).toByte()
                i += 3
                continue
            }
        }
        val encoded = ch.toString().encodeToByteArray()
        for (b in encoded) {
            out[outIdx++] = b
        }
        i++
    }
    return out.copyOf(outIdx).decodeToString()
}
