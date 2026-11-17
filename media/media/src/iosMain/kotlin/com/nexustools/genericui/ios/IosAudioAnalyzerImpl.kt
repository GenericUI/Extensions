package com.nexustools.genericui.ios

import com.nexustools.genericui.component.media.AudioAnalyzerConfig
import com.nexustools.genericui.component.media.AudioEnergyBands
import com.nexustools.genericui.component.media.AudioFftSize
import com.nexustools.genericui.component.media.AudioPcmFftAnalyzer
import com.nexustools.genericui.nativeimpl.media.AudioAnalyzerNativeImpl

/**
 * iOS `MTAudioProcessingTap` + [AudioPcmFftAnalyzer] implementation of [AudioAnalyzerNativeImpl].
 */
class IosAudioAnalyzerImpl(
    initialFftSize: Int = 2048
) : AudioAnalyzerNativeImpl {

    private val dsp = AudioPcmFftAnalyzer(
        AudioAnalyzerConfig(fftSize = AudioFftSize.fromSampleCount(initialFftSize))
    )

    override var fftSize: Int
        get() = dsp.fftSize
        set(value) {
            dsp.fftSize = value.coerceIn(32, 32768)
        }

    override val frequencyBinCount: Int
        get() = dsp.frequencyBinCount

    override fun pushPcmSamples(samples: FloatArray, channels: Int) {
        dsp.pushPcmFloatMonoOrStereo(samples, channels = channels)
    }

    override fun pushPcmS16Le(
        bytes: ByteArray,
        byteCount: Int,
        channels: Int,
        volumeScale: Float
    ) {
        dsp.pushPcmS16Le(
            bytes = bytes,
            byteCount = byteCount,
            channels = channels,
            volumeScale = volumeScale
        )
    }

    override fun getByteTimeDomainData(destination: ByteArray) {
        dsp.getByteTimeDomainData(destination)
    }

    override fun getFloatTimeDomainData(destination: FloatArray) {
        dsp.getFloatTimeDomainData(destination)
    }

    override fun getByteFrequencyData(destination: ByteArray) {
        dsp.getByteFrequencyData(destination)
    }

    override fun getFloatFrequencyData(destination: FloatArray) {
        dsp.getFloatFrequencyData(destination)
    }

    override fun computeEnergyBands(): AudioEnergyBands =
        dsp.computeEnergyBands()

    override fun dispose() {
        dsp.reset()
    }
}
