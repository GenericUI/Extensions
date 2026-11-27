package com.nexustools.genericui.ext.media

/**
 * Pure-Kotlin RIFF/WAVE PCM header parser and sample decoder supporting 8/16/24/32-bit integer PCM
 * and 32/64-bit IEEE float WAVE streams.
 */
object MediaWavDecoder {
    private const val FORMAT_PCM = 1
    private const val FORMAT_IEEE_FLOAT = 3
    private const val FORMAT_EXTENSIBLE = 0xFFFE

    fun isWavHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        return matchesFourCc(bytes, 0, "RIFF") && matchesFourCc(bytes, 8, "WAVE")
    }

    fun decode(bytes: ByteArray): MediaWavData? {
        if (!isWavHeader(bytes)) return null

        var audioFormat = 0
        var channels = 0
        var sampleRateHz = 0
        var blockAlign = 0
        var bitsPerSample = 0
        var hasFmt = false

        var dataOffset = -1
        var dataSize = 0

        var offset = 12
        while (offset + 8 <= bytes.size) {
            val chunkSizeRaw = readUInt32Le(bytes, offset + 4)
            val available = (bytes.size - (offset + 8)).toLong().coerceAtLeast(0L)
            val chunkSize = chunkSizeRaw.coerceAtMost(available).toInt()
            val payloadOffset = offset + 8

            if (matchesFourCc(bytes, offset, "fmt ") && chunkSize >= 16) {
                var fmt = readUInt16Le(bytes, payloadOffset)
                channels = readUInt16Le(bytes, payloadOffset + 2)
                sampleRateHz = readUInt32Le(bytes, payloadOffset + 4).toInt()
                blockAlign = readUInt16Le(bytes, payloadOffset + 12)
                bitsPerSample = readUInt16Le(bytes, payloadOffset + 14)
                if (fmt == FORMAT_EXTENSIBLE && chunkSize >= 40) {
                    fmt = readUInt16Le(bytes, payloadOffset + 24)
                }
                audioFormat = fmt
                hasFmt = true
            } else if (matchesFourCc(bytes, offset, "data")) {
                dataOffset = payloadOffset
                dataSize = chunkSize
            }

            if (chunkSizeRaw <= 0L && bytes.size - payloadOffset <= 0) break
            val paddedSize = chunkSizeRaw + (chunkSizeRaw and 1L)
            val nextOffset = payloadOffset.toLong() + paddedSize
            if (nextOffset <= offset || nextOffset > bytes.size.toLong()) break
            offset = nextOffset.toInt()
        }

        if (!hasFmt || dataOffset < 0 || channels <= 0 || sampleRateHz <= 0 || bitsPerSample <= 0) {
            return null
        }

        val bytesPerSample = (bitsPerSample + 7) / 8
        val frameStride = if (blockAlign >= channels * bytesPerSample) blockAlign else channels * bytesPerSample
        if (frameStride <= 0) return null

        val frameCount = dataSize / frameStride
        val monoSamples = FloatArray(frameCount)

        for (frame in 0 until frameCount) {
            val frameBase = dataOffset + frame * frameStride
            var sum = 0f
            for (ch in 0 until channels) {
                val sampleOff = frameBase + ch * bytesPerSample
                val sample = decodeSample(bytes, sampleOff, audioFormat, bitsPerSample)
                sum += sample
            }
            monoSamples[frame] = (sum / channels.toFloat()).coerceIn(-1f, 1f)
        }

        val durationSeconds = frameCount.toDouble() / sampleRateHz.toDouble()
        return MediaWavData(
            sampleRateHz = sampleRateHz,
            channels = channels,
            bitsPerSample = bitsPerSample,
            durationSeconds = durationSeconds,
            monoSamples = monoSamples
        )
    }

    private fun decodeSample(bytes: ByteArray, offset: Int, format: Int, bitsPerSample: Int): Float {
        return when (format) {
            FORMAT_PCM -> when (bitsPerSample) {
                8 -> if (offset < bytes.size) ((bytes[offset].toInt() and 0xFF) - 128) / 128f else 0f
                16 -> if (offset + 1 < bytes.size) {
                    readInt16Le(bytes, offset) / 32768f
                } else 0f
                24 -> if (offset + 2 < bytes.size) {
                    val b0 = bytes[offset].toInt() and 0xFF
                    val b1 = bytes[offset + 1].toInt() and 0xFF
                    val b2 = bytes[offset + 2].toInt()
                    val v = (b2 shl 16) or (b1 shl 8) or b0
                    v / 8388608f
                } else 0f
                32 -> if (offset + 3 < bytes.size) {
                    readInt32Le(bytes, offset) / 2147483648f
                } else 0f
                else -> 0f
            }
            FORMAT_IEEE_FLOAT -> when (bitsPerSample) {
                32 -> if (offset + 3 < bytes.size) {
                    Float.fromBits(readInt32Le(bytes, offset)).coerceIn(-1f, 1f)
                } else 0f
                64 -> if (offset + 7 < bytes.size) {
                    val lo = readUInt32Le(bytes, offset)
                    val hi = readUInt32Le(bytes, offset + 4)
                    Double.fromBits((hi shl 32) or lo).toFloat().coerceIn(-1f, 1f)
                } else 0f
                else -> 0f
            }
            else -> 0f
        }
    }

    private fun matchesFourCc(bytes: ByteArray, offset: Int, fourCc: String): Boolean {
        if (offset + 4 > bytes.size) return false
        return bytes[offset] == fourCc[0].code.toByte() &&
            bytes[offset + 1] == fourCc[1].code.toByte() &&
            bytes[offset + 2] == fourCc[2].code.toByte() &&
            bytes[offset + 3] == fourCc[3].code.toByte()
    }

    private fun readUInt16Le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readInt16Le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            (bytes[offset + 1].toInt() shl 8)

    private fun readInt32Le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            (bytes[offset + 3].toInt() shl 24)

    private fun readUInt32Le(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFFL) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFFL) shl 24)
}
