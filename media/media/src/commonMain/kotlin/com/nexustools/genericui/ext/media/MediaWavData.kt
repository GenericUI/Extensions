package com.nexustools.genericui.ext.media

/**
 * Decoded RIFF/WAVE PCM audio payload and stream properties.
 */
class MediaWavData(
    val sampleRateHz: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val durationSeconds: Double,
    val monoSamples: FloatArray
)
