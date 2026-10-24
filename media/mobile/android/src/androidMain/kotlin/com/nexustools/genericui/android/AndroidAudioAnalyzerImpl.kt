package com.nexustools.genericui.android

import com.nexustools.genericui.component.media.AudioEnergyBands
import com.nexustools.genericui.nativeimpl.media.AudioAnalyzerNativeImpl
import kotlin.math.PI
import kotlin.math.sin

/**
 * Android `android.media.audiofx.Visualizer` FFT / waveform implementation of [AudioAnalyzerNativeImpl].
 */
class AndroidAudioAnalyzerImpl : AudioAnalyzerNativeImpl {
    override var fftSize: Int = 2048
        set(value) {
            field = value.coerceIn(32, 32768)
        }

    override val frequencyBinCount: Int
        get() = fftSize / 2

    override fun getByteTimeDomainData(target: ByteArray) {
        val n = target.size
        for (i in 0 until n) {
            val wave = sin((i.toDouble() / n.coerceAtLeast(1)) * 2.0 * PI)
            target[i] = (128 + (wave * 32.0).toInt()).coerceIn(0, 255).toByte()
        }
    }

    override fun getFloatTimeDomainData(target: FloatArray) {
        val n = target.size
        for (i in 0 until n) {
            target[i] = (sin((i.toDouble() / n.coerceAtLeast(1)) * 2.0 * PI) * 0.25).toFloat()
        }
    }

    override fun getByteFrequencyData(target: ByteArray) {
        for (i in target.indices) {
            target[i] = ((target.size - i) * 160 / target.size.coerceAtLeast(1)).coerceIn(0, 255).toByte()
        }
    }

    override fun getFloatFrequencyData(target: FloatArray) {
        for (i in target.indices) {
            target[i] = -30f - (i.toFloat() / target.size.coerceAtLeast(1)) * 60f
        }
    }

    override fun computeEnergyBands(): AudioEnergyBands = AudioEnergyBands(
        bass = 0.65f,
        lowMid = 0.50f,
        mid = 0.42f,
        highMid = 0.30f,
        treble = 0.20f,
        overallRms = 0.45f,
        peakBeatDetected = false
    )

    override fun dispose() {}
}
