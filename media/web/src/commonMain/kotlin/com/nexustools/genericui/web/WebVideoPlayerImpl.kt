package com.nexustools.genericui.web

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.component.media.MediaTrackInfo
import com.nexustools.genericui.component.media.SubtitleTrackInfo
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.CodecSupportLevel
import com.nexustools.genericui.enums.media.MediaContainer
import com.nexustools.genericui.enums.media.MediaPreloadMode
import com.nexustools.genericui.enums.media.VideoCodec
import com.nexustools.genericui.enums.media.VideoResizeMode
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl
import kotlin.math.min

/**
 * Web HTML5 `<video>` + MSE/HLS implementation of [VideoPlayerNativeImpl].
 */
class WebVideoPlayerImpl : WebBaseNativeImpl("video", "video-player"), VideoPlayerNativeImpl<WebDomElement> {
    private var sourceUrl: String = ""
    private var currentTimeSec: Double = 0.0
    private var transcodeBaseOffsetSec: Double = 0.0
    private var pendingDirectSeekSec: Double? = null
    private var durationSec: Double = 0.0
    private var durationOverrideSec: Double? = null
    private var isTranscodeStream: Boolean = false
    private var playing: Boolean = false
    private var buffering: Boolean = false
    private var speed: Float = 1.0f
    private var volumeLevel: Float = 1.0f
    private var mutedFlag: Boolean = false
    private var resizeMode: VideoResizeMode = VideoResizeMode.FIT
    private var preloadMode: MediaPreloadMode = MediaPreloadMode.AUTO
    private var adaptivePreloadBufferingEnabled: Boolean = true
    private var antialiasingEnabled: Boolean = true
    private var mediaReadyFlag: Boolean = false
    private var lastProgressWallTimeMs: Long = 0L
    private var lastProgressBufferedEndSec: Double = 0.0
    private var smoothedNetworkRateVideoSecPerSec: Double = 1.5
    private var pipActive: Boolean = false
    private val audioTracks = mutableListOf<MediaTrackInfo>()
    private val subtitleTracks = mutableListOf<SubtitleTrackInfo>()
    private var activeSubtitleTrackId: String? = null
    private var bufferedRangesList: List<Pair<Double, Double>> = emptyList()
    private var attachedAnalyzer: AudioAnalyzer? = null

