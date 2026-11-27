@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread

internal actual fun readLocalMediaFileBytes(pathOrUri: String, maxBytes: Int): ByteArray? {
    val path = normalizeLocalMediaPath(pathOrUri) ?: return null
    val fp = fopen(path, "rb") ?: return null
    return try {
        val chunkSize = 65536
        val chunk = ByteArray(chunkSize)
        val chunks = mutableListOf<ByteArray>()
        var total = 0
        chunk.usePinned { pinned ->
            while (total < maxBytes) {
                val toRead = minOf(chunkSize, maxBytes - total)
                val count = fread(pinned.addressOf(0), 1.convert(), toRead.convert(), fp).toInt()
                if (count <= 0) break
                chunks.add(if (count == chunkSize) chunk.copyOf() else chunk.copyOf(count))
                total += count
            }
        }
        val result = ByteArray(total)
        var offset = 0
        for (part in chunks) {
            part.copyInto(result, destinationOffset = offset)
            offset += part.size
        }
        result
    } finally {
        fclose(fp)
    }
}
