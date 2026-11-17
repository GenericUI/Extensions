package com.nexustools.genericui.component.media

/**
 * Built-in cross-platform parser for WebVTT (`.vtt`) and SubRip (`.srt`) subtitle files.
 */
object WebVttSrtParser {
    private val INLINE_TAG_REGEX = Regex("<[^>]+>")

    fun parse(content: String): List<SubtitleCue> {
        val normalized = content.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isEmpty()) return emptyList()
        val blocks = normalized.split(Regex("\n{2,}"))
        val cues = mutableListOf<SubtitleCue>()
        var cueCounter = 0

        for (block in blocks) {
            val rawLines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (rawLines.isEmpty()) continue
            if (rawLines[0].startsWith("NOTE", ignoreCase = true) || rawLines[0].startsWith("STYLE", ignoreCase = true)) {
                continue
            }
            val lines = if (rawLines[0].startsWith("WEBVTT", ignoreCase = true)) {
                val idx = rawLines.indexOfFirst { it.contains("-->") }
                if (idx < 0) continue
                if (idx > 1) rawLines.subList(idx - 1, rawLines.size) else rawLines.subList(idx, rawLines.size)
            } else {
                rawLines
            }

            val arrowLineIdx = lines.indexOfFirst { it.contains("-->") }
            if (arrowLineIdx < 0) continue

            val cueId = if (arrowLineIdx > 0) lines[arrowLineIdx - 1] else "cue_${++cueCounter}"
            val timingParts = lines[arrowLineIdx].split("-->")
            if (timingParts.size < 2) continue

            val startSec = parseTimestampSeconds(timingParts[0].trim()) ?: continue
            val endTokens = timingParts[1].trim().split(Regex("\\s+"))
            val endSec = parseTimestampSeconds(endTokens[0].trim()) ?: continue

            val textLines = lines.subList(arrowLineIdx + 1, lines.size)
                .map { stripTagsAndDecodeEntities(it) }
                .filter { it.isNotEmpty() }
            if (textLines.isEmpty()) continue

            cues.add(
                SubtitleCue(
                    id = cueId,
                    startTimeSeconds = startSec,
                    endTimeSeconds = endSec,
                    text = textLines.joinToString("\n")
                )
            )
        }
        return cues
    }

    fun stripTagsAndDecodeEntities(raw: String): String =
        raw.replace(INLINE_TAG_REGEX, "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .trim()

    fun parseTimestampSeconds(raw: String): Double? {
        val cleaned = raw.replace(',', '.').trim()
        val parts = cleaned.split(':')
        return try {
            when (parts.size) {
                3 -> parts[0].toDouble() * 3600.0 + parts[1].toDouble() * 60.0 + parts[2].toDouble()
                2 -> parts[0].toDouble() * 60.0 + parts[1].toDouble()
                1 -> parts[0].toDouble()
                else -> null
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
