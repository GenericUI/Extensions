package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.FramePresentTarget
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.MediaPreloadMode
import com.nexustools.genericui.enums.media.VideoCodec
import com.nexustools.genericui.enums.media.VideoResizeMode
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.ext.media.overlay.VideoOverlayHost
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Hardware-accelerated cross-platform video player component.
 *
 * On desktop backends (`:windows`, `:macos`, `:gtk`, `:qt`), powered by `:desktop`'s VLC video playback,
 * HDR10/HLG/Dolby Vision tone-mapping (using Vulkan shaders as primary and OpenGL ES shaders as fallback),
 * and hardware/software decoding engine (`DesktopVlcVideoPlayerImpl`).
 */
open class VideoPlayer(
    sourceUrl: String? = null
) : AbstractComponent(), Player, FramePresentTarget {
    final override var sourceUrl: String? = sourceUrl
        private set

    private var fallbackPresentedFrameCount: Long = 0L
    private val beforeFramePresentListeners = mutableListOf<(Long, Double) -> Unit>()
    private var lastNotifiedBeforePresentFrame: Long = -1L

    override val presentedFrameCount: Long
        get() = maxOf(
            fallbackPresentedFrameCount,
            typedNative<VideoPlayerNativeImpl<*>>()?.getPresentedFrameCount() ?: 0L
        )

    override fun addBeforeFramePresentListener(listener: (frameIndex: Long, timestampMs: Double) -> Unit) {
        if (listener !in beforeFramePresentListeners) {
            beforeFramePresentListeners.add(listener)
        }
    }

    override fun removeBeforeFramePresentListener(listener: (frameIndex: Long, timestampMs: Double) -> Unit) {
        beforeFramePresentListeners.remove(listener)
    }

    internal fun notifyBeforeFramePresent(
        frameIndex: Long,
        timestampMs: Double = (currentTimeSeconds * 1000.0).coerceAtLeast(0.0)
    ) {
        if (frameIndex > fallbackPresentedFrameCount) {
            fallbackPresentedFrameCount = frameIndex
        }
        if (frameIndex == lastNotifiedBeforePresentFrame) return
        lastNotifiedBeforePresentFrame = frameIndex
        if (beforeFramePresentListeners.isNotEmpty()) {
            for (listener in beforeFramePresentListeners.toList()) {
                listener(frameIndex, timestampMs)
            }
        }
    }

    final override var seekOffsetSeconds: Double = 0.0
        private set

    final override var durationOverrideSeconds: Double? = null
        private set

    private var nativeReportedDurationSeconds: Double = 0.0
    private var playingFallbackState: Boolean = false
    private val fallbackAudioTracks = mutableListOf<MediaTrackInfo>()
    private val fallbackSubtitleTracks = mutableListOf<SubtitleTrackInfo>()
    private var selectedAudioTrackIdState: String? = null
    private var selectedSubtitleTrackIdState: String? = null
    private var subtitlesExplicitlyDisabled: Boolean = false
    private var directSubtitleCues: List<SubtitleCue> = emptyList()
    private val subtitleCuesByTrackId = linkedMapOf<String, List<SubtitleCue>>()

    internal val stateChangeObservers = mutableListOf<() -> Unit>()

    override var volume: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 2f)
            typedNative<VideoPlayerNativeImpl<*>>()?.setVolume(field)
            notifyStateChangeObservers()
        }

    override var isMuted: Boolean = false
        set(value) {
            field = value
            typedNative<VideoPlayerNativeImpl<*>>()?.setMuted(value)
            notifyStateChangeObservers()
        }

    override var playbackSpeed: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.1f, 8.0f)
            typedNative<VideoPlayerNativeImpl<*>>()?.setPlaybackSpeed(field)
            notifyStateChangeObservers()
        }

    var resizeMode: VideoResizeMode = VideoResizeMode.FIT
        set(value) {
            field = value
            typedNative<VideoPlayerNativeImpl<*>>()?.setResizeMode(value)
            notifyStateChangeObservers()
        }

    override var preloadMode: MediaPreloadMode = MediaPreloadMode.AUTO
        set(value) {
            field = value
            typedNative<VideoPlayerNativeImpl<*>>()?.setPreloadMode(value)
            notifyStateChangeObservers()
        }

    override var adaptivePreloadBufferingEnabled: Boolean = true
        set(value) {
            field = value
            typedNative<VideoPlayerNativeImpl<*>>()?.setAdaptivePreloadBufferingEnabled(value)
            notifyStateChangeObservers()
        }

    /**
     * Enables smooth bilinear/bicubic antialiasing and high-quality video frame scaling.
     * Enabled (`true`) by default across all backends.
     */
    var antialiasing: Boolean = true
        set(value) {
            field = value
            setAttribute("data-antialiasing", value.toString())
            typedNative<VideoPlayerNativeImpl<*>>()?.setAntialiasing(value)
            notifyStateChangeObservers()
        }

    private var mediaReadyFallbackState: Boolean = false
    private val mediaReadyWaiters = mutableListOf<() -> Unit>()

    override val isMediaReady: Boolean
        get() = typedNative<VideoPlayerNativeImpl<*>>()?.isMediaReady() == true || mediaReadyFallbackState

    override var onMediaReady: (() -> Unit)? = null

    init {
        setAttribute("data-antialiasing", antialiasing.toString())
    }

    internal fun notifyMediaReady() {
        val wasReady = mediaReadyFallbackState
        mediaReadyFallbackState = true
        val waiters = mediaReadyWaiters.toList()
        mediaReadyWaiters.clear()
        for (waiter in waiters) {
            waiter.invoke()
        }
        if (!wasReady) {
            onMediaReady?.invoke()
            notifyStateChangeObservers()
        }
    }

    /**
     * Suspends until the first frame / media metadata for the current [sourceUrl] is ready to play,
     * or until [timeoutMs] elapses. Returns `true` if media is ready.
     */
    override suspend fun awaitMediaReady(timeoutMs: Long): Boolean {
        if (isMediaReady) return true
        val result = withTimeoutOrNull(timeoutMs.coerceAtLeast(1L)) {
            if (isMediaReady) return@withTimeoutOrNull true
            suspendCancellableCoroutine { cont ->
                if (isMediaReady) {
                    if (cont.isActive) cont.resume(true)
                    return@suspendCancellableCoroutine
                }
                lateinit var waiter: () -> Unit
                waiter = {
                    mediaReadyWaiters.remove(waiter)
                    if (cont.isActive) {
                        cont.resume(true)
                    }
                }
                mediaReadyWaiters.add(waiter)
                cont.invokeOnCancellation {
                    mediaReadyWaiters.remove(waiter)
                }
            }
        }
        return result ?: isMediaReady
    }

    final override var isBuffering: Boolean = false
        private set

    final override var bufferedRanges: List<Pair<Double, Double>> = emptyList()
        get() {
            if (field.isNotEmpty()) return field
            return typedNative<VideoPlayerNativeImpl<*>>()?.getBufferedRanges().orEmpty()
        }
        private set

    override val bufferedEndSeconds: Double
        get() = bufferedRanges.maxOfOrNull { it.second } ?: 0.0

    override val estimatedNetworkSpeedRatio: Double
        get() = typedNative<VideoPlayerNativeImpl<*>>()?.getEstimatedNetworkSpeedRatio() ?: 1.0

    override fun getRequiredSafeBufferSeconds(): Double =
        typedNative<VideoPlayerNativeImpl<*>>()?.getRequiredSafeBufferSeconds() ?: 0.0

    var isInPictureInPictureMode: Boolean = false
        private set

    override val currentTimeSeconds: Double
        get() = typedNative<VideoPlayerNativeImpl<*>>()?.getCurrentTimeSeconds() ?: seekOffsetSeconds

    override val durationSeconds: Double
        get() = durationOverrideSeconds
            ?: typedNative<VideoPlayerNativeImpl<*>>()?.getDurationSeconds()?.takeIf { it > 0.0 }
            ?: nativeReportedDurationSeconds

    override val isPlaying: Boolean
        get() = typedNative<VideoPlayerNativeImpl<*>>()?.isPlaying() ?: playingFallbackState

    override var onTimeUpdate: ((Double) -> Unit)? = null
    override var onDurationChanged: ((Double) -> Unit)? = null
    override var onEnded: (() -> Unit)? = null
    override var onBufferingChanged: ((Boolean) -> Unit)? = null
    override var onError: ((String) -> Unit)? = null

    override var onPlayingStateChanged: ((Boolean) -> Unit)? = null
    var onBufferingStateChanged: ((Boolean) -> Unit)? = null
    override var onBufferedRangesChanged: ((List<Pair<Double, Double>>) -> Unit)? = null
    var onTracksChanged: (() -> Unit)? = null
    var onPictureInPictureChanged: ((Boolean) -> Unit)? = null

    private var attachedAnalyzer: AudioAnalyzer? = null

    internal var attachedOverlayHost: VideoOverlayHost? = null

    /**
     * Wraps this [VideoPlayer] in a [VideoOverlayHost] (or reuses the existing host) and
     * attaches [overlay] to it.
     */
    fun attachVideoOverlay(overlay: AbstractOverlay): VideoOverlayHost {
        val existing = attachedOverlayHost
        if (existing != null) {
            existing.attachOverlay(overlay)
            return existing
        }
        return VideoOverlayHost(videoPlayer = this, initialOverlay = overlay)
    }

    /**
     * Wraps this [VideoPlayer] in a [VideoOverlayHost] (or reuses the existing host),
     * optionally attaching [overlay] if provided.
     */
    fun withOverlay(overlay: AbstractOverlay? = null): VideoOverlayHost {
        val existing = attachedOverlayHost
        if (existing != null) {
            if (overlay != null) {
                existing.attachOverlay(overlay)
            }
            return existing
        }
        return VideoOverlayHost(videoPlayer = this, initialOverlay = overlay)
    }

    internal fun addStateChangeObserver(observer: () -> Unit) {
        if (observer !in stateChangeObservers) {
            stateChangeObservers.add(observer)
        }
    }

    internal fun removeStateChangeObserver(observer: () -> Unit) {
        stateChangeObservers.remove(observer)
    }

    internal fun notifyStateChangeObservers() {
        for (observer in stateChangeObservers.toList()) {
            observer.invoke()
        }
    }

    /**
     * Queries the uniform codec, container, 4K, HDR10/HLG/Dolby Vision, and surround audio capabilities
     * of the active platform video engine.
     */
    fun getMediaCapabilities(): MediaCapabilitiesReport {
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
            ?: MediaExtension.createVideoPlayerNative()
        return impl?.getMediaCapabilities() ?: MediaCapabilitiesReport(
            videoCodecs = emptyMap(),
            audioCodecs = emptyMap(),
            containers = emptySet()
        )
    }

    /**
     * Checks whether [codec] is supported by the active video player backend.
     */
    fun isVideoCodecSupported(codec: VideoCodec): Boolean {
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
            ?: MediaExtension.createVideoPlayerNative()
        return impl?.isVideoCodecSupported(codec) ?: false
    }

    /**
     * Checks whether [codec] is supported by the active video player backend.
     */
    override fun isAudioCodecSupported(codec: AudioCodec): Boolean {
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
            ?: MediaExtension.createVideoPlayerNative()
        return impl?.isAudioCodecSupported(codec) ?: false
    }

    /**
     * Returns all [VideoCodec] entries supported by the active video player backend.
     */
    fun getSupportedVideoCodecs(): Set<VideoCodec> {
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
            ?: MediaExtension.createVideoPlayerNative()
        return impl?.getSupportedVideoCodecs() ?: emptySet()
    }

    /**
     * Returns all [AudioCodec] entries supported by the active video player backend.
     */
    override fun getSupportedAudioCodecs(): Set<AudioCodec> {
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
            ?: MediaExtension.createVideoPlayerNative()
        return impl?.getSupportedAudioCodecs() ?: emptySet()
    }

    override fun setSource(url: String, seekOffsetSeconds: Double, durationOverrideSeconds: Double?) {
        this.sourceUrl = url
        this.seekOffsetSeconds = seekOffsetSeconds.coerceAtLeast(0.0)
        this.durationOverrideSeconds = durationOverrideSeconds
        this.mediaReadyFallbackState = false
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
        if (impl != null) {
            impl.setSource(url, this.seekOffsetSeconds, durationOverrideSeconds)
            if (impl.isMediaReady()) {
                notifyMediaReady()
            }
        } else {
            if (durationOverrideSeconds != null) {
                nativeReportedDurationSeconds = durationOverrideSeconds
                onDurationChanged?.invoke(durationOverrideSeconds)
            }
            if (url.isNotBlank()) {
                notifyMediaReady()
            }
        }
        notifyStateChangeObservers()
    }

    override fun play() {
        playingFallbackState = true
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
        if (impl != null) {
            impl.play()
        } else {
            onPlayingStateChanged?.invoke(true)
        }
        notifyStateChangeObservers()
    }

    override fun pause() {
        playingFallbackState = false
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
        if (impl != null) {
            impl.pause()
        } else {
            onPlayingStateChanged?.invoke(false)
        }
        notifyStateChangeObservers()
    }

    override fun togglePlayPause() {
        if (isPlaying) {
            pause()
        } else {
            play()
        }
    }

    override fun stop() {
        playingFallbackState = false
        seekOffsetSeconds = 0.0
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
        if (impl != null) {
            impl.stop()
        } else {
            onPlayingStateChanged?.invoke(false)
            onTimeUpdate?.invoke(0.0)
        }
        notifyStateChangeObservers()
    }

    override fun seekTo(seconds: Double) {
        val dur = durationSeconds
        val clamped = if (dur > 0.0) seconds.coerceIn(0.0, dur) else seconds.coerceAtLeast(0.0)
        seekOffsetSeconds = clamped
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
        if (impl != null) {
            impl.seekTo(clamped)
        } else {
            onTimeUpdate?.invoke(clamped)
        }
        notifyStateChangeObservers()
    }

    override fun skipDelta(deltaSeconds: Double) {
        seekTo(currentTimeSeconds + deltaSeconds)
    }

    override fun setBufferingState(buffering: Boolean) {
        isBuffering = buffering
        onBufferingChanged?.invoke(buffering)
        onBufferingStateChanged?.invoke(buffering)
        notifyStateChangeObservers()
    }

    override fun updateBufferedRanges(ranges: List<Pair<Double, Double>>) {
        bufferedRanges = ranges.toList()
        onBufferedRangesChanged?.invoke(bufferedRanges)
        notifyStateChangeObservers()
    }

    override fun isFullscreen(): Boolean {
        val host = attachedOverlayHost
        if (host != null && host !== this) {
            return host.isFullscreen() || super.isFullscreen()
        }
        return super.isFullscreen()
    }

    override fun requestFullscreen(cutoutMode: com.nexustools.genericui.enums.window.DisplayCutoutMode) {
        val host = attachedOverlayHost
        if (host != null && host !== this) {
            host.requestFullscreen(cutoutMode)
            updateFullscreenElementState(true, cutoutMode)
        } else {
            super.requestFullscreen(cutoutMode)
        }
        notifyStateChangeObservers()
    }

    override fun exitFullscreen() {
        val host = attachedOverlayHost
        if (host != null && host !== this) {
            host.exitFullscreen()
            updateFullscreenElementState(false)
        } else {
            super.exitFullscreen()
        }
        notifyStateChangeObservers()
    }

    override fun toggleFullscreen(cutoutMode: com.nexustools.genericui.enums.window.DisplayCutoutMode): Boolean {
        if (isFullscreen()) {
            exitFullscreen()
        } else {
            requestFullscreen(cutoutMode)
        }
        return isFullscreen()
    }

    override fun setFullscreen(fullscreen: Boolean, cutoutMode: com.nexustools.genericui.enums.window.DisplayCutoutMode) {
        if (fullscreen) {
            requestFullscreen(cutoutMode)
        } else {
            exitFullscreen()
        }
    }

    fun supportsAudioTrackSelection(): Boolean {
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
            ?: MediaExtension.createVideoPlayerNative()
        return impl?.supportsAudioTrackSelection() ?: false
    }

    fun supportsSubtitleTrackSelection(): Boolean {
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
            ?: MediaExtension.createVideoPlayerNative()
        return impl?.supportsSubtitleTrackSelection() ?: false
    }

    fun setAudioTracks(tracks: List<MediaTrackInfo>) {
        fallbackAudioTracks.clear()
        fallbackAudioTracks.addAll(tracks)
        selectedAudioTrackIdState = tracks.firstOrNull { it.isSelected }?.id
        onTracksChanged?.invoke()
        notifyStateChangeObservers()
    }

    fun getAudioTracks(): List<MediaTrackInfo> {
        val nativeTracks = typedNative<VideoPlayerNativeImpl<*>>()?.getAudioTracks().orEmpty()
        return if (nativeTracks.isNotEmpty()) nativeTracks else fallbackAudioTracks.toList()
    }

    fun getSelectedAudioTrackId(): String? =
        typedNative<VideoPlayerNativeImpl<*>>()?.getSelectedAudioTrackId()
            ?: selectedAudioTrackIdState
            ?: getAudioTracks().firstOrNull { it.isSelected }?.id

    fun selectAudioTrack(trackId: String): Boolean {
        selectedAudioTrackIdState = trackId
        val requestedIndex = if (trackId.startsWith("index:")) {
            trackId.removePrefix("index:").toIntOrNull()
        } else {
            null
        }
        var matchedFallback = false
        for (i in fallbackAudioTracks.indices) {
            val item = fallbackAudioTracks[i]
            val match = item.id == trackId || (requestedIndex != null && i == requestedIndex)
            if (match) matchedFallback = true
            fallbackAudioTracks[i] = item.copy(isSelected = match)
        }
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
        val result = if (impl != null) {
            impl.selectAudioTrack(trackId)
        } else {
            onTracksChanged?.invoke()
            matchedFallback || fallbackAudioTracks.isEmpty() || requestedIndex != null
        }
        notifyStateChangeObservers()
        return result
    }

    /**
     * Selects an audio track by its 0-based index among [getAudioTracks] by passing `"index:$index"`
     * directly to the underlying native player so selection succeeds both before and after demuxing.
     */
    fun selectAudioTrackByIndex(index: Int): Boolean {
        if (index < 0) return false
        val tracks = getAudioTracks()
        val targetId = tracks.getOrNull(index)?.id ?: "index:$index"
        return selectAudioTrack(targetId)
    }

    private var internalSubtitlesEnabledState: Boolean = true

    var internalSubtitlesEnabled: Boolean
        get() = typedNative<VideoPlayerNativeImpl<*>>()?.isInternalSubtitlesEnabled() ?: internalSubtitlesEnabledState
        set(value) {
            internalSubtitlesEnabledState = value
            if (!value) {
                subtitlesExplicitlyDisabled = true
            }
            typedNative<VideoPlayerNativeImpl<*>>()?.setInternalSubtitlesEnabled(value)
            notifyStateChangeObservers()
        }

    fun getSubtitleTracks(): List<SubtitleTrackInfo> {
        val nativeTracks = typedNative<VideoPlayerNativeImpl<*>>()?.getSubtitleTracks().orEmpty()
        return if (nativeTracks.isNotEmpty()) nativeTracks else fallbackSubtitleTracks.toList()
    }

    fun getSelectedSubtitleTrackId(): String? =
        if (subtitlesExplicitlyDisabled) null
        else typedNative<VideoPlayerNativeImpl<*>>()?.getSelectedSubtitleTrackId()
            ?: selectedSubtitleTrackIdState
            ?: getSubtitleTracks().firstOrNull { it.isSelected }?.id

    fun addExternalSubtitleTrack(label: String, language: String, vttOrSrtUrl: String) {
        val trackId = "sub-${fallbackSubtitleTracks.size}"
        fallbackSubtitleTracks.add(
            SubtitleTrackInfo(
                id = trackId,
                label = label,
                language = language,
                externalUrl = vttOrSrtUrl,
                isSelected = selectedSubtitleTrackIdState == trackId
            )
        )
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
        if (impl != null) {
            impl.addExternalSubtitleTrack(label, language, vttOrSrtUrl)
        } else {
            onTracksChanged?.invoke()
        }
        notifyStateChangeObservers()
    }

    fun selectSubtitleTrack(trackId: String?): Boolean {
        selectedSubtitleTrackIdState = trackId
        subtitlesExplicitlyDisabled = (trackId == null)
        var matchedFallback = (trackId == null)
        for (i in fallbackSubtitleTracks.indices) {
            val item = fallbackSubtitleTracks[i]
            val match = trackId != null && item.id == trackId
            if (match) matchedFallback = true
            fallbackSubtitleTracks[i] = item.copy(isSelected = match)
        }
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
        val result = if (impl != null) {
            impl.selectSubtitleTrack(trackId)
        } else {
            onTracksChanged?.invoke()
            matchedFallback || fallbackSubtitleTracks.isEmpty()
        }
        notifyStateChangeObservers()
        return result
    }

    fun selectSubtitleTrackByIndex(index: Int?): Boolean {
        if (index == null || index < 0) return selectSubtitleTrack(null)
        val tracks = getSubtitleTracks()
        val targetId = tracks.getOrNull(index)?.id ?: "index:$index"
        return selectSubtitleTrack(targetId)
    }

    val subtitleCues: List<SubtitleCue>
        get() = resolveActiveSubtitleCues()

    /**
     * Parses WebVTT (`.vtt`) or SubRip (`.srt`) content using [WebVttSrtParser] and attaches
     * the parsed [SubtitleCue]s to this player (optionally associated with [trackId]).
     */
    fun loadSubtitleContent(vttOrSrtContent: String, trackId: String? = null): List<SubtitleCue> {
        val parsed = WebVttSrtParser.parse(vttOrSrtContent)
        attachSubtitleCues(parsed, trackId)
        return parsed
    }

    /**
     * Attaches pre-parsed [SubtitleCue]s to this player (optionally associated with [trackId]).
     */
    fun attachSubtitleCues(cues: List<SubtitleCue>, trackId: String? = null) {
        if (trackId != null) {
            subtitleCuesByTrackId[trackId] = cues.toList()
        } else {
            directSubtitleCues = cues.toList()
            subtitlesExplicitlyDisabled = false
        }
        notifyStateChangeObservers()
    }

    /**
     * Returns the active [SubtitleCue] at [atTimeSeconds], or `null` if none is active.
     */
    fun getActiveSubtitleCue(atTimeSeconds: Double = currentTimeSeconds): SubtitleCue? =
        resolveActiveSubtitleCues().firstOrNull { it.isActiveAt(atTimeSeconds) }

    /**
     * Returns the active subtitle cue text at [atTimeSeconds], or `""` if none is active.
     */
    fun getActiveSubtitleCueText(atTimeSeconds: Double = currentTimeSeconds): String =
        getActiveSubtitleCue(atTimeSeconds)?.text ?: ""

    private fun resolveActiveSubtitleCues(): List<SubtitleCue> {
        if (subtitlesExplicitlyDisabled) return emptyList()
        val activeTrackId = selectedSubtitleTrackIdState
            ?: getSubtitleTracks().firstOrNull { it.isSelected }?.id
        if (activeTrackId != null) {
            subtitleCuesByTrackId[activeTrackId]?.let { return it }
        }
        return directSubtitleCues
    }

    fun supportsPictureInPicture(): Boolean =
        typedNative<VideoPlayerNativeImpl<*>>()?.supportsPictureInPicture() ?: false

    fun enterPictureInPicture(): Boolean {
        val entered = typedNative<VideoPlayerNativeImpl<*>>()?.enterPictureInPicture() ?: true
        if (entered && !isInPictureInPictureMode) {
            isInPictureInPictureMode = true
            onPictureInPictureChanged?.invoke(true)
            notifyStateChangeObservers()
        }
        return entered
    }

    fun exitPictureInPicture() {
        typedNative<VideoPlayerNativeImpl<*>>()?.exitPictureInPicture()
        if (isInPictureInPictureMode) {
            isInPictureInPictureMode = false
            onPictureInPictureChanged?.invoke(false)
            notifyStateChangeObservers()
        }
    }

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        attachedAnalyzer = analyzer
        typedNative<VideoPlayerNativeImpl<*>>()?.attachAudioAnalyzer(analyzer)
    }

    protected override fun createNativeImpl(factory: NativeFactory): VideoPlayerNativeImpl<*> =
        MediaExtension.createVideoPlayerNativeOrFallback()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<VideoPlayerNativeImpl<*>>() ?: return
        impl.setPreloadMode(preloadMode)
        impl.setAdaptivePreloadBufferingEnabled(adaptivePreloadBufferingEnabled)
        impl.setAntialiasing(antialiasing)
        impl.setInternalSubtitlesEnabled(internalSubtitlesEnabledState)
        impl.setVolume(volume)
        impl.setMuted(isMuted)
        impl.setPlaybackSpeed(playbackSpeed)
        impl.setResizeMode(resizeMode)
        impl.setOnMediaReady {
            notifyMediaReady()
        }
        impl.setOnBeforeFramePresent { frameIdx, tsMs ->
            notifyBeforeFramePresent(frameIdx, tsMs)
        }
        selectedAudioTrackIdState?.let { impl.selectAudioTrack(it) }
        if (subtitlesExplicitlyDisabled) {
            impl.selectSubtitleTrack(null)
        } else {
            selectedSubtitleTrackIdState?.let { impl.selectSubtitleTrack(it) }
        }
        impl.setOnTimeUpdate { timeSec ->
            seekOffsetSeconds = timeSec
            onTimeUpdate?.invoke(timeSec)
            notifyStateChangeObservers()
        }
        impl.setOnDurationChanged { durSec ->
            nativeReportedDurationSeconds = durSec
            if (durSec > 0.0 && !mediaReadyFallbackState) {
                notifyMediaReady()
            }
            onDurationChanged?.invoke(durSec)
            notifyStateChangeObservers()
        }
        impl.setOnEnded {
            playingFallbackState = false
            onPlayingStateChanged?.invoke(false)
            onEnded?.invoke()
            notifyStateChangeObservers()
        }
        impl.setOnBufferingChanged { buffering ->
            isBuffering = buffering
            onBufferingChanged?.invoke(buffering)
            onBufferingStateChanged?.invoke(buffering)
            notifyStateChangeObservers()
        }
        impl.setOnBufferingStateChanged { buffering ->
            isBuffering = buffering
            onBufferingChanged?.invoke(buffering)
            onBufferingStateChanged?.invoke(buffering)
            notifyStateChangeObservers()
        }
        impl.setOnPlayingStateChanged { playing ->
            playingFallbackState = playing
            onPlayingStateChanged?.invoke(playing)
            notifyStateChangeObservers()
        }
        impl.setOnBufferedRangesChanged { ranges ->
            bufferedRanges = ranges.toList()
            onBufferedRangesChanged?.invoke(bufferedRanges)
            notifyStateChangeObservers()
        }
        impl.setOnTracksChanged {
            onTracksChanged?.invoke()
            notifyStateChangeObservers()
        }
        impl.setOnError { err ->
            playingFallbackState = false
            onError?.invoke(err)
            notifyStateChangeObservers()
        }
        attachedAnalyzer?.let { impl.attachAudioAnalyzer(it) }
        sourceUrl?.let { impl.setSource(it, seekOffsetSeconds, durationOverrideSeconds) }
    }
}
