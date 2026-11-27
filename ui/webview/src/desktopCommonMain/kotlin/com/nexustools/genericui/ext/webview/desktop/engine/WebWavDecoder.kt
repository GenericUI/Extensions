package com.nexustools.genericui.ext.webview.desktop.engine

/**
 * Decodes RIFF/WAVE PCM and IEEE-float audio payloads into normalized mono/stereo float samples
 * (`-1.0f .. 1.0f`) alongside stream sample rate, channel count, and duration metadata.
 */
object WebWavDecoder {

    data class DecodedAudio(
        val sampleRate: Int,
        val channels: Int,
        val samplesInterleaved: FloatArray
    ) {
        val frameCount: Int get() = if (channels <= 0) 0 else samplesInterleaved.size / channels
        val durationSeconds: Double get() = if (sampleRate <= 0) 0.0 else frameCount.toDouble() / sampleRate.toDouble()

        fun channelSamples(channelIndex: Int): FloatArray {
            if (channels <= 1 || channelIndex >= channels) return samplesInterleaved
            val frames = frameCount
            val out = FloatArray(frames)
            for (f in 0 until frames) {
                out[f] = samplesInterleaved[f * channels + channelIndex]
            }
            return out
        }
    }

    fun decodeOrNull(bytes: ByteArray): DecodedAudio? {
        if (bytes.size < 44) return null
        if (readFourCc(bytes, 0) != "RIFF" || readFourCc(bytes, 8) != "WAVE") return null

        var audioFormat = 1
        var channels = 1
        var sampleRate = 44100
        var bitsPerSample = 16
        var dataOffset = -1
        var dataSize = 0

        var pos = 12
        while (pos + 8 <= bytes.size) {
            val chunkId = readFourCc(bytes, pos)
            val chunkSize = readIntLE(bytes, pos + 4).coerceAtLeast(0)
            val payloadPos = pos + 8
            if (chunkId == "fmt " && chunkSize >= 16 && payloadPos + 16 <= bytes.size) {
                audioFormat = readUShortLE(bytes, payloadPos)
                channels = readUShortLE(bytes, payloadPos + 2).coerceAtLeast(1)
                sampleRate = readIntLE(bytes, payloadPos + 4).coerceAtLeast(1)
                bitsPerSample = readUShortLE(bytes, payloadPos + 14).coerceAtLeast(8)
            } else if (chunkId == "data") {
                dataOffset = payloadPos
                dataSize = minOf(chunkSize, bytes.size - payloadPos)
                break
            }
            val padded = if ((chunkSize and 1) == 1) chunkSize + 1 else chunkSize
            val next = payloadPos + padded
            if (next <= pos) break
            pos = next
        }

        if (dataOffset < 0 || dataSize <= 0) return null
        val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(1)
        val totalSamples = dataSize / bytesPerSample
        val out = FloatArray(totalSamples)

        when {
            audioFormat == 3 && bitsPerSample == 32 -> {
                for (i in 0 until totalSamples) {
                    val bits = readIntLE(bytes, dataOffset + i * 4)
                    out[i] = Float.fromBits(bits).coerceIn(-1f, 1f)
                }
            }
            bitsPerSample == 8 -> {
                for (i in 0 until totalSamples) {
                    val u = bytes[dataOffset + i].toInt() and 0xFF
                    out[i] = ((u - 128) / 128.0f).coerceIn(-1f, 1f)
                }
            }
            bitsPerSample == 16 -> {
                for (i in 0 until totalSamples) {
                    val lo = bytes[dataOffset + i * 2].toInt() and 0xFF
                    val hi = bytes[dataOffset + i * 2 + 1].toInt()
                    val s16 = (hi shl 8) or lo
                    out[i] = (s16 / 32768.0f).coerceIn(-1f, 1f)
                }
            }
            bitsPerSample == 24 -> {
                for (i in 0 until totalSamples) {
                    val off = dataOffset + i * 3
                    val b0 = bytes[off].toInt() and 0xFF
                    val b1 = bytes[off + 1].toInt() and 0xFF
                    val b2 = bytes[off + 2].toInt()
                    val s24 = (b2 shl 16) or (b1 shl 8) or b0
                    out[i] = (s24 / 8388608.0f).coerceIn(-1f, 1f)
                }
            }
            bitsPerSample == 32 -> {
                for (i in 0 until totalSamples) {
                    val s32 = readIntLE(bytes, dataOffset + i * 4)
                    out[i] = (s32 / 2147483648.0).toFloat().coerceIn(-1f, 1f)
                }
            }
            else -> return null
        }

        return DecodedAudio(sampleRate = sampleRate, channels = channels, samplesInterleaved = out)
    }

    private fun readFourCc(b: ByteArray, off: Int): String =
        charArrayOf(b[off].toInt().toChar(), b[off + 1].toInt().toChar(), b[off + 2].toInt().toChar(), b[off + 3].toInt().toChar()).concatToString()

    private fun readUShortLE(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun readIntLE(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)
}
