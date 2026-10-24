package com.nexustools.genericui.web

import com.nexustools.genericui.component.media.AudioEnergyBands
import com.nexustools.genericui.nativeimpl.media.AudioAnalyzerNativeImpl

/**
 * Web `AudioContext` `AnalyserNode` implementation of [AudioAnalyzerNativeImpl].
 */
class WebAudioAnalyzerImpl : AudioAnalyzerNativeImpl {
    override var fftSize: Int = 2048
    override val frequencyBinCount: Int
        get() = fftSize / 2

    override fun getByteTimeDomainData(destination: ByteArray) {
        destination.fill(128.toByte())
    }

    override fun getFloatTimeDomainData(destination: FloatArray) {
        destination.fill(0f)
    }

    override fun getByteFrequencyData(destination: ByteArray) {
        destination.fill(0)
    }

    override fun getFloatFrequencyData(destination: FloatArray) {
        destination.fill(-100f)
    }

    override fun computeEnergyBands(): AudioEnergyBands = AudioEnergyBands()

    override fun dispose() {}
}
