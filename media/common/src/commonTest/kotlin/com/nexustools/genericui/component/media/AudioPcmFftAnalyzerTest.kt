package com.nexustools.genericui.component.media

import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.nativeimpl.media.AudioAnalyzerNativeImpl
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioPcmFftAnalyzerTest {

    @Test
    fun test440HzSineWaveProducesExpectedFftPeakTimeDomainAndEnergyBands() {
        val fftSize = 2048
        val sampleRate = 44100.0
        val freqHz = 440.0
        val analyzer = AudioPcmFftAnalyzer(
            AudioAnalyzerConfig(
                fftSize = AudioFftSize.FFT_2048,
                smoothingTimeConstant = 0.8f,
                minDecibels = -100f,
                maxDecibels = -30f
            )
        )

        val sineSamples = FloatArray(fftSize) { i ->
            sin(2.0 * PI * freqHz * i.toDouble() / sampleRate).toFloat()
        }
        analyzer.pushPcmFloatMonoOrStereo(sineSamples, channels = 1)

        val byteTime = ByteArray(fftSize)
        analyzer.getByteTimeDomainData(byteTime)
        val minTimeByte = byteTime.minOf { it.toInt() and 0xFF }
        val maxTimeByte = byteTime.maxOf { it.toInt() and 0xFF }
        assertTrue(
            maxTimeByte - minTimeByte > 200,
            "Expected non-flat getByteTimeDomainData for 440 Hz sine wave, got range [$minTimeByte..$maxTimeByte]"
        )

        val byteFreq = ByteArray(analyzer.frequencyBinCount)
        val floatFreq = FloatArray(analyzer.frequencyBinCount)
        analyzer.getByteFrequencyData(byteFreq)
        analyzer.getFloatFrequencyData(floatFreq)

        assertTrue(
            byteFreq.any { (it.toInt() and 0xFF) > 0 },
            "Expected non-zero getByteFrequencyData for 440 Hz sine wave"
        )

        val peakBinFloat = floatFreq.indices.maxByOrNull { floatFreq[it] } ?: -1
        val peakBinByte = byteFreq.indices.maxByOrNull { byteFreq[it].toInt() and 0xFF } ?: -1
        // Expected bin: 440 * 2048 / 44100 = 20.435 -> bin 20
        assertTrue(
            abs(peakBinFloat - 20) <= 1,
            "Expected float FFT peak near bin 20 for 440 Hz @ 44.1 kHz (N=2048), got bin $peakBinFloat"
        )
        assertTrue(
            abs(peakBinByte - 20) <= 1,
            "Expected byte FFT peak near bin 20 for 440 Hz @ 44.1 kHz (N=2048), got bin $peakBinByte"
        )

        val bands = analyzer.computeEnergyBands()
        assertTrue(bands.overallRms > 0.5f, "Expected positive overallRms, got ${bands.overallRms}")
        assertTrue(bands.lowMid > 0.2f, "Expected positive lowMid energy around 440 Hz (bin 20), got ${bands.lowMid}")
    }

    @Test
    fun testSilenceProduces128TimeDomainZeroFrequencyAndZeroEnergyBands() {
        val fftSize = 2048
        val analyzer = AudioPcmFftAnalyzer(
            AudioAnalyzerConfig(fftSize = AudioFftSize.FFT_2048)
        )

        // Push active signal first, then push silence to verify full reset/silence behavior
        val activeSamples = FloatArray(fftSize) { i ->
            sin(2.0 * PI * 440.0 * i.toDouble() / 44100.0).toFloat()
        }
        analyzer.pushPcmFloatMonoOrStereo(activeSamples, channels = 1)

        val silentSamples = FloatArray(fftSize) { 0.0f }
        analyzer.pushPcmFloatMonoOrStereo(silentSamples, channels = 1)

        val byteTime = ByteArray(fftSize)
        analyzer.getByteTimeDomainData(byteTime)
        for (i in byteTime.indices) {
            assertEquals(
                128,
                byteTime[i].toInt() and 0xFF,
                "Expected 128 in getByteTimeDomainData at index $i for silence"
            )
        }

        val byteFreq = ByteArray(analyzer.frequencyBinCount)
        analyzer.getByteFrequencyData(byteFreq)
        for (i in byteFreq.indices) {
            assertEquals(
                0,
                byteFreq[i].toInt() and 0xFF,
                "Expected 0 in getByteFrequencyData at bin $i for silence"
            )
        }

        val bands = analyzer.computeEnergyBands()
        assertEquals(0.0f, bands.bass)
        assertEquals(0.0f, bands.lowMid)
        assertEquals(0.0f, bands.mid)
        assertEquals(0.0f, bands.highMid)
        assertEquals(0.0f, bands.treble)
        assertEquals(0.0f, bands.overallRms)
        assertEquals(false, bands.peakBeatDetected)
    }

    @Test
    fun testS16LeStereoPushAndTypedAudioAnalyzerConfig() {
        MediaExtension.registerProviders(
            audioAnalyzer = { requestedFftSize ->
                val dsp = AudioPcmFftAnalyzer(
                    AudioAnalyzerConfig(fftSize = AudioFftSize.fromSampleCount(requestedFftSize))
                )
                object : AudioAnalyzerNativeImpl {
                    override var fftSize: Int
                        get() = dsp.fftSize
                        set(value) { dsp.fftSize = value }
                    override val frequencyBinCount: Int
                        get() = dsp.frequencyBinCount
                    override fun getByteTimeDomainData(destination: ByteArray) =
                        dsp.getByteTimeDomainData(destination)
                    override fun getFloatTimeDomainData(destination: FloatArray) =
                        dsp.getFloatTimeDomainData(destination)
                    override fun getByteFrequencyData(destination: ByteArray) =
                        dsp.getByteFrequencyData(destination)
                    override fun getFloatFrequencyData(destination: FloatArray) =
                        dsp.getFloatFrequencyData(destination)
                    override fun computeEnergyBands(): AudioEnergyBands =
                        dsp.computeEnergyBands()
                    override fun pushPcmSamples(samples: FloatArray, channels: Int) =
                        dsp.pushPcmFloatMonoOrStereo(samples, channels)
                    override fun pushPcmS16Le(
                        bytes: ByteArray,
                        byteCount: Int,
                        channels: Int,
                        volumeScale: Float
                    ) = dsp.pushPcmS16Le(bytes, byteCount, channels, volumeScale)
                    override fun dispose() = dsp.reset()
                }
            }
        )

        val analyzer = AudioAnalyzer(
            config = AudioAnalyzerConfig(
                fftSize = AudioFftSize.FFT_2048,
                smoothingTimeConstant = 0.75f
            )
        )
        assertEquals(2048, analyzer.fftSize)
        assertEquals(1024, analyzer.frequencyBinCount)

        // Generate stereo S16LE with 100 Hz (bass), 1200 Hz (mid), and 6000 Hz (treble)
        val frames = 2048
        val s16Bytes = ByteArray(frames * 4)
        for (i in 0 until frames) {
            val t = i.toDouble() / 44100.0
            val wave = (
                0.45 * sin(2.0 * PI * 100.0 * t) +
                    0.35 * sin(2.0 * PI * 1200.0 * t) +
                    0.20 * sin(2.0 * PI * 6000.0 * t)
                ).coerceIn(-1.0, 1.0)
            val s16 = (wave * 32767.0).toInt().toShort().toInt()
            val base = i * 4
            s16Bytes[base] = (s16 and 0xFF).toByte()
            s16Bytes[base + 1] = ((s16 shr 8) and 0xFF).toByte()
            s16Bytes[base + 2] = (s16 and 0xFF).toByte()
            s16Bytes[base + 3] = ((s16 shr 8) and 0xFF).toByte()
        }

        analyzer.pushPcmS16Le(s16Bytes, s16Bytes.size, channels = 2, volumeScale = 1.0f)
        val bands = analyzer.computeEnergyBands()
        assertTrue(bands.bass > 0.2f, "Expected positive bass energy, got ${bands.bass}")
        assertTrue(bands.mid > 0.2f, "Expected positive mid energy, got ${bands.mid}")
        assertTrue(bands.treble > 0.15f, "Expected positive treble energy, got ${bands.treble}")
        assertTrue(bands.overallRms > 0.2f, "Expected positive overallRms, got ${bands.overallRms}")
        analyzer.dispose()
    }
}
