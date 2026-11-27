package com.nexustools.genericui.ext.webview.desktop.engine

/**
 * Parses W3C WebVTT text tracks (`WEBVTT`) into timed [WebVttCue] entries.
 */
object WebVttParser {

    fun parse(vttSource: String): List<WebVttCue> {
        val cues = ArrayList<WebVttCue>()
        val normalized = vttSource.replace("\r\n", "\n").replace('\r', '\n')
        val blocks = normalized.split(Regex("\n{2,}"))
        for (rawBlock in blocks) {
            val lines = rawBlock.lines().map { it.trimEnd() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            if (lines[0].startsWith("WEBVTT", ignoreCase = true) ||
                lines[0].startsWith("NOTE", ignoreCase = true) ||
                lines[0].startsWith("STYLE", ignoreCase = true)
            ) {
                continue
            }
            var cueId = ""
            var timeIdx = 0
            if (!lines[0].contains("-->") && lines.size >= 2 && lines[1].contains("-->")) {
                cueId = lines[0].trim()
                timeIdx = 1
            }
            val timingLine = lines[timeIdx]
            if (!timingLine.contains("-->")) continue
            val arrowParts = timingLine.split("-->", limit = 2)
            val start = parseTimestamp(arrowParts[0].trim()) ?: continue
            val endToken = arrowParts[1].trim().split(Regex("\\s+")).firstOrNull() ?: continue
            val end = parseTimestamp(endToken) ?: continue
            val cueText = lines.drop(timeIdx + 1).joinToString("\n")
            cues.add(WebVttCue(id = cueId, startTime = start, endTime = end, text = cueText))
        }
        return cues
    }

    private fun parseTimestamp(raw: String): Double? {
        val parts = raw.split(':')
        if (parts.size !in 2..3) return null
        val (h, m, secPart) = if (parts.size == 3) {
            Triple(parts[0].toLongOrNull() ?: return null, parts[1].toLongOrNull() ?: return null, parts[2])
        } else {
            Triple(0L, parts[0].toLongOrNull() ?: return null, parts[1])
        }
        val secNormalized = secPart.replace(',', '.')
        val sec = secNormalized.toDoubleOrNull() ?: return null
        return h * 3600.0 + m * 60.0 + sec
    }
}
