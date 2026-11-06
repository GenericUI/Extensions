package com.nexustools.genericui.web

import com.nexustools.genericui.component.media.AudioAnalyzerConfig
import com.nexustools.genericui.component.media.AudioEnergyBands
import com.nexustools.genericui.component.media.AudioFftSize
import com.nexustools.genericui.component.media.AudioPcmFftAnalyzer
import com.nexustools.genericui.nativeimpl.media.AudioAnalyzerNativeImpl
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Web `AudioContext` `AnalyserNode` + [AudioPcmFftAnalyzer] implementation of [AudioAnalyzerNativeImpl].
 */
class WebAudioAnalyzerImpl(
    initialFftSize: Int = 2048
) : AudioAnalyzerNativeImpl {

    private val dsp = AudioPcmFftAnalyzer(
        AudioAnalyzerConfig(fftSize = AudioFftSize.fromSampleCount(initialFftSize))
    )

    private var hasPushedDirectSamples: Boolean = false

    override var fftSize: Int
        get() = dsp.fftSize
        set(value) {
            dsp.fftSize = value.coerceIn(32, 32768)
        }

    override val frequencyBinCount: Int
        get() = dsp.frequencyBinCount

    override fun pushPcmSamples(samples: FloatArray, channels: Int) {
        hasPushedDirectSamples = true
        dsp.pushPcmFloatMonoOrStereo(samples, channels = channels)
    }

    override fun pushPcmS16Le(
        bytes: ByteArray,
        byteCount: Int,
        channels: Int,
        volumeScale: Float
    ) {
        hasPushedDirectSamples = true
        dsp.pushPcmS16Le(
            bytes = bytes,
            byteCount = byteCount,
            channels = channels,
            volumeScale = volumeScale
        )
    }

    override fun getByteTimeDomainData(destination: ByteArray) {
        if (!hasPushedDirectSamples && WebAudioContextBridge.readByteTimeDomainData(fftSize, destination)) {
            return
        }
        dsp.getByteTimeDomainData(destination)
    }

    override fun getFloatTimeDomainData(destination: FloatArray) {
        if (!hasPushedDirectSamples && WebAudioContextBridge.readFloatTimeDomainData(fftSize, destination)) {
            return
        }
        dsp.getFloatTimeDomainData(destination)
    }

    override fun getByteFrequencyData(destination: ByteArray) {
        if (!hasPushedDirectSamples && WebAudioContextBridge.readByteFrequencyData(fftSize, destination)) {
            return
        }
        dsp.getByteFrequencyData(destination)
    }

    override fun getFloatFrequencyData(destination: FloatArray) {
        if (!hasPushedDirectSamples && WebAudioContextBridge.readFloatFrequencyData(fftSize, destination)) {
            return
        }
        dsp.getFloatFrequencyData(destination)
    }

    override fun computeEnergyBands(): AudioEnergyBands {
        if (!hasPushedDirectSamples && WebAudioContextBridge.hasActiveAnalyser()) {
            val binCount = frequencyBinCount.coerceAtLeast(32)
            val freqBytes = ByteArray(binCount)
            val timeBytes = ByteArray(fftSize.coerceAtLeast(64))
            val readFreq = WebAudioContextBridge.readByteFrequencyData(fftSize, freqBytes)
            val readTime = WebAudioContextBridge.readByteTimeDomainData(fftSize, timeBytes)
            if (readFreq && readTime) {
                var sumSqTime = 0f
                for (b in timeBytes) {
                    val u = b.toInt() and 0xFF
                    val s = (u - 128) / 128f
                    sumSqTime += s * s
                }
                val rms = sqrt(sumSqTime / timeBytes.size.toFloat()).coerceIn(0f, 1f)

                fun bandEnergy(startBin: Int, endBinInclusive: Int): Float {
                    val s = startBin.coerceIn(1, binCount - 1)
                    val e = endBinInclusive.coerceIn(s, binCount - 1)
                    var sumSq = 0f
                    var peak = 0f
                    var count = 0
                    for (bin in s..e) {
                        val norm = ((freqBytes[bin].toInt() and 0xFF) / 255f).coerceIn(0f, 1f)
                        sumSq += norm * norm
                        if (norm > peak) peak = norm
                        count++
                    }
                    if (count == 0 || peak <= 0f) return 0f
                    val bandRms = sqrt(sumSq / count.toFloat())
                    return (bandRms * 0.6f + peak * 0.4f).coerceIn(0f, 1f)
                }

                val bass = bandEnergy(1, 12)
                val lowMid = bandEnergy(13, 32)
                val mid = bandEnergy(33, 96)
                val highMid = bandEnergy(97, 220)
                val treble = bandEnergy(221, min(511, binCount - 1))
                return AudioEnergyBands(
                    bass = bass,
                    lowMid = lowMid,
                    mid = mid,
                    highMid = highMid,
                    treble = treble,
                    overallRms = rms,
                    peakBeatDetected = rms >= 0.30f || bass >= 0.60f
                )
            }
        }
        return dsp.computeEnergyBands()
    }

    override fun dispose() {
        hasPushedDirectSamples = false
        dsp.reset()
    }
}
