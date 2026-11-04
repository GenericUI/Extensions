package com.nexustools.genericui.web

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.MediaPreloadMode
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import kotlin.math.min

/**
 * Web HTML5 `<audio>` + Web Audio API implementation of [AudioPlayerNativeImpl].
 */
class WebAudioPlayerImpl : WebBaseNativeImpl("audio", "audio-player"), AudioPlayerNativeImpl<WebDomElement> {
    private var currentTimeSec: Double = 0.0
    private var durationSec: Double = 0.0
    private var playing: Boolean = false
    private var volumeLevel: Float = 1.0f
    private var preloadMode: MediaPreloadMode = MediaPreloadMode.AUTO
    private var adaptivePreloadBufferingEnabled: Boolean = true
    private var mediaReadyFlag: Boolean = false
    private var smoothedNetworkRateAudioSecPerSec: Double = 2.0
    private var bufferedRangesList: List<Pair<Double, Double>> = emptyList()
    private var attachedAnalyzer: AudioAnalyzer? = null
    private var onTimeUpdateListener: ((Double) -> Unit)? = null
    private var onDurationChangedListener: ((Double) -> Unit)? = null
    private var onBufferedRangesChangedListener: ((List<Pair<Double, Double>>) -> Unit)? = null
    private var onEndedListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    private var onMediaReadyListener: (() -> Unit)? = null

    private fun markMediaReady() {
        if (!mediaReadyFlag) {
            mediaReadyFlag = true
            nativeWidget.setAttribute("data-media-ready", "true")
            onMediaReadyListener?.invoke()
        }
    }

    init {
        nativeWidget.setAttribute("preload", "auto")
        nativeWidget.setAttribute("data-adaptive-preload-buffering", "true")
        WebBrowserDomBridge.attachMediaElementListeners(nativeWidget)
        nativeWidget.addEventListener("play") {
            playing = true
        }
        nativeWidget.addEventListener("pause") {
            playing = false
        }
        nativeWidget.addEventListener("ended") {
            playing = false
            onEndedListener?.invoke()
        }
        nativeWidget.addEventListener("timeupdate") { payload ->
            val sec = (payload as? Number)?.toDouble()
                ?: nativeWidget.getAttribute("data-current-time")?.toDoubleOrNull()
            if (sec != null && !sec.isNaN() && sec >= 0.0) {
                currentTimeSec = sec
            }
            onTimeUpdateListener?.invoke(currentTimeSec)
        }
        nativeWidget.addEventListener("canplay") {
            markMediaReady()
        }
        nativeWidget.addEventListener("loadeddata") {
            markMediaReady()
        }
        nativeWidget.addEventListener("loadedmetadata") {
            markMediaReady()
        }
        nativeWidget.addEventListener("durationchange") { payload ->
            val dur = (payload as? Number)?.toDouble()
                ?: nativeWidget.getAttribute("data-duration")?.toDoubleOrNull()
            if (dur != null && !dur.isNaN() && dur > 0.0) {
                durationSec = dur
                markMediaReady()
                onDurationChangedListener?.invoke(durationSec)
            }
        }
        nativeWidget.addEventListener("progress") { payload ->
            val rawRanges = nativeWidget.getAttribute("data-buffered-ranges")?.trim()
            if (!rawRanges.isNullOrEmpty()) {
                val parsed = rawRanges.split(',').mapNotNull { token ->
                    val dashIdx = token.indexOf('-')
                    if (dashIdx > 0) {
                        val s = token.substring(0, dashIdx).trim().toDoubleOrNull()
                        val e = token.substring(dashIdx + 1).trim().toDoubleOrNull()
                        if (s != null && e != null && !s.isNaN() && !e.isNaN() && e > s) s to e else null
                    } else null
                }
                if (parsed.isNotEmpty()) {
                    bufferedRangesList = parsed
                }
            } else {
                val bufEnd = (payload as? Number)?.toDouble()
                    ?: nativeWidget.getAttribute("data-buffered-time")?.toDoubleOrNull()
                if (bufEnd != null && !bufEnd.isNaN() && bufEnd >= 0.0) {
                    bufferedRangesList = listOf(0.0 to bufEnd)
                }
            }
            onBufferedRangesChangedListener?.invoke(bufferedRangesList)
        }
        nativeWidget.addEventListener("error") {
            onErrorListener?.invoke("Audio playback error")
        }
    }

