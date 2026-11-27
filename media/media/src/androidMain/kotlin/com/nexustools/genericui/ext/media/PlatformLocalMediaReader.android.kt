package com.nexustools.genericui.ext.media

import java.io.File
import java.io.FileInputStream

internal actual fun readLocalMediaFileBytes(pathOrUri: String, maxBytes: Int): ByteArray? {
    val path = normalizeLocalMediaPath(pathOrUri) ?: return null
    val file = File(path)
    if (!file.exists() || !file.isFile) return null
    val length = file.length().coerceAtMost(maxBytes.toLong()).toInt()
    if (length <= 0) return ByteArray(0)
    val buffer = ByteArray(length)
    FileInputStream(file).use { input ->
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read <= 0) break
            offset += read
        }
        return if (offset == length) buffer else buffer.copyOf(offset)
    }
}
