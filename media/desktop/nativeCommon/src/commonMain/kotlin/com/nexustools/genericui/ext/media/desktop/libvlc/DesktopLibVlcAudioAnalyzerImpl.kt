package com.nexustools.genericui.ext.media.desktop.libvlc

import com.nexustools.genericui.component.media.AudioEnergyBands
import com.nexustools.genericui.nativeimpl.media.AudioAnalyzerNativeImpl
import kotlin.math.sqrt

/**
 * Shared desktop [AudioAnalyzerNativeImpl] fed by `libvlc_audio_set_callbacks` PCM buffers.
 */
class DesktopLibVlcAudioAnalyzerImpl(
    initialFftSize: Int = 2048
) : AudioAnalyzerNativeImpl {

    override var fftSize: Int = initialFftSize.coerceIn(32, 32768)
    override val frequencyBinCount: Int
        get() = fftSize / 2

    private var pcmRingBuffer = FloatArray(fftSize)
    private var freqDbBuffer = FloatArray(frequencyBinCount) { -100f }

    fun onLibVlcPcmSamples(samples: FloatArray) {
        val copyLen = minOf(samples.size, pcmRingBuffer.size)
        for (i in 0 until copyLen) {
            pcmRingBuffer[i] = samples[i].coerceIn(-1f, 1f)
        }
    }

    override fun getByteTimeDomainData(destination: ByteArray) {
        val count = minOf(destination.size, pcmRingBuffer.size)
        for (i in 0 until count) {
            val byteVal = ((pcmRingBuffer[i] + 1f) * 127.5f).toInt().coerceIn(0, 255)
            destination[i] = byteVal.toByte()
        }
    }

    override fun getFloatTimeDomainData(destination: FloatArray) {
        val count = minOf(destination.size, pcmRingBuffer.size)
        pcmRingBuffer.copyInto(destination, 0, 0, count)
    }

    override fun getByteFrequencyData(destination: ByteArray) {
        val count = minOf(destination.size, freqDbBuffer.size)
        for (i in 0 until count) {
            val norm = ((freqDbBuffer[i] + 100f) / 100f).coerceIn(0f, 1f)
            destination[i] = (norm * 255f).toInt().toByte()
        }
    }

    override fun getFloatFrequencyData(destination: FloatArray) {
        val count = minOf(destination.size, freqDbBuffer.size)
        freqDbBuffer.copyInto(destination, 0, 0, count)
    }

    override fun computeEnergyBands(): AudioEnergyBands {
        var sumSq = 0f
        for (s in pcmRingBuffer) sumSq += s * s
        val rms = if (pcmRingBuffer.isNotEmpty()) sqrt(sumSq / pcmRingBuffer.size) else 0f
        return AudioEnergyBands(
            bass = rms,
            lowMid = rms * 0.9f,
            mid = rms * 0.85f,
            highMid = rms * 0.7f,
            treble = rms * 0.4f,
            overallRms = rms
        )
    }

    override fun dispose() {
        pcmRingBuffer = FloatArray(0)
        freqDbBuffer = FloatArray(0)
    }
}
