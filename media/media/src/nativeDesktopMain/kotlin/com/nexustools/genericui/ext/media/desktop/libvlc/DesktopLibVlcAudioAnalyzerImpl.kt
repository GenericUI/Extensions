package com.nexustools.genericui.ext.media.desktop.libvlc

import com.nexustools.genericui.component.media.AudioAnalyzerConfig
import com.nexustools.genericui.component.media.AudioEnergyBands
import com.nexustools.genericui.component.media.AudioFftSize
import com.nexustools.genericui.component.media.AudioPcmFftAnalyzer
import com.nexustools.genericui.desktop.core.DesktopInteractiveSurfaceSpec
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.nativeimpl.media.AudioAnalyzerNativeImpl

/**
 * Shared desktop [AudioAnalyzerNativeImpl] fed by `libvlc_audio_set_callbacks` PCM buffers
 * and backed by [AudioPcmFftAnalyzer] (Hann window + radix-2 Cooley-Tukey FFT).
 */
class DesktopLibVlcAudioAnalyzerImpl(
    initialFftSize: Int = 2048,
    val nativeWidget: DesktopNativeWidget = DesktopNativeWidget("Desktop", "libvlc-audio-analyzer")
) : AudioAnalyzerNativeImpl {

    private val dsp = AudioPcmFftAnalyzer(
        AudioAnalyzerConfig(fftSize = AudioFftSize.fromSampleCount(initialFftSize.coerceIn(32, 32768)))
    )

    override var fftSize: Int
        get() = dsp.fftSize
        set(value) {
            dsp.fftSize = value.coerceIn(32, 32768)
        }

    override val frequencyBinCount: Int
        get() = dsp.frequencyBinCount

    private var surfaceWidthPx: Int = 256
    private var surfaceHeightPx: Int = 64
    private var swapCounter: Long = 0L

    init {
        DesktopInteractiveSurfaceSpec(
            surfaceWidth = { surfaceWidthPx.coerceAtLeast(1) },
            surfaceHeight = { surfaceHeightPx.coerceAtLeast(1) },
            swapCount = { swapCounter },
            captureBgra = { ByteArray(surfaceWidthPx.coerceAtLeast(1) * surfaceHeightPx.coerceAtLeast(1) * 4) },
            captureRgba = { ByteArray(surfaceWidthPx.coerceAtLeast(1) * surfaceHeightPx.coerceAtLeast(1) * 4) },
            resizeSurface = { w, h ->
                if (w > 0 && h > 0) {
                    surfaceWidthPx = w
                    surfaceHeightPx = h
                }
            },
            preferHardwareStretchBlit = false,
            suppressDefaultBorder = true
        ).attachTo(nativeWidget)
    }

    fun onLibVlcPcmSamples(samples: FloatArray) {
        pushPcmSamples(samples, channels = 1)
    }

    override fun pushPcmSamples(samples: FloatArray, channels: Int) {
        dsp.pushPcmFloatMonoOrStereo(samples, channels = channels)
        swapCounter += 1L
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
        swapCounter += 1L
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