    override fun getSupportedAudioCodecs(): Set<AudioCodec> = setOf(
        AudioCodec.AAC,
        AudioCodec.MP3,
        AudioCodec.OPUS,
        AudioCodec.VORBIS,
        AudioCodec.FLAC,
        AudioCodec.PCM
    )

    override fun setSource(url: String, seekOffsetSeconds: Double) {
        currentTimeSec = seekOffsetSeconds.coerceAtLeast(0.0)
        mediaReadyFlag = false
        nativeWidget.setAttribute("data-media-ready", "false")
        nativeWidget.setAttribute("src", url)
        WebBrowserDomBridge.mediaLoad(nativeWidget, currentTimeSec)
        if (url.isNotBlank() && !WebBrowserDomBridge.isBrowserEnvironment) {
            markMediaReady()
        }
    }

    override fun play() {
        playing = true
        WebBrowserDomBridge.mediaPlay(nativeWidget) { errMsg ->
            playing = false
            onErrorListener?.invoke(errMsg)
        }
    }

    override fun pause() {
        playing = false
        WebBrowserDomBridge.mediaPause(nativeWidget)
    }

    override fun stop() {
        playing = false
        currentTimeSec = 0.0
        WebBrowserDomBridge.mediaPause(nativeWidget)
        WebBrowserDomBridge.mediaSeekTo(nativeWidget, 0.0)
    }

    override fun seekTo(seconds: Double) {
        currentTimeSec = seconds.coerceAtLeast(0.0)
        WebBrowserDomBridge.mediaSeekTo(nativeWidget, currentTimeSec)
        onTimeUpdateListener?.invoke(currentTimeSec)
    }

    override fun getCurrentTimeSeconds(): Double = currentTimeSec
    override fun getDurationSeconds(): Double = durationSec
    override fun isPlaying(): Boolean = playing

    override fun setVolume(volume: Float) {
        volumeLevel = volume.coerceIn(0f, 1f)
        WebBrowserDomBridge.mediaSetVolume(nativeWidget, volumeLevel)
    }

    override fun getVolume(): Float = volumeLevel

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        attachedAnalyzer = analyzer
    }

    override fun setOnTimeUpdate(listener: ((Double) -> Unit)?) { onTimeUpdateListener = listener }
    override fun setOnDurationChanged(listener: ((Double) -> Unit)?) { onDurationChangedListener = listener }
    override fun setOnBufferedRangesChanged(listener: ((List<Pair<Double, Double>>) -> Unit)?) {
        onBufferedRangesChangedListener = listener
    }
    override fun setOnEnded(listener: (() -> Unit)?) { onEndedListener = listener }
    override fun setOnError(listener: ((String) -> Unit)?) { onErrorListener = listener }

    override fun setPreloadMode(mode: MediaPreloadMode) {
        preloadMode = mode
        nativeWidget.setAttribute("preload", mode.htmlAttributeValue)
    }

    override fun getPreloadMode(): MediaPreloadMode = preloadMode

    override fun setAdaptivePreloadBufferingEnabled(enabled: Boolean) {
        adaptivePreloadBufferingEnabled = enabled
        nativeWidget.setAttribute("data-adaptive-preload-buffering", enabled.toString())
    }

    override fun isAdaptivePreloadBufferingEnabled(): Boolean = adaptivePreloadBufferingEnabled

    override fun setOnMediaReady(listener: (() -> Unit)?) {
        onMediaReadyListener = listener
    }

    override fun isMediaReady(): Boolean = mediaReadyFlag

    override fun getBufferedRanges(): List<Pair<Double, Double>> = bufferedRangesList

    override fun getEstimatedNetworkSpeedRatio(): Double = smoothedNetworkRateAudioSecPerSec

    override fun getRequiredSafeBufferSeconds(): Double {
        val d = (durationSec - currentTimeSec).coerceAtLeast(0.0)
        if (d <= 0.0) return 0.0
        return min(d, 8.0)
    }
}
