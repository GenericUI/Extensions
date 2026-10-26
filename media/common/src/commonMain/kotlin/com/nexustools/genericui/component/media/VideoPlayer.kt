package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.media.VideoResizeMode
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl

/**
 * Hardware-accelerated cross-platform video player component.
 *
 * On desktop backends (`:windows`, `:macos`, `:gtk`, `:qt`), powered by `:desktop`'s VLC video playback,
 * HDR10/HLG/Dolby Vision tone-mapping (using Vulkan shaders as primary and OpenGL ES shaders as fallback),
 * and hardware/software decoding engine (`DesktopVlcVideoPlayerImpl`).
 */
open class VideoPlayer(
    sourceUrl: String? = null
) : AbstractComponent() {
    var sourceUrl: String? = sourceUrl
        private set

    var seekOffsetSeconds: Double = 0.0
        private set

    var durationOverrideSeconds: Double? = null
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

    var volume: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 2f)
            typedNative<VideoPlayerNativeImpl<*>>()?.setVolume(field)
            notifyStateChangeObservers()
        }

    var isMuted: Boolean = false
        set(value) {
            field = value
            typedNative<VideoPlayerNativeImpl<*>>()?.setMuted(value)
            notifyStateChangeObservers()
        }

    var playbackSpeed: Float = 1.0f
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

    var fullscreenTargetElementId: String? = null
        set(value) {
            field = value
            typedNative<VideoPlayerNativeImpl<*>>()?.setFullscreenTargetElementId(value)
        }

    @kotlin.jvm.JvmName("assignFullscreenTargetElementId")
    fun setFullscreenTargetElementId(elementId: String?) {
        fullscreenTargetElementId = elementId
    }

    var isBuffering: Boolean = false
        private set

    var bufferedRanges: List<Pair<Double, Double>> = emptyList()
        private set

    var isFullscreen: Boolean = false
        private set

    var isInPictureInPictureMode: Boolean = false
        private set

    val currentTimeSeconds: Double
        get() = typedNative<VideoPlayerNativeImpl<*>>()?.getCurrentTimeSeconds() ?: seekOffsetSeconds

    val durationSeconds: Double
        get() = durationOverrideSeconds
            ?: typedNative<VideoPlayerNativeImpl<*>>()?.getDurationSeconds()?.takeIf { it > 0.0 }
            ?: nativeReportedDurationSeconds

    val isPlaying: Boolean
        get() = typedNative<VideoPlayerNativeImpl<*>>()?.isPlaying() ?: playingFallbackState

    var onTimeUpdate: ((Double) -> Unit)? = null
    var onDurationChanged: ((Double) -> Unit)? = null
    var onEnded: (() -> Unit)? = null
    var onBufferingChanged: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    var onPlayingStateChanged: ((Boolean) -> Unit)? = null
    var onBufferingStateChanged: ((Boolean) -> Unit)? = null
    var onBufferedRangesChanged: ((List<Pair<Double, Double>>) -> Unit)? = null
    var onTracksChanged: (() -> Unit)? = null
    var onFullscreenChanged: ((Boolean) -> Unit)? = null
    var onPictureInPictureChanged: ((Boolean) -> Unit)? = null

    private var attachedAnalyzer: AudioAnalyzer? = null

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

    fun setSource(url: String, seekOffsetSeconds: Double = 0.0, durationOverrideSeconds: Double? = null) {
        this.sourceUrl = url
        this.seekOffsetSeconds = seekOffsetSeconds.coerceAtLeast(0.0)
        this.durationOverrideSeconds = durationOverrideSeconds
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
        if (impl != null) {
            impl.setSource(url, this.seekOffsetSeconds, durationOverrideSeconds)
        } else if (durationOverrideSeconds != null) {
            nativeReportedDurationSeconds = durationOverrideSeconds
            onDurationChanged?.invoke(durationOverrideSeconds)
        }
        notifyStateChangeObservers()
    }

    fun play() {
        playingFallbackState = true
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
        if (impl != null) {
            impl.play()
        } else {
            onPlayingStateChanged?.invoke(true)
        }
        notifyStateChangeObservers()
    }

    fun pause() {
        playingFallbackState = false
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
        if (impl != null) {
            impl.pause()
        } else {
            onPlayingStateChanged?.invoke(false)
        }
        notifyStateChangeObservers()
    }

    fun togglePlayPause() {
        if (isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun stop() {
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

    fun seekTo(seconds: Double) {
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

    fun skipDelta(deltaSeconds: Double) {
        seekTo(currentTimeSeconds + deltaSeconds)
    }

    fun setBufferingState(buffering: Boolean) {
        isBuffering = buffering
        onBufferingChanged?.invoke(buffering)
        onBufferingStateChanged?.invoke(buffering)
        notifyStateChangeObservers()
    }

    fun updateBufferedRanges(ranges: List<Pair<Double, Double>>) {
        bufferedRanges = ranges.toList()
        onBufferedRangesChanged?.invoke(bufferedRanges)
        notifyStateChangeObservers()
    }

    fun setFullscreen(fullscreen: Boolean) {
        if (isFullscreen != fullscreen) {
            isFullscreen = fullscreen
            onFullscreenChanged?.invoke(fullscreen)
            notifyStateChangeObservers()
        }
    }

    fun toggleFullscreen(): Boolean {
        setFullscreen(!isFullscreen)
        return isFullscreen
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

    fun selectAudioTrack(trackId: String) {
        selectedAudioTrackIdState = trackId
        for (i in fallbackAudioTracks.indices) {
            val item = fallbackAudioTracks[i]
            fallbackAudioTracks[i] = item.copy(isSelected = item.id == trackId)
        }
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
        if (impl != null) {
            impl.selectAudioTrack(trackId)
        } else {
            onTracksChanged?.invoke()
        }
        notifyStateChangeObservers()
    }

    fun getSubtitleTracks(): List<SubtitleTrackInfo> {
        val nativeTracks = typedNative<VideoPlayerNativeImpl<*>>()?.getSubtitleTracks().orEmpty()
        return if (nativeTracks.isNotEmpty()) nativeTracks else fallbackSubtitleTracks.toList()
    }

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

    fun selectSubtitleTrack(trackId: String?) {
        selectedSubtitleTrackIdState = trackId
        subtitlesExplicitlyDisabled = (trackId == null)
        for (i in fallbackSubtitleTracks.indices) {
            val item = fallbackSubtitleTracks[i]
            fallbackSubtitleTracks[i] = item.copy(isSelected = trackId != null && item.id == trackId)
        }
        val impl = typedNative<VideoPlayerNativeImpl<*>>()
        if (impl != null) {
            impl.selectSubtitleTrack(trackId)
        } else {
            onTracksChanged?.invoke()
        }
        notifyStateChangeObservers()
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

    fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        attachedAnalyzer = analyzer
        typedNative<VideoPlayerNativeImpl<*>>()?.attachAudioAnalyzer(analyzer)
    }

    override fun createNativeImpl(factory: NativeFactory): VideoPlayerNativeImpl<*> =
        MediaExtension.createVideoPlayerNativeOrFallback()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<VideoPlayerNativeImpl<*>>() ?: return
        impl.setVolume(volume)
        impl.setMuted(isMuted)
        impl.setPlaybackSpeed(playbackSpeed)
        impl.setResizeMode(resizeMode)
        impl.setFullscreenTargetElementId(fullscreenTargetElementId)
        impl.setOnTimeUpdate { timeSec ->
            seekOffsetSeconds = timeSec
            onTimeUpdate?.invoke(timeSec)
            notifyStateChangeObservers()
        }
        impl.setOnDurationChanged { durSec ->
            nativeReportedDurationSeconds = durSec
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
