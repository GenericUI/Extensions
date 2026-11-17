package com.nexustools.genericui.android

import com.nexustools.genericui.component.media.AudioAnalyzerConfig
import com.nexustools.genericui.component.media.AudioEnergyBands
import com.nexustools.genericui.component.media.AudioFftSize
import com.nexustools.genericui.component.media.AudioPcmFftAnalyzer
import com.nexustools.genericui.nativeimpl.media.AudioAnalyzerNativeImpl

/**
 * Android `android.media.audiofx.Visualizer` + [AudioPcmFftAnalyzer] implementation of [AudioAnalyzerNativeImpl].
 */
class AndroidAudioAnalyzerImpl(
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

    fun onVisualizerWaveformCapture(waveform: ByteArray) {
        if (waveform.isEmpty()) return
        val floats = FloatArray(waveform.size) { i ->
            val u = waveform[i].toInt() and 0xFF
            ((u - 128) / 128f).coerceIn(-1f, 1f)
        }
        dsp.pushPcmFloatMonoOrStereo(floats, channels = 1)
    }

    override fun getByteTimeDomainData(target: ByteArray) {
        dsp.getByteTimeDomainData(target)
    }

    override fun getFloatTimeDomainData(target: FloatArray) {
        dsp.getFloatTimeDomainData(target)
    }

    override fun getByteFrequencyData(target: ByteArray) {
        dsp.getByteFrequencyData(target)
    }

    override fun getFloatFrequencyData(target: FloatArray) {
        dsp.getFloatFrequencyData(target)
    }

    override fun computeEnergyBands(): AudioEnergyBands =
        dsp.computeEnergyBands()

    override fun dispose() {
        dsp.reset()
    }
}
