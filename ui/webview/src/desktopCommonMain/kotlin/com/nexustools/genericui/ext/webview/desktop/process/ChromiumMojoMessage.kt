package com.nexustools.genericui.ext.webview.desktop.process

/**
 * Framed Mojo IPC message exchanged between Browser, Renderer, GPU, and Network processes.
 *
 * Supports deterministic single-line wire serialization ([encodeWireLine] / [decodeWireLine])
 * using percent/hex-escaped UTF-8 key-value pairs so arbitrary HTML, JS, CSS, and JSON payloads
 * round-trip 100% losslessly over `stdin`/`stdout` pipes or in-memory channels.
 */
data class ChromiumMojoMessage(
    val messageId: Long,
    val channelId: String,
    val type: ChromiumMojoMessageType,
    val sourceProcess: ChromiumProcessType,
    val targetProcess: ChromiumProcessType,
    val payload: Map<String, String> = emptyMap()
) {
    fun encodeWireLine(): String {
        val sb = StringBuilder("MOJO/1.0|")
        sb.append(messageId).append('|')
        sb.append(escapeComponent(channelId)).append('|')
        sb.append(type.name).append('|')
        sb.append(sourceProcess.name).append('|')
        sb.append(targetProcess.name).append('|')
        var first = true
        for ((key, value) in payload) {
            if (!first) sb.append('&')
            first = false
            sb.append(escapeComponent(key)).append('=').append(escapeComponent(value))
        }
        return sb.toString()
    }

    companion object {
        private const val HEX_DIGITS = "0123456789ABCDEF"

        fun decodeWireLine(line: String): ChromiumMojoMessage? {
            val trimmed = line.trim('\r', '\n', ' ')
            if (!trimmed.startsWith("MOJO/1.0|")) return null
            val parts = trimmed.split('|')
            if (parts.size != 7 || parts[0] != "MOJO/1.0") return null

            val msgId = parts[1].toLongOrNull() ?: return null
            val channelId = unescapeComponent(parts[2])
            val msgType = ChromiumMojoMessageType.entries.firstOrNull { it.name == parts[3] } ?: return null
            val srcProc = ChromiumProcessType.entries.firstOrNull { it.name == parts[4] } ?: return null
            val dstProc = ChromiumProcessType.entries.firstOrNull { it.name == parts[5] } ?: return null

            val rawPayload = parts[6]
            val payloadMap = LinkedHashMap<String, String>()
            if (rawPayload.isNotEmpty()) {
                for (entry in rawPayload.split('&')) {
                    if (entry.isEmpty()) continue
                    val eqIdx = entry.indexOf('=')
                    if (eqIdx >= 0) {
                        val k = unescapeComponent(entry.substring(0, eqIdx))
                        val v = unescapeComponent(entry.substring(eqIdx + 1))
                        payloadMap[k] = v
                    } else {
                        payloadMap[unescapeComponent(entry)] = ""
                    }
                }
            }

            return ChromiumMojoMessage(
                messageId = msgId,
                channelId = channelId,
                type = msgType,
                sourceProcess = srcProc,
                targetProcess = dstProc,
                payload = payloadMap
            )
        }

        internal fun escapeComponent(raw: String): String {
            if (raw.isEmpty()) return ""
            val bytes = raw.encodeToByteArray()
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                val u = b.toInt() and 0xFF
                val isUnreserved = (u in 'A'.code..'Z'.code) ||
                    (u in 'a'.code..'z'.code) ||
                    (u in '0'.code..'9'.code) ||
                    u == '-'.code ||
                    u == '_'.code ||
                    u == '.'.code ||
                    u == '~'.code
                if (isUnreserved) {
                    sb.append(u.toChar())
                } else {
                    sb.append('%')
                    sb.append(HEX_DIGITS[(u ushr 4) and 0x0F])
                    sb.append(HEX_DIGITS[u and 0x0F])
                }
            }
            return sb.toString()
        }

        internal fun unescapeComponent(escaped: String): String {
            if (escaped.isEmpty()) return ""
            val out = ByteArray(escaped.length)
            var outLen = 0
            var i = 0
            while (i < escaped.length) {
                val ch = escaped[i]
                if (ch == '%' && i + 2 < escaped.length) {
                    val hi = hexVal(escaped[i + 1])
                    val lo = hexVal(escaped[i + 2])
                    if (hi >= 0 && lo >= 0) {
                        out[outLen++] = ((hi shl 4) or lo).toByte()
                        i += 3
                        continue
                    }
                }
                out[outLen++] = ch.code.toByte()
                i++
            }
            return out.decodeToString(0, outLen)
        }

        private fun hexVal(c: Char): Int = when (c) {
            in '0'..'9' -> c.code - '0'.code
            in 'A'..'F' -> c.code - 'A'.code + 10
            in 'a'..'f' -> c.code - 'a'.code + 10
            else -> -1
        }
    }
}
