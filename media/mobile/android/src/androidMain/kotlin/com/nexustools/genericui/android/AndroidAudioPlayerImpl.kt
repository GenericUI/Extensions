package com.nexustools.genericui.android

import android.media.MediaCodecList
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl

/**
 * Android `android.media.MediaPlayer` + `android.media.audiofx.Visualizer` implementation of [AudioPlayerNativeImpl].
 */
class AndroidAudioPlayerImpl : AudioPlayerNativeImpl {
    private var sourceUrl: String = ""
    private var currentPositionSeconds: Double = 0.0
    private var durationSeconds: Double = 120.0
    private var playing: Boolean = false
    private var volume: Float = 1f
    private var analyzer: AudioAnalyzer? = null
    private var mediaPlayer: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    private var onTimeUpdate: ((Double) -> Unit)? = null
    private var onDurationChanged: ((Double) -> Unit)? = null
    private var onBufferedRangesChanged: ((List<Pair<Double, Double>>) -> Unit)? = null
    private var onEnded: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onMediaReady: (() -> Unit)? = null
    private var mediaReady: Boolean = false

    override fun isAudioCodecSupported(codec: AudioCodec): Boolean = runCatching {
        val codecInfos = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos ?: return@runCatching false
        codecInfos.any { info ->
            !info.isEncoder && (info.supportedTypes?.any { supportedMime ->
                codec.mimeTypes.any { targetMime -> supportedMime.equals(targetMime, ignoreCase = true) }
            } == true)
        }
    }.getOrDefault(false)

    override fun getSupportedAudioCodecs(): Set<AudioCodec> =
        AudioCodec.entries.filterTo(linkedSetOf()) { isAudioCodecSupported(it) }

    private fun attachVisualizerIfPossible() {
        val activeAnalyzer = analyzer ?: run {
            releaseVisualizer()
            return
        }
        val mp = mediaPlayer ?: return
        runCatching {
            val sessionId = mp.audioSessionId
            if (sessionId <= 0) return@runCatching
            releaseVisualizer()
            val viz = Visualizer(sessionId)
            val range = Visualizer.getCaptureSizeRange()
            val targetSize = activeAnalyzer.fftSize.coerceIn(range[0], range[1])
            viz.captureSize = targetSize
            viz.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        val wf = waveform ?: return
                        if (wf.isEmpty()) return
                        val floats = FloatArray(wf.size) { i ->
                            val u = wf[i].toInt() and 0xFF
                            ((u - 128) / 128f).coerceIn(-1f, 1f)
                        }
                        analyzer?.pushPcmSamples(floats, channels = 1)
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                    }
                },
                Visualizer.getMaxCaptureRate() / 2,
                true,
                false
            )
            viz.enabled = true
            visualizer = viz
        }
    }

    private fun releaseVisualizer() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
    }

    override fun setSource(url: String, seekOffsetSeconds: Double) {
        sourceUrl = url
        currentPositionSeconds = seekOffsetSeconds.coerceAtLeast(0.0)
        mediaReady = url.isNotBlank()
        runCatching {
            releaseVisualizer()
            mediaPlayer?.release()
            mediaPlayer = null
            if (url.isNotBlank() && !url.startsWith("synth://")) {
                val mp = MediaPlayer()
                mp.setDataSource(url)
                mp.setVolume(volume, volume)
                mp.setOnPreparedListener { prepared ->
                    val durMs = prepared.duration
                    if (durMs > 0) {
                        durationSeconds = durMs / 1000.0
                        onDurationChanged?.invoke(durationSeconds)
                    }
                    if (currentPositionSeconds > 0.0) {
                        prepared.seekTo((currentPositionSeconds * 1000.0).toInt())
                    }
                    mediaReady = true
                    onMediaReady?.invoke()
                    if (playing) {
                        prepared.start()
                        attachVisualizerIfPossible()
                    }
                }
                mp.setOnCompletionListener {
                    playing = false
                    onEnded?.invoke()
                }
                mp.setOnErrorListener { _, what, extra ->
                    playing = false
                    onError?.invoke("Android MediaPlayer error ($what, $extra)")
                    true
                }
                mediaPlayer = mp
                mp.prepareAsync()
            }
        }
        if (mediaReady) {
            onMediaReady?.invoke()
        }
    }

    override fun play() {
        playing = true
        runCatching {
            mediaPlayer?.start()
            attachVisualizerIfPossible()
        }
    }

    override fun pause() {
        playing = false
        runCatching {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        }
    }

    override fun stop() {
        playing = false
        currentPositionSeconds = 0.0
        releaseVisualizer()
        runCatching {
            mediaPlayer?.stop()
        }
    }

    override fun seekTo(seconds: Double) {
        currentPositionSeconds = seconds.coerceAtLeast(0.0)
        runCatching {
            mediaPlayer?.seekTo((currentPositionSeconds * 1000.0).toInt())
        }
        onTimeUpdate?.invoke(currentPositionSeconds)
    }

    override fun getCurrentTimeSeconds(): Double {
        val posMs = runCatching {
            if (mediaPlayer?.isPlaying == true) mediaPlayer?.currentPosition ?: -1 else -1
        }.getOrDefault(-1)
        if (posMs >= 0) {
            currentPositionSeconds = posMs / 1000.0
        }
        return currentPositionSeconds
    }

    override fun getDurationSeconds(): Double {
        val durMs = runCatching { mediaPlayer?.duration ?: -1 }.getOrDefault(-1)
        if (durMs > 0) {
            durationSeconds = durMs / 1000.0
        }
        return durationSeconds
    }

    override fun isPlaying(): Boolean = playing

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        runCatching {
            mediaPlayer?.setVolume(this.volume, this.volume)
        }
    }

    override fun getVolume(): Float = volume

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        this.analyzer = analyzer
        if (analyzer != null && playing) {
            attachVisualizerIfPossible()
        } else if (analyzer == null) {
            releaseVisualizer()
        }
    }

    override fun setOnTimeUpdate(listener: ((Double) -> Unit)?) { onTimeUpdate = listener }
    override fun setOnDurationChanged(listener: ((Double) -> Unit)?) { onDurationChanged = listener }
    override fun setOnBufferedRangesChanged(listener: ((List<Pair<Double, Double>>) -> Unit)?) {
        onBufferedRangesChanged = listener
    }
    override fun setOnEnded(listener: (() -> Unit)?) { onEnded = listener }
    override fun setOnError(listener: ((String) -> Unit)?) { onError = listener }
    override fun setOnMediaReady(listener: (() -> Unit)?) {
        onMediaReady = listener
        if (mediaReady) listener?.invoke()
    }
    override fun isMediaReady(): Boolean = mediaReady

    override fun dispose() {
        releaseVisualizer()
        runCatching {
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }
}
