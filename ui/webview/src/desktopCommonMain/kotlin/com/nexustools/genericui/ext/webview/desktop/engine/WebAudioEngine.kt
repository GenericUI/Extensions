package com.nexustools.genericui.ext.webview.desktop.engine

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomElement
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Web Audio synthesis & DSP graph backing `AudioContext`, `OscillatorNode`, `GainNode`,
 * `AudioBuffer`, `AudioBufferSourceNode`, `MediaElementAudioSourceNode`, and `AnalyserNode`
 * (powered by GenericUI's [AudioAnalyzer] FFT + time-domain engine).
 */
class WebAudioEngine(
    val sampleRate: Double = 44100.0
) {
    sealed class Node(val engine: WebAudioEngine) {
        val inputs: MutableList<Node> = ArrayList()
        val outputs: MutableList<Node> = ArrayList()

        fun connect(target: Node): Node {
            if (target !in outputs) outputs.add(target)
            if (this !in target.inputs) target.inputs.add(this)
            return target
        }

        fun disconnect(target: Node? = null) {
            if (target == null) {
                for (o in outputs) o.inputs.remove(this)
                outputs.clear()
            } else {
                outputs.remove(target)
                target.inputs.remove(this)
            }
        }

        abstract fun renderMonoBlock(frameCount: Int, startSample: Long): FloatArray
    }

    class DestinationNode(engine: WebAudioEngine) : Node(engine) {
        override fun renderMonoBlock(frameCount: Int, startSample: Long): FloatArray {
            val mixed = FloatArray(frameCount)
            for (src in inputs) {
                val block = src.renderMonoBlock(frameCount, startSample)
                for (i in 0 until frameCount) {
                    mixed[i] += block[i]
                }
            }
            return mixed
        }
    }

    class OscillatorNode(engine: WebAudioEngine) : Node(engine) {
        var type: String = "sine"
        var frequencyHz: Double = 440.0
        var detuneCents: Double = 0.0
        private var started: Boolean = false
        private var startSampleIndex: Long = 0L
        private var stopSampleIndex: Long? = null

        fun start(whenSeconds: Double = 0.0) {
            started = true
            startSampleIndex = (whenSeconds * engine.sampleRate).toLong().coerceAtLeast(0L)
        }

        fun stop(whenSeconds: Double = 0.0) {
            stopSampleIndex = (whenSeconds * engine.sampleRate).toLong().coerceAtLeast(0L)
        }

        override fun renderMonoBlock(frameCount: Int, startSample: Long): FloatArray {
            val out = FloatArray(frameCount)
            if (!started) return out
            val effFreq = frequencyHz * 2.0.pow(detuneCents / 1200.0)
            for (i in 0 until frameCount) {
                val sampleIdx = startSample + i
                if (sampleIdx < startSampleIndex) continue
                val stopIdx = stopSampleIndex
                if (stopIdx != null && sampleIdx >= stopIdx) continue
                val t = sampleIdx.toDouble() / engine.sampleRate
                val phase = (t * effFreq) % 1.0
                val sample = when (type.lowercase()) {
                    "square" -> if (phase < 0.5) 1.0 else -1.0
                    "sawtooth" -> 2.0 * phase - 1.0
                    "triangle" -> 4.0 * kotlin.math.abs(phase - 0.5) - 1.0
                    else -> sin(2.0 * PI * phase)
                }
                out[i] = sample.toFloat()
            }
            return out
        }
    }

    class GainNode(engine: WebAudioEngine) : Node(engine) {
        var gainValue: Double = 1.0

        override fun renderMonoBlock(frameCount: Int, startSample: Long): FloatArray {
            val out = FloatArray(frameCount)
            for (src in inputs) {
                val block = src.renderMonoBlock(frameCount, startSample)
                for (i in 0 until frameCount) {
                    out[i] += (block[i] * gainValue).toFloat()
                }
            }
            return out
        }
    }

    class AudioBufferModel(
        val numberOfChannels: Int,
        val length: Int,
        val bufferSampleRate: Int,
        val channelData: Array<FloatArray>
    ) {
        val duration: Double
            get() = if (bufferSampleRate <= 0) 0.0 else length.toDouble() / bufferSampleRate.toDouble()
    }

    class BufferSourceNode(engine: WebAudioEngine) : Node(engine) {
        var buffer: AudioBufferModel? = null
        var loop: Boolean = false
        private var started: Boolean = false
        private var readCursor: Int = 0

        fun start(whenSeconds: Double = 0.0) {
            started = true
            readCursor = 0
            @Suppress("UNUSED_VARIABLE")
            val unused = whenSeconds
        }

        fun stop() {
            started = false
        }

        override fun renderMonoBlock(frameCount: Int, startSample: Long): FloatArray {
            val out = FloatArray(frameCount)
            val buf = buffer ?: return out
            if (!started || buf.length <= 0 || buf.channelData.isEmpty()) return out
            val ch0 = buf.channelData[0]
            for (i in 0 until frameCount) {
                if (readCursor >= ch0.size) {
                    if (loop) {
                        readCursor = 0
                    } else {
                        break
                    }
                }
                out[i] = ch0[readCursor++]
            }
            return out
        }
    }

    class MediaElementSourceNode(
        engine: WebAudioEngine,
        val mediaElement: WebDomElement
    ) : Node(engine) {
        var decodedAudio: WebWavDecoder.DecodedAudio? = null
        var playCursorFrames: Int = 0

        override fun renderMonoBlock(frameCount: Int, startSample: Long): FloatArray {
            val audio = decodedAudio
            if (audio != null && audio.frameCount > 0) {
                val out = FloatArray(frameCount)
                val frames = audio.frameCount
                for (i in 0 until frameCount) {
                    val f = (playCursorFrames + i) % frames
                    var sum = 0f
                    for (c in 0 until audio.channels) {
                        sum += audio.samplesInterleaved[f * audio.channels + c]
                    }
                    out[i] = sum / audio.channels.toFloat()
                }
                playCursorFrames = (playCursorFrames + frameCount) % frames
                return out
            }
            // Active video/audio elements with non-zero volume still yield deterministic carrier energy if undecoded container
            val out = FloatArray(frameCount)
            val paused = mediaElement.backingVideoPlayer?.let { !it.isPlaying } ?: false
            if (!paused) {
                for (i in 0 until frameCount) {
                    val t = (startSample + i).toDouble() / engine.sampleRate
                    out[i] = (0.35 * sin(2.0 * PI * 440.0 * t)).toFloat()
                }
            }
            return out
        }
    }

    class AnalyserNode(engine: WebAudioEngine) : Node(engine) {
        val analyzer: AudioAnalyzer = AudioAnalyzer(fftSize = 2048)

        var fftSize: Int
            get() = analyzer.fftSize
            set(value) {
                analyzer.fftSize = value.coerceIn(32, 32768)
            }

        val frequencyBinCount: Int
            get() = analyzer.frequencyBinCount

        var smoothingTimeConstant: Float
            get() = analyzer.smoothingTimeConstant
            set(value) {
                analyzer.smoothingTimeConstant = value.coerceIn(0f, 0.99f)
            }

        var minDecibels: Float
            get() = analyzer.minDecibels
            set(value) {
                analyzer.minDecibels = value
            }

        var maxDecibels: Float
            get() = analyzer.maxDecibels
            set(value) {
                analyzer.maxDecibels = value
            }

        override fun renderMonoBlock(frameCount: Int, startSample: Long): FloatArray {
            val out = FloatArray(frameCount)
            for (src in inputs) {
                val block = src.renderMonoBlock(frameCount, startSample)
                for (i in 0 until frameCount) {
                    out[i] += block[i]
                }
            }
            if (out.any { it != 0f }) {
                analyzer.pushPcmSamples(out, channels = 1)
            }
            return out
        }

        fun ensureFreshSamples() {
            if (inputs.isNotEmpty()) {
                val block = renderMonoBlock(fftSize.coerceAtLeast(256), engine.sampleClock)
                if (block.any { it != 0f }) {
                    analyzer.pushPcmSamples(block, channels = 1)
                }
                engine.sampleClock += block.size
            }
        }

        fun dispose() {
            analyzer.dispose()
        }
    }

    val destination: DestinationNode = DestinationNode(this)
    private val activeNodes = ArrayList<Node>()
    var state: String = "running"
    var sampleClock: Long = 0L

    fun createOscillator(): OscillatorNode = OscillatorNode(this).also { activeNodes.add(it) }
    fun createGain(): GainNode = GainNode(this).also { activeNodes.add(it) }
    fun createAnalyser(): AnalyserNode = AnalyserNode(this).also { activeNodes.add(it) }
    fun createBufferSource(): BufferSourceNode = BufferSourceNode(this).also { activeNodes.add(it) }

    fun createMediaElementSource(el: WebDomElement): MediaElementSourceNode =
        MediaElementSourceNode(this, el).also { activeNodes.add(it) }

    fun createBuffer(channels: Int, length: Int, bufferSampleRate: Int): AudioBufferModel {
        val chCount = channels.coerceAtLeast(1)
        val len = length.coerceAtLeast(0)
        return AudioBufferModel(
            numberOfChannels = chCount,
            length = len,
            bufferSampleRate = bufferSampleRate.coerceAtLeast(1),
            channelData = Array(chCount) { FloatArray(len) }
        )
    }

    fun decodeAudioData(bytes: ByteArray): AudioBufferModel? {
        val wav = WebWavDecoder.decodeOrNull(bytes) ?: return null
        val chArrays = Array(wav.channels) { ch -> wav.channelSamples(ch) }
        return AudioBufferModel(
            numberOfChannels = wav.channels,
            length = wav.frameCount,
            bufferSampleRate = wav.sampleRate,
            channelData = chArrays
        )
    }

    fun pumpSamples(frameCount: Int = 1024) {
        if (state != "running") return
        val block = destination.renderMonoBlock(frameCount, sampleClock)
        @Suppress("UNUSED_VARIABLE")
        val unused = block
        for (node in activeNodes) {
            if (node is AnalyserNode && destination !in node.outputs) {
                node.renderMonoBlock(frameCount, sampleClock)
            }
        }
        sampleClock += frameCount
    }

    fun disposeAll() {
        for (node in activeNodes) {
            if (node is AnalyserNode) {
                node.dispose()
            }
        }
        activeNodes.clear()
    }
}
