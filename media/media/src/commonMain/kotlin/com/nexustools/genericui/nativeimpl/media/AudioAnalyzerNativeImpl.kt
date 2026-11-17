package com.nexustools.genericui.nativeimpl.media

import com.nexustools.genericui.component.media.AudioEnergyBands
import com.nexustools.genericui.core.Disposable

interface AudioAnalyzerNativeImpl : Disposable {
    var fftSize: Int
    val frequencyBinCount: Int
    fun getByteTimeDomainData(destination: ByteArray)
    fun getFloatTimeDomainData(destination: FloatArray)
    fun getByteFrequencyData(destination: ByteArray)
    fun getFloatFrequencyData(destination: FloatArray)
    fun computeEnergyBands(): AudioEnergyBands
    fun pushPcmSamples(samples: FloatArray, channels: Int = 1) {}
    fun pushPcmS16Le(
        bytes: ByteArray,
        byteCount: Int = bytes.size,
        channels: Int = 2,
        volumeScale: Float = 1.0f
    ) {}
}
