package com.nexustools.genericui.swing

import com.nexustools.genericui.component.media.AudioEnergyBands
import com.nexustools.genericui.nativeimpl.media.AudioAnalyzerNativeImpl
import com.sun.jna.Pointer
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.callback.AudioCallbackAdapter
import kotlin.math.sqrt

/**
 * Java Swing [AudioAnalyzerNativeImpl] backed by `libvlcj`'s [AudioCallbackAdapter].
 */
class SwingAudioAnalyzerImpl : AudioCallbackAdapter(), AudioAnalyzerNativeImpl {
    override var fftSize: Int = 2048
    override val frequencyBinCount: Int
        get() = (fftSize / 2).coerceAtLeast(16)

    private var pcmRingBuffer = FloatArray(fftSize)
    private var freqDbBuffer = FloatArray(frequencyBinCount) { -100f }

    override fun play(mediaPlayer: MediaPlayer, samples: Pointer, sampleCount: Int, pts: Long) {
        val count = minOf(sampleCount, pcmRingBuffer.size)
        if (count > 0) {
            val floats = samples.getFloatArray(0L, count)
            for (i in 0 until count) {
                pcmRingBuffer[i] = floats[i].coerceIn(-1f, 1f)
            }
        }
    }

    override fun getByteTimeDomainData(destination: ByteArray) {
        val count = minOf(destination.size, pcmRingBuffer.size)
        for (i in 0 until count) {
            destination[i] = (((pcmRingBuffer[i] + 1f) * 127.5f).toInt().coerceIn(0, 255)).toByte()
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
        return AudioEnergyBands(overallRms = rms)
    }

    override fun dispose() {
        pcmRingBuffer = FloatArray(0)
        freqDbBuffer = FloatArray(0)
    }
}