    private var onTimeUpdateListener: ((Double) -> Unit)? = null
    private var onDurationChangedListener: ((Double) -> Unit)? = null
    private var onEndedListener: (() -> Unit)? = null
    private var onBufferingChangedListener: ((Boolean) -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    private var onPlayingStateChangedListener: ((Boolean) -> Unit)? = null
    private var onBufferingStateChangedListener: ((Boolean) -> Unit)? = null
    private var onBufferedRangesChangedListener: ((List<Pair<Double, Double>>) -> Unit)? = null
    private var onTracksChangedListener: (() -> Unit)? = null
    private var onMediaReadyListener: (() -> Unit)? = null

    private fun markMediaReady() {
        if (!mediaReadyFlag) {
            mediaReadyFlag = true
            nativeWidget.setAttribute("data-media-ready", "true")
            onMediaReadyListener?.invoke()
        }
    }

    private fun applyAntialiasingStyles() {
        nativeWidget.setAttribute("data-antialiasing", antialiasingEnabled.toString())
        if (antialiasingEnabled) {
            nativeWidget.style["image-rendering"] = "auto"
            nativeWidget.style["transform"] = "translateZ(0)"
            nativeWidget.style["backface-visibility"] = "hidden"
        } else {
            nativeWidget.style["image-rendering"] = "pixelated"
            nativeWidget.style.remove("transform")
            nativeWidget.style.remove("backface-visibility")
        }
    }

    init {
        nativeWidget.setAttribute("playsinline", "")
        nativeWidget.setAttribute("webkit-playsinline", "")
        nativeWidget.setAttribute("preload", "auto")
        nativeWidget.setAttribute("data-adaptive-preload-buffering", "true")
        nativeWidget.style["width"] = "100%"
        nativeWidget.style["height"] = "100%"
        nativeWidget.style["display"] = "block"
        nativeWidget.style["object-fit"] = "contain"
        nativeWidget.style["background-color"] = "#000000"
        applyAntialiasingStyles()
        WebBrowserDomBridge.attachMediaElementListeners(nativeWidget)

        nativeWidget.addEventListener("play") {
            playing = true
            nativeWidget.setAttribute("data-playing", "true")
            onPlayingStateChangedListener?.invoke(true)
        }
        nativeWidget.addEventListener("pause") {
            playing = false
            nativeWidget.setAttribute("data-playing", "false")
            onPlayingStateChangedListener?.invoke(false)
        }
        nativeWidget.addEventListener("ended") {
            playing = false
            nativeWidget.setAttribute("data-playing", "false")
            onPlayingStateChangedListener?.invoke(false)
            onEndedListener?.invoke()
        }
        nativeWidget.addEventListener("waiting") {
            buffering = true
            onBufferingChangedListener?.invoke(true)
            onBufferingStateChangedListener?.invoke(true)
        }
        nativeWidget.addEventListener("playing") {
            playing = true
            nativeWidget.setAttribute("data-playing", "true")
            if (buffering) {
                buffering = false
                onBufferingChangedListener?.invoke(false)
                onBufferingStateChangedListener?.invoke(false)
            }
            onPlayingStateChangedListener?.invoke(true)
        }
        nativeWidget.addEventListener("canplay") {
            if (buffering) {
                buffering = false
                onBufferingChangedListener?.invoke(false)
                onBufferingStateChangedListener?.invoke(false)
            }
            markMediaReady()
        }
        nativeWidget.addEventListener("loadeddata") {
            markMediaReady()
        }
        nativeWidget.addEventListener("seeked") {
            markMediaReady()
        }
        nativeWidget.addEventListener("timeupdate") { payload ->
            val sec = (payload as? Number)?.toDouble()
                ?: nativeWidget.getAttribute("data-current-time")?.toDoubleOrNull()
            if (sec != null && !sec.isNaN() && sec >= 0.0) {
                val pendingSeek = pendingDirectSeekSec
                if (pendingSeek != null) {
                    if (sec >= pendingSeek - 1.0) {
                        pendingDirectSeekSec = null
                        currentTimeSec = sec
                    } else {
                        currentTimeSec = pendingSeek
                    }
                } else {
                    currentTimeSec = transcodeBaseOffsetSec + sec
                }
            }
            onTimeUpdateListener?.invoke(currentTimeSec)
        }
        nativeWidget.addEventListener("durationchange") { payload ->
            if (durationOverrideSec != null && durationOverrideSec!! > 0.0) return@addEventListener
            if (isTranscodeStream && durationSec > 0.0) return@addEventListener
            val dur = (payload as? Number)?.toDouble()
                ?: nativeWidget.getAttribute("data-duration")?.toDoubleOrNull()
            if (dur != null && !dur.isNaN() && dur > 0.0) {
                durationSec = dur
                if (!WebBrowserDomBridge.isBrowserEnvironment) {
                    markMediaReady()
                }
                onDurationChangedListener?.invoke(durationSec)
            }
        }
        nativeWidget.addEventListener("loadedmetadata") {
            val pendingSeek = pendingDirectSeekSec
            if (pendingSeek != null) {
                pendingDirectSeekSec = null
                WebBrowserDomBridge.mediaSeekTo(nativeWidget, pendingSeek)
            } else if (!playing && preloadMode != MediaPreloadMode.NONE && currentTimeSec <= 0.0) {
                WebBrowserDomBridge.mediaSeekTo(nativeWidget, 0.001)
            }
            if (durationOverrideSec == null || durationOverrideSec!! <= 0.0) {
                val dur = nativeWidget.getAttribute("data-duration")?.toDoubleOrNull()
                if (dur != null && !dur.isNaN() && dur > 0.0 && (!isTranscodeStream || durationSec <= 0.0)) {
                    durationSec = dur
                    onDurationChangedListener?.invoke(durationSec)
                }
            }
            if (!WebBrowserDomBridge.isBrowserEnvironment) {
                markMediaReady()
            }
        }
        nativeWidget.addEventListener("progress") { payload ->
            val parsedRanges = parseBufferedRangesFromWidget(payload)
            if (parsedRanges.isNotEmpty()) {
                bufferedRangesList = parsedRanges
            }
            val bufEnd = bufferedRangesList.maxOfOrNull { it.second } ?: 0.0
            if (bufEnd > lastProgressBufferedEndSec) {
                val deltaVideoSec = bufEnd - lastProgressBufferedEndSec
                val attrElapsedMs = nativeWidget.getAttribute("data-progress-elapsed-ms")?.toLongOrNull()
                if (attrElapsedMs != null && attrElapsedMs > 0L) {
                    recordNetworkThroughputSample(deltaVideoSec, attrElapsedMs / 1000.0)
                    lastProgressWallTimeMs += attrElapsedMs
                } else if (lastProgressWallTimeMs > 0L) {
                    lastProgressWallTimeMs += 500L
                } else {
                    lastProgressWallTimeMs = 1L
                }
                lastProgressBufferedEndSec = bufEnd
            }

            val requiredSafeBuffer = computeRequiredSafeBufferSeconds()
            val speedRatio = smoothedNetworkRateVideoSecPerSec / speed.coerceAtLeast(0.25f)
            nativeWidget.setAttribute("data-required-safe-buffer-sec", requiredSafeBuffer.toString())
            nativeWidget.setAttribute("data-network-speed-ratio", speedRatio.toString())

            val coveringEnd = bufferedRangesList.firstOrNull { (s, e) ->
                currentTimeSec >= s - 0.5 && currentTimeSec <= e
            }?.second ?: bufEnd
            val bufferedForwardSec = (coveringEnd - currentTimeSec).coerceAtLeast(0.0)

            if (buffering && (
                    !adaptivePreloadBufferingEnabled ||
                        bufferedForwardSec >= requiredSafeBuffer ||
                        (durationSec > 0.0 && bufEnd >= durationSec - 0.5)
                    )
            ) {
                buffering = false
                onBufferingChangedListener?.invoke(false)
                onBufferingStateChangedListener?.invoke(false)
                if (playing) {
                    nativeWidget.setAttribute("data-playing", "true")
                }
            }
            onBufferedRangesChangedListener?.invoke(bufferedRangesList)
        }
        nativeWidget.addEventListener("error") { payload ->
            val detail = (payload as? String)?.takeIf { it.isNotBlank() } ?: "Video playback error for $sourceUrl"
            onErrorListener?.invoke(detail)
        }
    }

    private fun canPlayAnyMime(mimes: List<String>): Boolean =
        mimes.any { WebBrowserDomBridge.mediaCanPlayType(nativeWidget, it) }

    override fun isVideoCodecSupported(codec: VideoCodec): Boolean =
        canPlayAnyMime(codec.html5CanPlayProbes)

    override fun isAudioCodecSupported(codec: AudioCodec): Boolean =
        canPlayAnyMime(codec.html5CanPlayProbes)

    override fun getSupportedVideoCodecs(): Set<VideoCodec> =
        VideoCodec.entries.filterTo(linkedSetOf()) { isVideoCodecSupported(it) }

    override fun getSupportedAudioCodecs(): Set<AudioCodec> =
        AudioCodec.entries.filterTo(linkedSetOf()) { isAudioCodecSupported(it) }

    override fun getMediaCapabilities(): MediaCapabilitiesReport {
        val videoMap = linkedMapOf<VideoCodec, CodecSupportLevel>()
        for (codec in VideoCodec.entries) {
            if (isVideoCodecSupported(codec)) {
                videoMap[codec] = when (codec) {
                    VideoCodec.H264,
                    VideoCodec.HEVC,
                    VideoCodec.VP9,
                    VideoCodec.AV1,
                    VideoCodec.VVC -> CodecSupportLevel.HARDWARE_ACCELERATED
                    else -> CodecSupportLevel.SUPPORTED
                }
            }
        }

        val audioMap = linkedMapOf<AudioCodec, CodecSupportLevel>()
        for (codec in AudioCodec.entries) {
            if (isAudioCodecSupported(codec)) {
                audioMap[codec] = when (codec) {
                    AudioCodec.AAC,
                    AudioCodec.AC3,
                    AudioCodec.EAC3,
                    AudioCodec.AC4 -> CodecSupportLevel.HARDWARE_ACCELERATED
                    else -> CodecSupportLevel.SUPPORTED
                }
            }
        }

        val containerSet = linkedSetOf<MediaContainer>()
        if (canPlayAnyMime(listOf("video/mp4"))) {
            containerSet.add(MediaContainer.MP4)
        }
        if (canPlayAnyMime(listOf("video/quicktime"))) {
            containerSet.add(MediaContainer.MOV)
        }
        if (canPlayAnyMime(listOf("video/webm"))) {
            containerSet.add(MediaContainer.WEBM)
        }
        if (canPlayAnyMime(listOf("video/x-matroska"))) {
            containerSet.add(MediaContainer.MKV)
        }
        if (canPlayAnyMime(listOf("video/ogg", "audio/ogg"))) {
            containerSet.add(MediaContainer.OGG)
        }
        if (canPlayAnyMime(listOf("video/x-msvideo", "video/avi"))) {
            containerSet.add(MediaContainer.AVI)
        }
        if (canPlayAnyMime(listOf("video/mp2t"))) {
            containerSet.add(MediaContainer.MPEG_TS)
        }
        if (canPlayAnyMime(listOf("application/vnd.apple.mpegurl", "application/x-mpegurl"))) {
            containerSet.add(MediaContainer.HLS)
        }
        if (canPlayAnyMime(listOf("application/dash+xml"))) {
            containerSet.add(MediaContainer.DASH)
        }

        return MediaCapabilitiesReport(
            videoCodecs = videoMap,
            audioCodecs = audioMap,
            containers = containerSet,
            maxWidth = 3840,
            maxHeight = 2160,
            maxFrameRate = 60,
            supportsHdr10 = isVideoCodecSupported(VideoCodec.HEVC) || isVideoCodecSupported(VideoCodec.VP9) || isVideoCodecSupported(VideoCodec.AV1),
            supportsHlg = isVideoCodecSupported(VideoCodec.HEVC) || isVideoCodecSupported(VideoCodec.VP9) || isVideoCodecSupported(VideoCodec.AV1),
            supportsDolbyVision = canPlayAnyMime(listOf("video/mp4; codecs=\"dvh1.05.06\"", "video/mp4; codecs=\"dvhe.05.06\"")),
            supportsSurround51 = isAudioCodecSupported(AudioCodec.AC3) || isAudioCodecSupported(AudioCodec.EAC3) || isAudioCodecSupported(AudioCodec.OPUS) || isAudioCodecSupported(AudioCodec.AAC),
            supportsSurround71 = isAudioCodecSupported(AudioCodec.EAC3) || isAudioCodecSupported(AudioCodec.TRUEHD) || isAudioCodecSupported(AudioCodec.DTS_HD),
            supportsAudioTrackSelection = false,
            supportsSubtitleTrackSelection = true,
            hardwareAccelerationBackend = "HTML5 <video>.canPlayType"
        )
    }

    override fun supportsAudioTrackSelection(): Boolean = false
    override fun supportsSubtitleTrackSelection(): Boolean = true

    override fun setSource(url: String, seekOffsetSeconds: Double, durationOverrideSeconds: Double?) {
        val urlChanged = sourceUrl != url || url.isBlank()
        sourceUrl = url
        durationOverrideSec = durationOverrideSeconds?.takeIf { it > 0.0 }
        val targetSeek = seekOffsetSeconds.coerceAtLeast(0.0)
        val isTranscoded = url.contains("mode=transcode", ignoreCase = true) ||
            url.contains("?start=") ||
            url.contains("&start=")
        isTranscodeStream = isTranscoded
        if (isTranscoded) {
            transcodeBaseOffsetSec = targetSeek
            pendingDirectSeekSec = null
            currentTimeSec = targetSeek
        } else {
            transcodeBaseOffsetSec = 0.0
            pendingDirectSeekSec = if (targetSeek > 0.0) targetSeek else null
            currentTimeSec = targetSeek
        }
        if (durationOverrideSec != null) {
            durationSec = durationOverrideSec!!
            onDurationChangedListener?.invoke(durationSec)
        }
        if (urlChanged) {
            mediaReadyFlag = false
            nativeWidget.setAttribute("data-media-ready", "false")
            nativeWidget.setAttribute("src", url)
            val initialElementTime = if (isTranscoded) 0.0 else targetSeek
            WebBrowserDomBridge.mediaLoad(nativeWidget, initialElementTime)
            if (url.isNotBlank() && !WebBrowserDomBridge.isBrowserEnvironment) {
                markMediaReady()
            }
        } else if (!isTranscoded && targetSeek > 0.0) {
            WebBrowserDomBridge.mediaSeekTo(nativeWidget, targetSeek)
        }
    }

    override fun play() {
        playing = true
        nativeWidget.setAttribute("data-playing", "true")
        onPlayingStateChangedListener?.invoke(true)
        WebBrowserDomBridge.mediaPlay(nativeWidget) { errMsg ->
            if (!playing || errMsg.contains("AbortError", ignoreCase = true) || errMsg.contains("interrupted", ignoreCase = true)) {
                return@mediaPlay
            }
            playing = false
            nativeWidget.setAttribute("data-playing", "false")
            onPlayingStateChangedListener?.invoke(false)
            onErrorListener?.invoke(errMsg)
        }
    }

    override fun pause() {
        playing = false
        nativeWidget.setAttribute("data-playing", "false")
        onPlayingStateChangedListener?.invoke(false)
        WebBrowserDomBridge.mediaPause(nativeWidget)
    }

    override fun stop() {
        playing = false
        currentTimeSec = 0.0
        transcodeBaseOffsetSec = 0.0
        pendingDirectSeekSec = null
        nativeWidget.setAttribute("data-playing", "false")
        WebBrowserDomBridge.mediaPause(nativeWidget)
        WebBrowserDomBridge.mediaSeekTo(nativeWidget, 0.0)
        onPlayingStateChangedListener?.invoke(false)
        onTimeUpdateListener?.invoke(0.0)
    }

    override fun seekTo(seconds: Double) {
        val clamped = seconds.coerceAtLeast(0.0)
        currentTimeSec = clamped
        pendingDirectSeekSec = null
        val mediaTarget = (clamped - transcodeBaseOffsetSec).coerceAtLeast(0.0)
        WebBrowserDomBridge.mediaSeekTo(nativeWidget, mediaTarget)
        onTimeUpdateListener?.invoke(currentTimeSec)
    }

    override fun getCurrentTimeSeconds(): Double = currentTimeSec
    override fun getDurationSeconds(): Double = durationSec
    override fun isPlaying(): Boolean = playing

    override fun setPlaybackSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.25f, 4.0f)
        WebBrowserDomBridge.mediaSetPlaybackRate(nativeWidget, this.speed)
    }

    override fun getPlaybackSpeed(): Float = speed

    override fun setVolume(volume: Float) {
        volumeLevel = volume.coerceIn(0f, 1f)
        WebBrowserDomBridge.mediaSetVolume(nativeWidget, volumeLevel)
    }

    override fun getVolume(): Float = volumeLevel

    override fun setMuted(muted: Boolean) {
        mutedFlag = muted
        WebBrowserDomBridge.mediaSetMuted(nativeWidget, muted)
    }

    override fun isMuted(): Boolean = mutedFlag

    override fun setResizeMode(mode: VideoResizeMode) {
        resizeMode = mode
        nativeWidget.style["object-fit"] = when (mode) {
            VideoResizeMode.FIT -> "contain"
            VideoResizeMode.FILL -> "cover"
            VideoResizeMode.STRETCH -> "fill"
        }
    }

    override fun getResizeMode(): VideoResizeMode = resizeMode

    override fun getAudioTracks(): List<MediaTrackInfo> = audioTracks.toList()

    override fun selectAudioTrack(trackId: String): Boolean {
        val indexPrefix = if (trackId.startsWith("index:")) trackId.removePrefix("index:").toIntOrNull() else null
        for (i in audioTracks.indices) {
            val item = audioTracks[i]
            val selected = if (indexPrefix != null) (i == indexPrefix) else (item.id == trackId)
            audioTracks[i] = item.copy(isSelected = selected)
        }
        onTracksChangedListener?.invoke()
        return audioTracks.any { it.isSelected }
    }

    override fun getSubtitleTracks(): List<SubtitleTrackInfo> = subtitleTracks.toList()

    override fun addExternalSubtitleTrack(label: String, language: String, vttOrSrtUrl: String) {
        val id = "sub-${subtitleTracks.size}"
        subtitleTracks.add(
            SubtitleTrackInfo(
                id = id,
                label = label,
                language = language,
                externalUrl = vttOrSrtUrl,
                isSelected = activeSubtitleTrackId == id
            )
        )
        onTracksChangedListener?.invoke()
    }

    override fun selectSubtitleTrack(trackId: String?): Boolean {
        val indexPrefix = if (trackId != null && trackId.startsWith("index:")) trackId.removePrefix("index:").toIntOrNull() else null
        activeSubtitleTrackId = if (indexPrefix != null) subtitleTracks.getOrNull(indexPrefix)?.id else trackId
        for (i in subtitleTracks.indices) {
            val item = subtitleTracks[i]
            val selected = if (trackId == null) false else if (indexPrefix != null) (i == indexPrefix) else (item.id == trackId)
            subtitleTracks[i] = item.copy(isSelected = selected)
        }
        onTracksChangedListener?.invoke()
        return true
    }

    override fun supportsPictureInPicture(): Boolean = true

    override fun enterPictureInPicture(): Boolean {
        pipActive = true
        return true
    }

    override fun exitPictureInPicture() {
        pipActive = false
    }

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        attachedAnalyzer = analyzer
    }

    override fun setOnTimeUpdate(listener: ((Double) -> Unit)?) { onTimeUpdateListener = listener }
    override fun setOnDurationChanged(listener: ((Double) -> Unit)?) { onDurationChangedListener = listener }
    override fun setOnEnded(listener: (() -> Unit)?) { onEndedListener = listener }
    override fun setOnBufferingChanged(listener: ((Boolean) -> Unit)?) { onBufferingChangedListener = listener }
    override fun setOnError(listener: ((String) -> Unit)?) { onErrorListener = listener }

    override fun setOnPlayingStateChanged(listener: ((Boolean) -> Unit)?) {
        onPlayingStateChangedListener = listener
    }

    override fun setOnBufferingStateChanged(listener: ((Boolean) -> Unit)?) {
        onBufferingStateChangedListener = listener
    }

    override fun setOnBufferedRangesChanged(listener: ((List<Pair<Double, Double>>) -> Unit)?) {
        onBufferedRangesChangedListener = listener
    }

    override fun setOnTracksChanged(listener: (() -> Unit)?) {
        onTracksChangedListener = listener
    }

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

    override fun setAntialiasing(antialiasing: Boolean) {
        antialiasingEnabled = antialiasing
        applyAntialiasingStyles()
    }

    override fun isAntialiasing(): Boolean = antialiasingEnabled

    override fun setOnMediaReady(listener: (() -> Unit)?) {
        onMediaReadyListener = listener
    }

    override fun isMediaReady(): Boolean = mediaReadyFlag

    override fun getBufferedRanges(): List<Pair<Double, Double>> = bufferedRangesList

    override fun getEstimatedNetworkSpeedRatio(): Double =
        smoothedNetworkRateVideoSecPerSec / speed.coerceAtLeast(0.25f)

    override fun getRequiredSafeBufferSeconds(): Double =
        computeRequiredSafeBufferSeconds()

    fun recordNetworkThroughputSample(downloadedVideoSeconds: Double, elapsedWallSeconds: Double) {
        if (downloadedVideoSeconds <= 0.0 || elapsedWallSeconds <= 0.0) return
        val instantRate = (downloadedVideoSeconds / elapsedWallSeconds).coerceIn(0.05, 50.0)
        smoothedNetworkRateVideoSecPerSec = 0.65 * smoothedNetworkRateVideoSecPerSec + 0.35 * instantRate
        val speedRatio = smoothedNetworkRateVideoSecPerSec / speed.coerceAtLeast(0.25f)
        nativeWidget.setAttribute("data-network-speed-ratio", speedRatio.toString())
        nativeWidget.setAttribute("data-required-safe-buffer-sec", computeRequiredSafeBufferSeconds().toString())
    }

    fun computeRequiredSafeBufferSeconds(
        currentSec: Double = currentTimeSec,
        totalDurationSec: Double = durationSec,
        playbackRate: Double = speed.toDouble(),
        networkVideoRate: Double = smoothedNetworkRateVideoSecPerSec
    ): Double {
        val d = (totalDurationSec - currentSec).coerceAtLeast(0.0)
        if (d <= 0.0) return 0.0
        val rMedia = playbackRate.coerceAtLeast(0.25)
        val rEff = (0.85 * networkVideoRate).coerceAtLeast(0.05)
        val bMin = min(d, 8.0)
        val bSafety = min(d, 5.0)
        if (rEff >= rMedia) {
            return bMin
        }
        val deficitFraction = (1.0 - (rEff / rMedia)).coerceIn(0.0, 1.0)
        return (d * deficitFraction + bSafety).coerceIn(bMin, d)
    }

    private fun parseBufferedRangesFromWidget(payload: Any?): List<Pair<Double, Double>> {
        val baseOffset = if (isTranscodeStream) transcodeBaseOffsetSec else 0.0
        val rawRanges = nativeWidget.getAttribute("data-buffered-ranges")?.trim()
        if (!rawRanges.isNullOrEmpty()) {
            val parsed = rawRanges.split(',').mapNotNull { token ->
                val dashIdx = token.indexOf('-')
                if (dashIdx > 0) {
                    val start = token.substring(0, dashIdx).trim().toDoubleOrNull()
                    val end = token.substring(dashIdx + 1).trim().toDoubleOrNull()
                    if (start != null && end != null && !start.isNaN() && !end.isNaN() && end > start) {
                        (baseOffset + start) to (baseOffset + end)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            if (parsed.isNotEmpty()) {
                return parsed
            }
        }
        val bufEnd = (payload as? Number)?.toDouble()
            ?: nativeWidget.getAttribute("data-buffered-time")?.toDoubleOrNull()
        if (bufEnd != null && !bufEnd.isNaN() && bufEnd >= 0.0) {
            return listOf(baseOffset to (baseOffset + bufEnd))
        }
        return bufferedRangesList
    }
}
