package com.nexustools.genericui.web

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.MediaPreloadMode
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import kotlin.math.min

/**
 * Web HTML5 `<audio>` + Web Audio API implementation of [AudioPlayerNativeImpl].
 */
class WebAudioPlayerImpl : AudioPlayerNativeImpl {
    internal val audioElement: WebDomElement = WebDomElement("audio")

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
            audioElement.setAttribute("data-media-ready", "true")
            onMediaReadyListener?.invoke()
        }
    }

    init {
        audioElement.setAttribute("crossorigin", "anonymous")
        audioElement.setAttribute("preload", "auto")
        audioElement.setAttribute("style", "display:none")
        audioElement.style["display"] = "none"
        audioElement.setAttribute("data-adaptive-preload-buffering", "true")
        WebAudioContextBridge.ensureCrossOriginAnonymous(audioElement)
        WebBrowserDomBridge.attachMediaElementListeners(audioElement)
        audioElement.addEventListener("play") {
            playing = true
            audioElement.setAttribute("data-playing", "true")
            val analyzer = attachedAnalyzer
            if (analyzer != null) {
                WebAudioContextBridge.connectMediaElement(audioElement, analyzer.fftSize)
            }
            WebAudioContextBridge.resumeAudioContext()
        }
        audioElement.addEventListener("playing") {
            playing = true
            audioElement.setAttribute("data-playing", "true")
            val analyzer = attachedAnalyzer
            if (analyzer != null) {
                WebAudioContextBridge.connectMediaElement(audioElement, analyzer.fftSize)
            }
            WebAudioContextBridge.resumeAudioContext()
        }
        audioElement.addEventListener("pause") {
            playing = false
            audioElement.setAttribute("data-playing", "false")
        }
        audioElement.addEventListener("ended") {
            playing = false
            audioElement.setAttribute("data-playing", "false")
            onEndedListener?.invoke()
        }
        audioElement.addEventListener("timeupdate") { payload ->
            val sec = (payload as? Number)?.toDouble()
                ?: audioElement.getAttribute("data-current-time")?.toDoubleOrNull()
            if (sec != null && !sec.isNaN() && sec >= 0.0) {
                currentTimeSec = sec
            }
            onTimeUpdateListener?.invoke(currentTimeSec)
        }
        audioElement.addEventListener("canplay") {
            markMediaReady()
        }
        audioElement.addEventListener("loadeddata") {
            markMediaReady()
        }
        audioElement.addEventListener("loadedmetadata") {
            markMediaReady()
        }
        audioElement.addEventListener("durationchange") { payload ->
            val dur = (payload as? Number)?.toDouble()
                ?: audioElement.getAttribute("data-duration")?.toDoubleOrNull()
            if (dur != null && !dur.isNaN() && dur > 0.0) {
                durationSec = dur
                markMediaReady()
                onDurationChangedListener?.invoke(durationSec)
            }
        }
        audioElement.addEventListener("progress") { payload ->
            val rawRanges = audioElement.getAttribute("data-buffered-ranges")?.trim()
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
                    ?: audioElement.getAttribute("data-buffered-time")?.toDoubleOrNull()
                if (bufEnd != null && !bufEnd.isNaN() && bufEnd >= 0.0) {
                    bufferedRangesList = listOf(0.0 to bufEnd)
                }
            }
            onBufferedRangesChangedListener?.invoke(bufferedRangesList)
        }
        audioElement.addEventListener("error") {
            onErrorListener?.invoke("Audio playback error")
        }
    }

    override fun isAudioCodecSupported(codec: AudioCodec): Boolean =
        codec.html5CanPlayProbes.any { WebBrowserDomBridge.mediaCanPlayType(audioElement, it) }

    override fun getSupportedAudioCodecs(): Set<AudioCodec> =
        AudioCodec.entries.filterTo(linkedSetOf()) { isAudioCodecSupported(it) }

    override fun setSource(url: String, seekOffsetSeconds: Double) {
        currentTimeSec = seekOffsetSeconds.coerceAtLeast(0.0)
        mediaReadyFlag = false
        audioElement.setAttribute("crossorigin", "anonymous")
        WebAudioContextBridge.ensureCrossOriginAnonymous(audioElement)
        audioElement.setAttribute("data-media-ready", "false")
        audioElement.setAttribute("src", url)
        val analyzer = attachedAnalyzer
        if (analyzer != null) {
            WebAudioContextBridge.connectMediaElement(audioElement, analyzer.fftSize)
        }
        WebBrowserDomBridge.mediaLoad(audioElement, currentTimeSec)
        if (url.isNotBlank() && !WebBrowserDomBridge.isBrowserEnvironment) {
            markMediaReady()
        }
    }

    override fun play() {
        playing = true
        audioElement.setAttribute("data-playing", "true")
        val analyzer = attachedAnalyzer
        if (analyzer != null) {
            WebAudioContextBridge.connectMediaElement(audioElement, analyzer.fftSize)
        }
        WebAudioContextBridge.resumeAudioContext()
        WebBrowserDomBridge.mediaPlay(audioElement) { errMsg ->
            playing = false
            audioElement.setAttribute("data-playing", "false")
            onErrorListener?.invoke(errMsg)
        }
    }

    override fun pause() {
        playing = false
        audioElement.setAttribute("data-playing", "false")
        WebBrowserDomBridge.mediaPause(audioElement)
    }

    override fun stop() {
        playing = false
        currentTimeSec = 0.0
        audioElement.setAttribute("data-playing", "false")
        WebBrowserDomBridge.mediaPause(audioElement)
        WebBrowserDomBridge.mediaSeekTo(audioElement, 0.0)
    }

    override fun seekTo(seconds: Double) {
        currentTimeSec = seconds.coerceAtLeast(0.0)
        WebBrowserDomBridge.mediaSeekTo(audioElement, currentTimeSec)
        onTimeUpdateListener?.invoke(currentTimeSec)
    }

    override fun getCurrentTimeSeconds(): Double = currentTimeSec
    override fun getDurationSeconds(): Double = durationSec
    override fun isPlaying(): Boolean = playing

    override fun setVolume(volume: Float) {
        volumeLevel = volume.coerceIn(0f, 1f)
        WebBrowserDomBridge.mediaSetVolume(audioElement, volumeLevel)
    }

    override fun getVolume(): Float = volumeLevel

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        attachedAnalyzer = analyzer
        if (analyzer != null) {
            audioElement.setAttribute("crossorigin", "anonymous")
            WebAudioContextBridge.connectMediaElement(audioElement, analyzer.fftSize)
            if (playing) {
                WebAudioContextBridge.resumeAudioContext()
            }
        }
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
        audioElement.setAttribute("preload", mode.htmlAttributeValue)
    }

    override fun getPreloadMode(): MediaPreloadMode = preloadMode

    override fun setAdaptivePreloadBufferingEnabled(enabled: Boolean) {
        adaptivePreloadBufferingEnabled = enabled
        audioElement.setAttribute("data-adaptive-preload-buffering", enabled.toString())
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

    override fun dispose() {
        playing = false
        currentTimeSec = 0.0
        WebBrowserDomBridge.mediaPause(audioElement)
        audioElement.removeAttribute("src")
        audioElement.parent?.let { parent ->
            WebBrowserDomBridge.removeChild(parent, audioElement)
        }
    }
}
