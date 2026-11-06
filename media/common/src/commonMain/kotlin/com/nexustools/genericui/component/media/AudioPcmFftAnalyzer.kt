package com.nexustools.genericui.component.media

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.TimeSource

/**
 * Shared common-code PCM time-domain and radix-2 Cooley-Tukey FFT spectrum processor
 * used by backend `AudioAnalyzerNativeImpl` implementations across Desktop, Web, and Mobile.
 */
class AudioPcmFftAnalyzer(
    initialConfig: AudioAnalyzerConfig = AudioAnalyzerConfig()
) {
    var config: AudioAnalyzerConfig = initialConfig
        set(value) {
            val prevN = field.fftSize.sampleCount
            field = value
            val newN = value.fftSize.sampleCount
            if (newN != prevN) {
                allocateBuffers(newN)
            }
        }

    var fftSize: Int
        get() = config.fftSize.sampleCount
        set(value) {
            val resolved = AudioFftSize.fromSampleCount(value)
            if (resolved != config.fftSize) {
                config = config.copy(fftSize = resolved)
            }
        }

    val frequencyBinCount: Int
        get() = fftSize / 2

    private val monotonicClock = TimeSource.Monotonic
    private var startMark = monotonicClock.markNow()
    private var lastSampleMark: TimeSource.Monotonic.ValueTimeMark? = null
    private var lastDecayMark: TimeSource.Monotonic.ValueTimeMark? = null

    var lastSampleTimestampMs: Long = 0L
        private set

    private var ringBuffer = FloatArray(fftSize)
    private var orderedTimeDomain = FloatArray(fftSize)
    private var ringWritePos: Int = 0
    private var hannWindow = buildHannWindow(fftSize)
    private var fftReal = FloatArray(fftSize)
    private var fftImag = FloatArray(fftSize)
    private var smoothedMag = FloatArray(frequencyBinCount)
    private var freqDbBuffer = FloatArray(frequencyBinCount) { config.minDecibels }
    private var hasSmoothedFrame: Boolean = false
    private var currentRms: Float = 0f

    private fun buildHannWindow(n: Int): FloatArray {
        val denom = (n - 1).coerceAtLeast(1).toDouble()
        return FloatArray(n) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i.toDouble() / denom))).toFloat()
        }
    }

    private fun allocateBuffers(n: Int) {
        val bins = (n / 2).coerceAtLeast(1)
        ringBuffer = FloatArray(n)
        orderedTimeDomain = FloatArray(n)
        ringWritePos = 0
        hannWindow = buildHannWindow(n)
        fftReal = FloatArray(n)
        fftImag = FloatArray(n)
        smoothedMag = FloatArray(bins)
        freqDbBuffer = FloatArray(bins) { config.minDecibels }
        hasSmoothedFrame = false
        currentRms = 0f
    }

    fun pushPcmFloatMonoOrStereo(samples: FloatArray, channels: Int = 1) {
        if (samples.isEmpty()) return
        val ch = channels.coerceAtLeast(1)
        val totalFrames = samples.size / ch
        if (totalFrames <= 0) return

        val n = fftSize
        val mask = n - 1
        var writePos = ringWritePos and mask
        val ring = ringBuffer
        if (ring.size != n) {
            allocateBuffers(n)
        }

        for (frame in 0 until totalFrames) {
            val base = frame * ch
            val left = samples[base]
            val mono = if (ch >= 2 && base + 1 < samples.size) {
                0.5f * (left + samples[base + 1])
            } else {
                left
            }
            val clean = if (mono.isNaN() || mono.isInfinite()) 0f else mono.coerceIn(-1f, 1f)
            ringBuffer[writePos] = clean
            writePos = (writePos + 1) and mask
        }
        ringWritePos = writePos
        recordSampleActivityAndComputeFft()
    }

    fun pushPcmS16Le(
        bytes: ByteArray,
        byteCount: Int = bytes.size,
        channels: Int = 2,
        volumeScale: Float = 1.0f
    ) {
        val validBytes = byteCount.coerceIn(0, bytes.size)
        if (validBytes <= 0) return
        val ch = channels.coerceAtLeast(1)
        val bytesPerFrame = 2 * ch
        val totalFrames = validBytes / bytesPerFrame
        if (totalFrames <= 0) return

        val n = fftSize
        val mask = n - 1
        var writePos = ringWritePos and mask
        if (ringBuffer.size != n) {
            allocateBuffers(n)
        }

        val scale = (volumeScale.coerceIn(0f, 2f)) / 32768f
        val step = if (totalFrames > n * 2) (totalFrames / n).coerceAtLeast(1) else 1
        var frame = 0
        while (frame < totalFrames) {
            val base = frame * bytesPerFrame
            val loL = bytes[base].toInt() and 0xFF
            val hiL = bytes[base + 1].toInt()
            val left = ((hiL shl 8) or loL).toShort().toFloat() * scale
            val mono = if (ch >= 2 && base + 3 < validBytes) {
                val loR = bytes[base + 2].toInt() and 0xFF
                val hiR = bytes[base + 3].toInt()
                val right = ((hiR shl 8) or loR).toShort().toFloat() * scale
                0.5f * (left + right)
            } else {
                left
            }
            ringBuffer[writePos] = mono.coerceIn(-1f, 1f)
            writePos = (writePos + 1) and mask
            frame += step
        }
        ringWritePos = writePos
        recordSampleActivityAndComputeFft()
    }

    private fun recordSampleActivityAndComputeFft() {
        val now = monotonicClock.markNow()
        lastSampleMark = now
        lastDecayMark = now
        lastSampleTimestampMs = (now - startMark).inWholeMilliseconds.coerceAtLeast(1L)
        recomputeFftFrame()
    }

    private fun recomputeFftFrame() {
        val n = fftSize
        val bins = frequencyBinCount
        if (ringBuffer.size != n || fftReal.size != n || freqDbBuffer.size != bins) {
            allocateBuffers(n)
        }

        val mask = n - 1
        val readStart = ringWritePos and mask
        var sumSq = 0f
        for (i in 0 until n) {
            val sample = ringBuffer[(readStart + i) and mask]
            orderedTimeDomain[i] = sample
            sumSq += sample * sample
            fftReal[i] = sample * hannWindow[i]
            fftImag[i] = 0f
        }

        val minDb = config.minDecibels
        if (sumSq < 1e-10f) {
            currentRms = 0f
            hasSmoothedFrame = false
            orderedTimeDomain.fill(0f)
            smoothedMag.fill(0f)
            freqDbBuffer.fill(minDb)
            return
        }

        currentRms = sqrt(sumSq / n.toFloat()).coerceIn(0f, 1f)

        // In-place Radix-2 Cooley-Tukey FFT
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                val tr = fftReal[i]
                fftReal[i] = fftReal[j]
                fftReal[j] = tr
                val ti = fftImag[i]
                fftImag[i] = fftImag[j]
                fftImag[j] = ti
            }
            var m = n shr 1
            while (m >= 1 && j >= m) {
                j -= m
                m = m shr 1
            }
            j += m
        }

        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = -2.0 * PI / len.toDouble()
            val wLenR = cos(angle).toFloat()
            val wLenI = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var wR = 1f
                var wI = 0f
                for (k in 0 until halfLen) {
                    val evenIdx = i + k
                    val oddIdx = evenIdx + halfLen
                    val uR = fftReal[evenIdx]
                    val uI = fftImag[evenIdx]
                    val vR = fftReal[oddIdx] * wR - fftImag[oddIdx] * wI
                    val vI = fftReal[oddIdx] * wI + fftImag[oddIdx] * wR
                    fftReal[evenIdx] = uR + vR
                    fftImag[evenIdx] = uI + vI
                    fftReal[oddIdx] = uR - vR
                    fftImag[oddIdx] = uI - vI
                    val nextWR = wR * wLenR - wI * wLenI
                    wI = wR * wLenI + wI * wLenR
                    wR = nextWR
                }
                i += len
            }
            len = len shl 1
        }

        val invN = 1f / n.toFloat()
        val alpha = config.smoothingTimeConstant.coerceIn(0f, 0.99f)
        val useSmoothing = hasSmoothedFrame && alpha > 0f
        for (bin in 0 until bins) {
            val re = fftReal[bin]
            val im = fftImag[bin]
            val mag = sqrt(re * re + im * im) * invN
            val smoothed = if (useSmoothing) {
                alpha * smoothedMag[bin] + (1f - alpha) * mag
            } else {
                mag
            }
            smoothedMag[bin] = smoothed
            val db = if (smoothed > 1e-10f) {
                (20.0 * log10(smoothed.toDouble())).toFloat()
            } else {
                minDb
            }
            freqDbBuffer[bin] = db.coerceAtLeast(minDb)
        }
        hasSmoothedFrame = true
    }

    private fun applyIdleDecayIfNeeded() {
        val mark = lastSampleMark ?: return
        val elapsedSincePushMs = mark.elapsedNow().inWholeMilliseconds
        if (elapsedSincePushMs <= 300L) return

        val prevDecay = lastDecayMark
        if (prevDecay != null && prevDecay.elapsedNow().inWholeMilliseconds < 16L) {
            return
        }
        lastDecayMark = monotonicClock.markNow()

        val decay = config.smoothingTimeConstant.coerceIn(0.1f, 0.95f)
        val minDb = config.minDecibels
        var maxSample = 0f
        for (i in orderedTimeDomain.indices) {
            val v = orderedTimeDomain[i] * decay
            val clean = if (abs(v) < 1e-4f) 0f else v
            orderedTimeDomain[i] = clean
            ringBuffer[i] = clean
            val a = abs(clean)
            if (a > maxSample) maxSample = a
        }

        var anyBinActive = false
        for (bin in smoothedMag.indices) {
            val s = smoothedMag[bin] * decay
            if (s < 1e-6f) {
                smoothedMag[bin] = 0f
                freqDbBuffer[bin] = minDb
            } else {
                smoothedMag[bin] = s
                val db = (20.0 * log10(s.toDouble())).toFloat().coerceAtLeast(minDb)
                freqDbBuffer[bin] = db
                if (db > minDb + 0.5f) {
                    anyBinActive = true
                }
            }
        }

        currentRms *= decay
        if (currentRms < 1e-4f && maxSample < 1e-4f && !anyBinActive) {
            reset()
        }
    }

    fun getByteTimeDomainData(destination: ByteArray) {
        applyIdleDecayIfNeeded()
        val timeData = orderedTimeDomain
        val count = min(destination.size, timeData.size)
        for (i in 0 until count) {
            val sample = timeData[i].coerceIn(-1f, 1f)
            val byteVal = if (abs(sample) < 1e-6f) {
                128
            } else {
                ((sample + 1f) * 127.5f).roundToInt().coerceIn(0, 255)
            }
            destination[i] = byteVal.toByte()
        }
        for (i in count until destination.size) {
            destination[i] = 128.toByte()
        }
    }

    fun getFloatTimeDomainData(destination: FloatArray) {
        applyIdleDecayIfNeeded()
        val timeData = orderedTimeDomain
        val count = min(destination.size, timeData.size)
        timeData.copyInto(destination, 0, 0, count)
        for (i in count until destination.size) {
            destination[i] = 0f
        }
    }

    fun getByteFrequencyData(destination: ByteArray) {
        applyIdleDecayIfNeeded()
        val dbData = freqDbBuffer
        val minDb = config.minDecibels
        var maxDb = config.maxDecibels
        for (v in dbData) {
            if (v > maxDb) maxDb = v
        }
        val range = (maxDb - minDb).coerceAtLeast(1e-3f)
        val count = min(destination.size, dbData.size)
        for (i in 0 until count) {
            val db = dbData[i]
            if (db <= minDb + 1e-4f) {
                destination[i] = 0
            } else {
                val norm = ((db - minDb) / range).coerceIn(0f, 1f)
                destination[i] = (norm * 255f).roundToInt().coerceIn(0, 255).toByte()
            }
        }
        for (i in count until destination.size) {
            destination[i] = 0
        }
    }

    fun getFloatFrequencyData(destination: FloatArray) {
        applyIdleDecayIfNeeded()
        val dbData = freqDbBuffer
        val minDb = config.minDecibels
        val count = min(destination.size, dbData.size)
        dbData.copyInto(destination, 0, 0, count)
        for (i in count until destination.size) {
            destination[i] = minDb
        }
    }

    fun computeEnergyBands(): AudioEnergyBands {
        applyIdleDecayIfNeeded()
        if (currentRms <= 1e-6f) {
            return AudioEnergyBands()
        }
        val dbData = freqDbBuffer
        val binCount = dbData.size
        if (binCount == 0) return AudioEnergyBands()

        val minDb = config.minDecibels
        var maxDb = config.maxDecibels
        for (v in dbData) {
            if (v > maxDb) maxDb = v
        }
        val range = (maxDb - minDb).coerceAtLeast(1e-3f)

        fun bandEnergy(startBin: Int, endBinInclusive: Int): Float {
            if (binCount <= 1) return 0f
            val scale = if (binCount < 512) binCount.toFloat() / 512f else 1f
            val s = (if (scale < 1f) (startBin * scale).toInt() else startBin).coerceIn(1, binCount - 1)
            val e = (if (scale < 1f) (endBinInclusive * scale).toInt() else endBinInclusive)
                .coerceIn(s, binCount - 1)
            var sumSq = 0f
            var peak = 0f
            var count = 0
            for (b in s..e) {
                val db = dbData[b]
                val norm = if (db <= minDb + 1e-4f) 0f else ((db - minDb) / range).coerceIn(0f, 1f)
                sumSq += norm * norm
                if (norm > peak) peak = norm
                count++
            }
            if (count == 0 || peak <= 0f) return 0f
            val rms = sqrt(sumSq / count.toFloat())
            return (rms * 0.6f + peak * 0.4f).coerceIn(0f, 1f)
        }

        val bass = bandEnergy(1, 12)
        val lowMid = bandEnergy(13, 32)
        val mid = bandEnergy(33, 96)
        val highMid = bandEnergy(97, 220)
        val trebleEnd = min(511, (binCount - 1).coerceAtLeast(1))
        val treble = if (trebleEnd >= 221 || binCount < 512) {
            bandEnergy(221, maxOf(221, trebleEnd))
        } else {
            0f
        }
        val rms = currentRms.coerceIn(0f, 1f)
        val beat = rms >= 0.30f || bass >= 0.60f

        return AudioEnergyBands(
            bass = bass,
            lowMid = lowMid,
            mid = mid,
            highMid = highMid,
            treble = treble,
            overallRms = rms,
            peakBeatDetected = beat
        )
    }

    fun reset() {
        ringBuffer.fill(0f)
        orderedTimeDomain.fill(0f)
        ringWritePos = 0
        fftReal.fill(0f)
        fftImag.fill(0f)
        smoothedMag.fill(0f)
        freqDbBuffer.fill(config.minDecibels)
        hasSmoothedFrame = false
        currentRms = 0f
        lastSampleMark = null
        lastDecayMark = null
    }
}
