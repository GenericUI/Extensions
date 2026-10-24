package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.nativeimpl.media.AudioAnalyzerNativeImpl

/**
 * Real-time FFT spectrum, time-domain waveform, and [AudioEnergyBands] analyzer attachable
 * to [AudioPlayer] or [VideoPlayer].
 */
class AudioAnalyzer(
    fftSize: Int = 2048
) : Disposable {
    private var nativeImpl: AudioAnalyzerNativeImpl? = MediaExtension.createAudioAnalyzerNative(fftSize)

    var fftSize: Int = fftSize
        set(value) {
            field = value
            ensureImpl()?.fftSize = value
        }

    val frequencyBinCount: Int
        get() = ensureImpl()?.frequencyBinCount ?: (fftSize / 2)

    fun getByteTimeDomainData(destination: ByteArray) {
        ensureImpl()?.getByteTimeDomainData(destination)
    }

    fun getFloatTimeDomainData(destination: FloatArray) {
        ensureImpl()?.getFloatTimeDomainData(destination)
    }

    fun getByteFrequencyData(destination: ByteArray) {
        ensureImpl()?.getByteFrequencyData(destination)
    }

    fun getFloatFrequencyData(destination: FloatArray) {
        ensureImpl()?.getFloatFrequencyData(destination)
    }

    fun computeEnergyBands(): AudioEnergyBands =
        ensureImpl()?.computeEnergyBands() ?: AudioEnergyBands()

    private fun ensureImpl(): AudioAnalyzerNativeImpl? {
        if (nativeImpl == null) {
            nativeImpl = MediaExtension.createAudioAnalyzerNative(fftSize)?.also {
                it.fftSize = fftSize
            }
        }
        return nativeImpl
    }

    override fun dispose() {
        nativeImpl?.dispose()
        nativeImpl = null
    }
}
