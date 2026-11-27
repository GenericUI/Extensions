package com.nexustools.genericui.ext.media

import com.nexustools.genericui.component.media.MediaTrackInfo
import com.nexustools.genericui.component.media.SubtitleTrackInfo

/**
 * Pure-Kotlin ISO-BMFF / MP4 box parser extracting duration, video dimensions, frame counts,
 * audio track metadata, and subtitle track metadata from `moov`/`mvhd`/`tkhd`/`mdhd`/`hdlr`/`stbl`.
 */
object MediaMp4HeaderParser {
    private val KNOWN_TOP_BOXES = setOf("ftyp", "moov", "mdat", "free", "skip", "wide", "pnot", "uuid", "meta")

    fun isIsoBmffHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val size = readUInt32Be(bytes, 0)
        val type = readFourCc(bytes, 4)
        return size >= 8L && type in KNOWN_TOP_BOXES
    }

    fun parse(bytes: ByteArray): MediaContainerMetadata? {
        if (bytes.size < 12) return null

        var moovDurationSec = 0.0
        var maxTrackDurationSec = 0.0
        var naturalWidth = 0
        var naturalHeight = 0
        var videoFrameCount = 0
        var videoCodec = ""
        val audioTracks = mutableListOf<MediaTrackInfo>()
        val subtitleTracks = mutableListOf<SubtitleTrackInfo>()
        var foundMoov = false

        var offset = 0
        while (offset + 8 <= bytes.size) {
            val box = readBoxHeader(bytes, offset, bytes.size) ?: break
            if (box.type == "moov") {
                foundMoov = true
                parseMoov(
                    bytes = bytes,
                    start = box.payloadStart,
                    end = box.boxEnd,
                    onMovieDuration = { sec ->
                        if (sec > moovDurationSec) moovDurationSec = sec
                    },
                    onVideoTrack = { w, h, durSec, frames, codec ->
                        if (w > 0 && h > 0 && naturalWidth == 0) {
                            naturalWidth = w
                            naturalHeight = h
                        }
                        if (frames > videoFrameCount) videoFrameCount = frames
                        if (codec.isNotEmpty() && videoCodec.isEmpty()) videoCodec = codec
                        if (durSec > maxTrackDurationSec) maxTrackDurationSec = durSec
                    },
                    onAudioTrack = { label, lang, codec, channels, sampleRate, durSec ->
                        audioTracks.add(
                            MediaTrackInfo(
                                id = "audio-${audioTracks.size}",
                                label = label.ifBlank { "Audio ${audioTracks.size + 1}" },
                                language = lang.ifBlank { "und" },
                                codec = codec,
                                channels = channels.coerceAtLeast(1),
                                sampleRateHz = sampleRate.coerceAtLeast(8000),
                                isDefault = audioTracks.isEmpty(),
                                isSelected = audioTracks.isEmpty()
                            )
                        )
                        if (durSec > maxTrackDurationSec) maxTrackDurationSec = durSec
                    },
                    onSubtitleTrack = { label, lang, codec ->
                        subtitleTracks.add(
                            SubtitleTrackInfo(
                                id = "sub-${subtitleTracks.size}",
                                label = label.ifBlank { "Subtitles ${subtitleTracks.size + 1}" },
                                language = lang.ifBlank { "en" },
                                format = codec.ifBlank { "tx3g" },
                                isSelected = false
                            )
                        )
                    }
                )
            }
            if (box.boxEnd <= offset) break
            offset = box.boxEnd
        }

        if (!foundMoov) return null
        val duration = if (moovDurationSec > 0.0) moovDurationSec else maxTrackDurationSec
        return MediaContainerMetadata(
            durationSeconds = duration,
            naturalWidth = naturalWidth,
            naturalHeight = naturalHeight,
            frameCount = videoFrameCount,
            videoCodec = videoCodec,
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks
        )
    }

    private fun parseMoov(
        bytes: ByteArray,
        start: Int,
        end: Int,
        onMovieDuration: (Double) -> Unit,
        onVideoTrack: (Int, Int, Double, Int, String) -> Unit,
        onAudioTrack: (String, String, String, Int, Int, Double) -> Unit,
        onSubtitleTrack: (String, String, String) -> Unit
    ) {
        var offset = start
        while (offset + 8 <= end) {
            val box = readBoxHeader(bytes, offset, end) ?: break
            when (box.type) {
                "mvhd" -> parseMvhd(bytes, box.payloadStart, box.boxEnd)?.let(onMovieDuration)
                "trak" -> parseTrak(bytes, box.payloadStart, box.boxEnd, onVideoTrack, onAudioTrack, onSubtitleTrack)
            }
            if (box.boxEnd <= offset) break
            offset = box.boxEnd
        }
    }

    private fun parseMvhd(bytes: ByteArray, start: Int, end: Int): Double? {
        if (start + 4 > end) return null
        val version = bytes[start].toInt() and 0xFF
        val timescale: Long
        val duration: Long
        if (version == 0) {
            if (start + 20 > end) return null
            timescale = readUInt32Be(bytes, start + 12)
            duration = readUInt32Be(bytes, start + 16)
        } else {
            if (start + 32 > end) return null
            timescale = readUInt32Be(bytes, start + 20)
            duration = readInt64Be(bytes, start + 24)
        }
        if (timescale <= 0L || duration < 0L) return null
        return duration.toDouble() / timescale.toDouble()
    }

    private fun parseTrak(
        bytes: ByteArray,
        start: Int,
        end: Int,
        onVideoTrack: (Int, Int, Double, Int, String) -> Unit,
        onAudioTrack: (String, String, String, Int, Int, Double) -> Unit,
        onSubtitleTrack: (String, String, String) -> Unit
    ) {
        var tkhdWidth = 0
        var tkhdHeight = 0
        var stsdWidth = 0
        var stsdHeight = 0
        var handlerType = ""
        var handlerName = ""
        var language = "und"
        var mdhdDurationSec = 0.0
        var codec = ""
        var channels = 2
        var sampleRate = 48000
        var sampleCount = 0

        var offset = start
        while (offset + 8 <= end) {
            val box = readBoxHeader(bytes, offset, end) ?: break
            when (box.type) {
                "tkhd" -> {
                    val dims = parseTkhdDimensions(bytes, box.payloadStart, box.boxEnd)
                    if (dims != null) {
                        tkhdWidth = dims.first
                        tkhdHeight = dims.second
                    }
                }
                "mdia" -> {
                    var mOff = box.payloadStart
                    while (mOff + 8 <= box.boxEnd) {
                        val mBox = readBoxHeader(bytes, mOff, box.boxEnd) ?: break
                        when (mBox.type) {
                            "mdhd" -> {
                                val parsedMdhd = parseMdhd(bytes, mBox.payloadStart, mBox.boxEnd)
                                if (parsedMdhd != null) {
                                    mdhdDurationSec = parsedMdhd.first
                                    language = parsedMdhd.second
                                }
                            }
                            "hdlr" -> {
                                val parsedHdlr = parseHdlr(bytes, mBox.payloadStart, mBox.boxEnd)
                                if (parsedHdlr != null) {
                                    handlerType = parsedHdlr.first
                                    handlerName = parsedHdlr.second
                                }
                            }
                            "minf" -> {
                                parseMinf(
                                    bytes = bytes,
                                    start = mBox.payloadStart,
                                    end = mBox.boxEnd,
                                    onStsd = { entryCodec, w, h, ch, sr ->
                                        if (codec.isEmpty()) codec = entryCodec
                                        if (w > 0 && h > 0) {
                                            stsdWidth = w
                                            stsdHeight = h
                                        }
                                        if (ch > 0) channels = ch
                                        if (sr > 0) sampleRate = sr
                                    },
                                    onStsz = { count ->
                                        if (count > sampleCount) sampleCount = count
                                    }
                                )
                            }
                        }
                        if (mBox.boxEnd <= mOff) break
                        mOff = mBox.boxEnd
                    }
                }
            }
            if (box.boxEnd <= offset) break
            offset = box.boxEnd
        }

        when (handlerType) {
            "vide" -> {
                val w = if (tkhdWidth > 0) tkhdWidth else stsdWidth
                val h = if (tkhdHeight > 0) tkhdHeight else stsdHeight
                onVideoTrack(w, h, mdhdDurationSec, sampleCount, codec)
            }
            "soun" -> onAudioTrack(handlerName, language, codec, channels, sampleRate, mdhdDurationSec)
            "sbtl", "text" -> onSubtitleTrack(handlerName, language, codec)
        }
    }

    private fun parseTkhdDimensions(bytes: ByteArray, start: Int, end: Int): Pair<Int, Int>? {
        if (start + 4 > end) return null
        val version = bytes[start].toInt() and 0xFF
        val wOffset = if (version == 0) start + 76 else start + 88
        val hOffset = wOffset + 4
        if (hOffset + 4 > end) return null
        val width = (readUInt32Be(bytes, wOffset) ushr 16).toInt()
        val height = (readUInt32Be(bytes, hOffset) ushr 16).toInt()
        return width to height
    }

    private fun parseMdhd(bytes: ByteArray, start: Int, end: Int): Pair<Double, String>? {
        if (start + 4 > end) return null
        val version = bytes[start].toInt() and 0xFF
        val timescale: Long
        val duration: Long
        val langOffset: Int
        if (version == 0) {
            if (start + 22 > end) return null
            timescale = readUInt32Be(bytes, start + 12)
            duration = readUInt32Be(bytes, start + 16)
            langOffset = start + 20
        } else {
            if (start + 34 > end) return null
            timescale = readUInt32Be(bytes, start + 20)
            duration = readInt64Be(bytes, start + 24)
            langOffset = start + 32
        }
        val langRaw = readUInt16Be(bytes, langOffset)
        val c1 = ((langRaw ushr 10) and 0x1F) + 0x60
        val c2 = ((langRaw ushr 5) and 0x1F) + 0x60
        val c3 = (langRaw and 0x1F) + 0x60
        val lang = if (c1 in 97..122 && c2 in 97..122 && c3 in 97..122) {
            charArrayOf(c1.toChar(), c2.toChar(), c3.toChar()).concatToString()
        } else {
            "und"
        }
        val durSec = if (timescale > 0L && duration >= 0L) duration.toDouble() / timescale.toDouble() else 0.0
        return durSec to lang
    }

    private fun parseHdlr(bytes: ByteArray, start: Int, end: Int): Pair<String, String>? {
        if (start + 12 > end) return null
        val handlerType = readFourCc(bytes, start + 8)
        val nameStart = start + 24
        val name = if (nameStart < end) {
            var nameEnd = nameStart
            while (nameEnd < end && bytes[nameEnd] != 0.toByte()) {
                nameEnd++
            }
            bytes.decodeToString(nameStart, nameEnd).trim()
        } else {
            ""
        }
        return handlerType to name
    }

    private fun parseMinf(
        bytes: ByteArray,
        start: Int,
        end: Int,
        onStsd: (String, Int, Int, Int, Int) -> Unit,
        onStsz: (Int) -> Unit
    ) {
        var offset = start
        while (offset + 8 <= end) {
            val box = readBoxHeader(bytes, offset, end) ?: break
            if (box.type == "stbl") {
                var sOff = box.payloadStart
                while (sOff + 8 <= box.boxEnd) {
                    val sBox = readBoxHeader(bytes, sOff, box.boxEnd) ?: break
                    when (sBox.type) {
                        "stsd" -> parseStsd(bytes, sBox.payloadStart, sBox.boxEnd, onStsd)
                        "stsz" -> if (sBox.payloadStart + 12 <= sBox.boxEnd) {
                            onStsz(readUInt32Be(bytes, sBox.payloadStart + 8).toInt())
                        }
                    }
                    if (sBox.boxEnd <= sOff) break
                    sOff = sBox.boxEnd
                }
            }
            if (box.boxEnd <= offset) break
            offset = box.boxEnd
        }
    }

    private fun parseStsd(
        bytes: ByteArray,
        start: Int,
        end: Int,
        onStsd: (String, Int, Int, Int, Int) -> Unit
    ) {
        if (start + 16 > end) return
        val entryCount = readUInt32Be(bytes, start + 4).toInt()
        if (entryCount <= 0) return
        val entryBox = readBoxHeader(bytes, start + 8, end) ?: return
        val entryBase = start + 8
        var w = 0
        var h = 0
        var ch = 0
        var sr = 0
        if (entryBase + 36 <= entryBox.boxEnd) {
            w = readUInt16Be(bytes, entryBase + 32)
            h = readUInt16Be(bytes, entryBase + 34)
        }
        if (entryBase + 36 <= entryBox.boxEnd) {
            ch = readUInt16Be(bytes, entryBase + 24)
            sr = (readUInt32Be(bytes, entryBase + 32) ushr 16).toInt()
        }
        onStsd(entryBox.type, w, h, ch, sr)
    }

    private data class BoxSpan(
        val type: String,
        val payloadStart: Int,
        val boxEnd: Int
    )

    private fun readBoxHeader(bytes: ByteArray, offset: Int, parentEnd: Int): BoxSpan? {
        if (offset + 8 > parentEnd) return null
        val size32 = readUInt32Be(bytes, offset)
        val type = readFourCc(bytes, offset + 4)
        val headerSize: Int
        val totalSize: Long
        if (size32 == 1L) {
            if (offset + 16 > parentEnd) return null
            headerSize = 16
            totalSize = readInt64Be(bytes, offset + 8)
        } else if (size32 == 0L) {
            headerSize = 8
            totalSize = (parentEnd - offset).toLong()
        } else {
            headerSize = 8
            totalSize = size32
        }
        if (totalSize < headerSize.toLong()) return null
        val boxEnd = (offset.toLong() + totalSize).coerceAtMost(parentEnd.toLong()).toInt()
        val payloadStart = (offset + headerSize).coerceAtMost(boxEnd)
        return BoxSpan(type = type, payloadStart = payloadStart, boxEnd = boxEnd)
    }

    private fun readFourCc(bytes: ByteArray, offset: Int): String =
        charArrayOf(
            (bytes[offset].toInt() and 0xFF).toChar(),
            (bytes[offset + 1].toInt() and 0xFF).toChar(),
            (bytes[offset + 2].toInt() and 0xFF).toChar(),
            (bytes[offset + 3].toInt() and 0xFF).toChar()
        ).concatToString()

    private fun readUInt16Be(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)

    private fun readUInt32Be(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xFFL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 8) or
            (bytes[offset + 3].toLong() and 0xFFL)

    private fun readInt64Be(bytes: ByteArray, offset: Int): Long =
        (readUInt32Be(bytes, offset) shl 32) or readUInt32Be(bytes, offset + 4)
}
