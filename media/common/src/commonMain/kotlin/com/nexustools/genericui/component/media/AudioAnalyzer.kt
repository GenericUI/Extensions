package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.nativeimpl.media.AudioAnalyzerNativeImpl

/**
 * Real-time FFT spectrum, time-domain waveform, and [AudioEnergyBands] analyzer attachable
 * to [AudioPlayer] or [VideoPlayer].
 */
class AudioAnalyzer(
    config: AudioAnalyzerConfig = AudioAnalyzerConfig()
) : Disposable {

    constructor(fftSize: Int) : this(
        AudioAnalyzerConfig(fftSize = AudioFftSize.fromSampleCount(fftSize))
    )

    var config: AudioAnalyzerConfig = config
        set(value) {
            field = value
            val newFftSize = value.fftSize.sampleCount
            if (fftSize != newFftSize) {
                fftSize = newFftSize
            } else {
                nativeImpl?.fftSize = newFftSize
            }
        }

    private var nativeImpl: AudioAnalyzerNativeImpl? = null

    var fftSize: Int = config.fftSize.sampleCount
        set(value) {
            field = value
            val resolvedEnum = AudioFftSize.fromSampleCount(value)
            if (config.fftSize != resolvedEnum) {
                config = config.copy(fftSize = resolvedEnum)
            }
            nativeImpl?.fftSize = value
        }

    var smoothingTimeConstant: Float
        get() = config.smoothingTimeConstant
        set(value) {
            config = config.copy(smoothingTimeConstant = value.coerceIn(0f, 0.99f))
        }

    var minDecibels: Float
        get() = config.minDecibels
        set(value) {
            config = config.copy(minDecibels = value)
        }

    var maxDecibels: Float
        get() = config.maxDecibels
        set(value) {
            config = config.copy(maxDecibels = value)
        }

    val frequencyBinCount: Int
        get() = nativeImpl?.frequencyBinCount ?: (fftSize / 2)

    fun getByteTimeDomainData(destination: ByteArray) {
        ensureImpl().getByteTimeDomainData(destination)
    }

    fun getFloatTimeDomainData(destination: FloatArray) {
        ensureImpl().getFloatTimeDomainData(destination)
    }

    fun getByteFrequencyData(destination: ByteArray) {
        ensureImpl().getByteFrequencyData(destination)
    }

    fun getFloatFrequencyData(destination: FloatArray) {
        ensureImpl().getFloatFrequencyData(destination)
    }

    fun computeEnergyBands(): AudioEnergyBands =
        ensureImpl().computeEnergyBands()

    fun pushPcmSamples(samples: FloatArray, channels: Int = 1) {
        ensureImpl().pushPcmSamples(samples, channels)
    }

    fun pushPcmS16Le(
        bytes: ByteArray,
        byteCount: Int = bytes.size,
        channels: Int = 2,
        volumeScale: Float = 1.0f
    ) {
        ensureImpl().pushPcmS16Le(bytes, byteCount, channels, volumeScale)
    }

    private fun ensureImpl(): AudioAnalyzerNativeImpl {
        val existing = nativeImpl
        if (existing != null) return existing
        val created = MediaExtension.createAudioAnalyzerNative(fftSize).also {
            it.fftSize = fftSize
        }
        nativeImpl = created
        return created
    }

    override fun dispose() {
        nativeImpl?.dispose()
        nativeImpl = null
    }
}
