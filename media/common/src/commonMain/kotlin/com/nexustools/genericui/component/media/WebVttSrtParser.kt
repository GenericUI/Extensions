package com.nexustools.genericui.component.media

/**
 * Built-in cross-platform parser for WebVTT (`.vtt`) and SubRip (`.srt`) subtitle files.
 */
object WebVttSrtParser {
    fun parse(content: String): List<SubtitleCue> {
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isEmpty()) return emptyList()
        val blocks = normalized.split(Regex("\n{2,}"))
        val cues = mutableListOf<SubtitleCue>()
        var cueCounter = 0

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            if (lines[0].startsWith("WEBVTT", ignoreCase = true) || lines[0].startsWith("NOTE", ignoreCase = true)) {
                continue
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
