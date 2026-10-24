package com.nexustools.genericui.ext.media

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.AudioEnergyBands
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.nativeimpl.media.AudioAnalyzerNativeImpl

/**
 * Extended real-time FFT spectrum, time-domain waveform, and [AudioEnergyBands] analyzer in
 * `:extensions:media` attachable to [VideoPlayerExt] or [AudioPlayerExt].
 */
class AudioAnalyzerExt(
    fftSize: Int = 2048
) : Disposable {

    val analyzer: AudioAnalyzer = AudioAnalyzer(fftSize)
    private var providerImpl: AudioAnalyzerNativeImpl? = MediaExtension.createAudioAnalyzerNative(fftSize)

    var fftSize: Int
        get() = providerImpl?.fftSize ?: analyzer.fftSize
        set(value) {
            analyzer.fftSize = value
            providerImpl?.fftSize = value
        }

    val frequencyBinCount: Int
        get() = providerImpl?.frequencyBinCount ?: analyzer.frequencyBinCount

    fun getByteTimeDomainData(destination: ByteArray) {
        val impl = providerImpl
        if (impl != null) {
            impl.getByteTimeDomainData(destination)
        } else {
            analyzer.getByteTimeDomainData(destination)
        }
    }

    fun getFloatTimeDomainData(destination: FloatArray) {
        val impl = providerImpl
        if (impl != null) {
            impl.getFloatTimeDomainData(destination)
        } else {
            analyzer.getFloatTimeDomainData(destination)
        }
    }

    fun getByteFrequencyData(destination: ByteArray) {
        val impl = providerImpl
        if (impl != null) {
            impl.getByteFrequencyData(destination)
        } else {
            analyzer.getByteFrequencyData(destination)
        }
    }

    fun getFloatFrequencyData(destination: FloatArray) {
        val impl = providerImpl
        if (impl != null) {
            impl.getFloatFrequencyData(destination)
        } else {
            analyzer.getFloatFrequencyData(destination)
        }
    }

    fun computeEnergyBands(): AudioEnergyBands =
        providerImpl?.computeEnergyBands() ?: analyzer.computeEnergyBands()

    override fun dispose() {
        providerImpl?.dispose()
        providerImpl = null
        analyzer.dispose()
    }
}
