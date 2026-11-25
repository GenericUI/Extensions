package com.nexustools.genericui.component.media

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.media.MediaPreloadMode
import com.nexustools.genericui.enums.media.VideoResizeMode
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.ext.media.overlay.VideoOverlayHost
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.ext.overlay.OverlayHost
import com.nexustools.genericui.ext.overlay.OverlayStageProvider
import com.nexustools.genericui.ext.overlay.OverlayWindowTitleBar
import com.nexustools.genericui.ext.overlay.OverlayWindowTitleBarConfig
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.math.roundToLong

/**
 * Fragment hosting a full-bleed [VideoPlayer] wrapped in a sharp rectangular [VideoOverlayHost]
 * while implementing the full [Player] contract directly.
 *
 * Sets [prefersUndecoratedWindow] to `true` and enables the overlay-embedded [OverlayWindowTitleBar]
 * by default so windowed desktop/macOS shells render inside a single edge-to-edge rectangular
 * window with auto-hiding titlebar chrome in windowed mode and automatic hiding in fullscreen.
 */
open class VideoFragment(
    sourceUrl: String? = null,
    initialOverlay: AbstractOverlay? = null
) : Fragment(), Player, OverlayStageProvider {

    override val prefersUndecoratedWindow: Boolean = true

    val videoPlayer: VideoPlayer = VideoPlayer(sourceUrl = sourceUrl).apply {
        addClass("genericui-video-player")
        setAttribute("data-pip-compatible", "true")
    }

    val overlayHost: VideoOverlayHost = VideoOverlayHost(
        videoPlayer = videoPlayer,
        initialOverlay = initialOverlay
    ).apply {
        addClass("genericui-video-overlay-host")
        setAttribute("borderRadiusPx", "0")
        setAttribute("data-pip-compatible", "true")
        windowTitleBarEnabled = true
        windowDragEnabled = true
        fullscreenStateOverride = { isFullscreen() }
        onToggleFullscreenOverride = { toggleFullscreen() }
    }

    private var disposedFlag: Boolean = false

    override val isDisposed: Boolean
        get() = disposedFlag || super<Fragment>.isDisposed

    private var hookedFullscreenTarget: AbstractComponent? = null
    private var previousFullscreenListener: ((Boolean) -> Unit)? = null
    private val fullscreenListenerForwarder: (Boolean) -> Unit = { fullscreen ->
        previousFullscreenListener?.invoke(fullscreen)
        val fs = isFullscreen()
        overlayHost.updateState { it.copy(isFullscreen = fs) }
        overlayHost.syncWindowTitleBar()
        videoPlayer.notifyStateChangeObservers()
    }

    private var hookedOverlayHostFullscreen: Boolean = false
    private var previousOverlayHostFullscreenListener: ((Boolean) -> Unit)? = null
    private val overlayHostFullscreenForwarder: (Boolean) -> Unit = { fullscreen ->
        previousOverlayHostFullscreenListener?.invoke(fullscreen)
        val fs = isFullscreen()
        overlayHost.updateState { it.copy(isFullscreen = fs) }
        overlayHost.syncWindowTitleBar()
        videoPlayer.notifyStateChangeObservers()
    }

    private var hookedPlayerFullscreen: Boolean = false
    private var previousPlayerFullscreenListener: ((Boolean) -> Unit)? = null
    private val playerFullscreenForwarder: (Boolean) -> Unit = { fullscreen ->
        previousPlayerFullscreenListener?.invoke(fullscreen)
        val fs = isFullscreen()
        overlayHost.updateState { it.copy(isFullscreen = fs) }
        overlayHost.syncWindowTitleBar()
    }

    init {
        MediaExtension.ensureStyleSheetRegistered()
        htmlTag = "genericui:video-fragment"
        addClass("genericui-video-fragment")
        setAttribute("borderRadiusPx", "0")
        addChild(overlayHost)
        ensureFullscreenActionWired()
        installHooks()
        overlayHost.syncWindowTitleBar()
    }

    // -------------------------------------------------------------------------
    // Overlay & Windowed Titlebar API
    // -------------------------------------------------------------------------

    var overlay: AbstractOverlay?
        get() = overlayHost.overlay
        set(value) {
            overlayHost.overlay = value
            ensureFullscreenActionWired()
            installHooks()
            overlayHost.syncWindowTitleBar()
        }

    var windowTitleBarEnabled: Boolean
        get() = overlayHost.windowTitleBarEnabled
        set(value) {
            overlayHost.windowTitleBarEnabled = value
        }

    var windowTitleBarConfig: OverlayWindowTitleBarConfig
        get() = overlayHost.windowTitleBarConfig
        set(value) {
            overlayHost.windowTitleBarConfig = value
        }

    var windowDragEnabled: Boolean
        get() = overlayHost.windowDragEnabled
        set(value) {
            overlayHost.windowDragEnabled = value
        }

    var onWindowDragMove: (() -> Unit)?
        get() = overlayHost.onWindowDragMove
        set(value) {
            overlayHost.onWindowDragMove = value
        }

    val windowTitleBar: OverlayWindowTitleBar
        get() = overlayHost.windowTitleBar

    fun attachVideoOverlay(overlay: AbstractOverlay): VideoOverlayHost {
        overlayHost.attachOverlay(overlay)
        ensureFullscreenActionWired()
        installHooks()
        overlayHost.syncWindowTitleBar()
        return overlayHost
    }

    fun withOverlay(overlay: AbstractOverlay): VideoFragment {
        overlayHost.attachOverlay(overlay)
        ensureFullscreenActionWired()
        installHooks()
        overlayHost.syncWindowTitleBar()
        return this
    }

    fun detachVideoOverlay() {
        overlayHost.detachOverlay()
        ensureFullscreenActionWired()
        installHooks()
        overlayHost.syncWindowTitleBar()
    }

    // -------------------------------------------------------------------------
    // OverlayStageProvider Contract
    // -------------------------------------------------------------------------

    override val stageOverlayHost: OverlayHost
        get() = overlayHost

    override val stageTargetComponent: AbstractComponent
        get() = videoPlayer

    override val isStagePlaying: Boolean
        get() = isPlaying

    override val stageTitle: String
        get() = overlayHost.state.title.ifBlank {
            hostingWindow?.getTitle().orEmpty().ifBlank { pageTitle }
        }

    override val stageAspectRatio: Pair<Int, Int>
        get() = resolveStageAspectRatio()

    override fun toggleStagePlayPause() = togglePlayPause()

    override fun pauseStagePlayback() = pause()

    override fun toggleStageFullscreen() {
        toggleFullscreen()
    }

    // -------------------------------------------------------------------------
    // Fragment Lifecycle & Window Hook Synchronization
    // -------------------------------------------------------------------------

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        installHooks()
        overlayHost.syncWindowTitleBar()
    }

    override fun onStart() {
        super.onStart()
        installHooks()
        overlayHost.syncWindowTitleBar()
    }

    override fun onResume() {
        super.onResume()
        installHooks()
        overlayHost.syncWindowTitleBar()
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        overlayHost.onResize(viewportWidth, viewportHeight)
        overlayHost.syncWindowTitleBar()
    }

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
        installHooks()
        overlayHost.syncWindowTitleBar()
    }

    override fun isFullscreen(): Boolean =
        super.isFullscreen() || overlayHost.isFullscreen() || videoPlayer.isFullscreen()

    override fun toggleFullscreen(cutoutMode: DisplayCutoutMode): Boolean {
        setFullscreen(!isFullscreen(), cutoutMode)
        return isFullscreen()
    }

    override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) {
        if (!fullscreen && overlayHost.isFullscreen()) {
            overlayHost.exitFullscreen()
        }
        super.setFullscreen(fullscreen, cutoutMode)
        val fs = isFullscreen()
        overlayHost.updateState { it.copy(isFullscreen = fs) }
        overlayHost.syncWindowTitleBar()
        videoPlayer.notifyStateChangeObservers()
    }

    override fun onDestroy() {
        disposedFlag = true
        cleanupHooks()
        if (!overlayHost.isDisposed) {
            overlayHost.dispose()
        }
        if (!videoPlayer.isDisposed) {
            videoPlayer.dispose()
        }
        super.onDestroy()
    }

    override fun dispose() {
        disposedFlag = true
        cleanupHooks()
        if (!overlayHost.isDisposed) {
            overlayHost.dispose()
        }
        if (!videoPlayer.isDisposed) {
            videoPlayer.dispose()
        }
        super.dispose()
    }

    // -------------------------------------------------------------------------
    // Player Contract Delegation
    // -------------------------------------------------------------------------

    override val sourceUrl: String?
        get() = videoPlayer.sourceUrl

    override val seekOffsetSeconds: Double
        get() = videoPlayer.seekOffsetSeconds

    override val durationOverrideSeconds: Double?
        get() = videoPlayer.durationOverrideSeconds

    override var volume: Float
        get() = videoPlayer.volume
        set(value) {
            videoPlayer.volume = value
        }

    override var isMuted: Boolean
        get() = videoPlayer.isMuted
        set(value) {
            videoPlayer.isMuted = value
        }

    override var playbackSpeed: Float
        get() = videoPlayer.playbackSpeed
        set(value) {
            videoPlayer.playbackSpeed = value
        }

    override var preloadMode: MediaPreloadMode
        get() = videoPlayer.preloadMode
        set(value) {
            videoPlayer.preloadMode = value
        }

    override var adaptivePreloadBufferingEnabled: Boolean
        get() = videoPlayer.adaptivePreloadBufferingEnabled
        set(value) {
            videoPlayer.adaptivePreloadBufferingEnabled = value
        }

    override var keepScreenOn: Boolean
        get() = videoPlayer.keepScreenOn
        set(value) {
            videoPlayer.keepScreenOn = value
        }

    override val isMediaReady: Boolean
        get() = videoPlayer.isMediaReady

    override val isBuffering: Boolean
        get() = videoPlayer.isBuffering

    override val bufferedRanges: List<Pair<Double, Double>>
        get() = videoPlayer.bufferedRanges

    override val bufferedEndSeconds: Double
        get() = videoPlayer.bufferedEndSeconds

    override val estimatedNetworkSpeedRatio: Double
        get() = videoPlayer.estimatedNetworkSpeedRatio

    override val currentTimeSeconds: Double
        get() = videoPlayer.currentTimeSeconds

    override val durationSeconds: Double
        get() = videoPlayer.durationSeconds

    override val isPlaying: Boolean
        get() = videoPlayer.isPlaying

    override var onMediaReady: (() -> Unit)?
        get() = videoPlayer.onMediaReady
        set(value) {
            videoPlayer.onMediaReady = value
        }

    override var onTimeUpdate: ((Double) -> Unit)?
        get() = videoPlayer.onTimeUpdate
        set(value) {
            videoPlayer.onTimeUpdate = value
        }

    override var onDurationChanged: ((Double) -> Unit)?
        get() = videoPlayer.onDurationChanged
        set(value) {
            videoPlayer.onDurationChanged = value
        }

    override var onPlayingStateChanged: ((Boolean) -> Unit)?
        get() = videoPlayer.onPlayingStateChanged
        set(value) {
            videoPlayer.onPlayingStateChanged = value
        }

    override var onBufferingChanged: ((Boolean) -> Unit)?
        get() = videoPlayer.onBufferingChanged
        set(value) {
            videoPlayer.onBufferingChanged = value
        }

    override var onBufferedRangesChanged: ((List<Pair<Double, Double>>) -> Unit)?
        get() = videoPlayer.onBufferedRangesChanged
        set(value) {
            videoPlayer.onBufferedRangesChanged = value
        }

    override var onEnded: (() -> Unit)?
        get() = videoPlayer.onEnded
        set(value) {
            videoPlayer.onEnded = value
        }

    override var onError: ((String) -> Unit)?
        get() = videoPlayer.onError
        set(value) {
            videoPlayer.onError = value
        }

    override suspend fun awaitMediaReady(timeoutMs: Long): Boolean =
        videoPlayer.awaitMediaReady(timeoutMs)

    override fun getRequiredSafeBufferSeconds(): Double =
        videoPlayer.getRequiredSafeBufferSeconds()

    override fun setSource(url: String, seekOffsetSeconds: Double, durationOverrideSeconds: Double?) {
        videoPlayer.setSource(url, seekOffsetSeconds, durationOverrideSeconds)
    }

    override fun play() {
        videoPlayer.play()
    }

    override fun pause() {
        videoPlayer.pause()
    }

    override fun togglePlayPause() {
        videoPlayer.togglePlayPause()
    }

    override fun stop() {
        videoPlayer.stop()
    }

    override fun seekTo(seconds: Double) {
        videoPlayer.seekTo(seconds)
    }

    override fun skipDelta(deltaSeconds: Double) {
        videoPlayer.skipDelta(deltaSeconds)
    }

    override fun setBufferingState(buffering: Boolean) {
        videoPlayer.setBufferingState(buffering)
    }

    override fun updateBufferedRanges(ranges: List<Pair<Double, Double>>) {
        videoPlayer.updateBufferedRanges(ranges)
    }

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        videoPlayer.attachAudioAnalyzer(analyzer)
    }

    // -------------------------------------------------------------------------
    // Additional VideoPlayer Metrics & Track / Subtitle Helpers
    // -------------------------------------------------------------------------

    val positionMs: Long
        get() = (currentTimeSeconds * 1000.0).roundToLong().coerceAtLeast(0L)

    val durationMs: Long
        get() = (durationSeconds * 1000.0).roundToLong().coerceAtLeast(0L)

    var resizeMode: VideoResizeMode
        get() = videoPlayer.resizeMode
        set(value) {
            videoPlayer.resizeMode = value
        }

    var antialiasing: Boolean
        get() = videoPlayer.antialiasing
        set(value) {
            videoPlayer.antialiasing = value
        }

    val presentedFrameCount: Long
        get() = videoPlayer.presentedFrameCount

    var onBufferingStateChanged: ((Boolean) -> Unit)?
        get() = videoPlayer.onBufferingStateChanged
        set(value) {
            videoPlayer.onBufferingStateChanged = value
        }

    var onTracksChanged: (() -> Unit)?
        get() = videoPlayer.onTracksChanged
        set(value) {
            videoPlayer.onTracksChanged = value
        }

    @get:kotlin.jvm.JvmName("audioTracksProperty")
    val audioTracks: List<MediaTrackInfo>
        get() = videoPlayer.getAudioTracks()

    @get:kotlin.jvm.JvmName("subtitleTracksProperty")
    val subtitleTracks: List<SubtitleTrackInfo>
        get() = videoPlayer.getSubtitleTracks()

    var internalSubtitlesEnabled: Boolean
        get() = videoPlayer.internalSubtitlesEnabled
        set(value) {
            videoPlayer.internalSubtitlesEnabled = value
        }

    val subtitleCues: List<SubtitleCue>
        get() = videoPlayer.subtitleCues

    fun getAudioTracks(): List<MediaTrackInfo> = videoPlayer.getAudioTracks()

    fun setAudioTracks(tracks: List<MediaTrackInfo>) {
        videoPlayer.setAudioTracks(tracks)
    }

    fun getSelectedAudioTrackId(): String? = videoPlayer.getSelectedAudioTrackId()

    fun selectAudioTrack(trackId: String): Boolean = videoPlayer.selectAudioTrack(trackId)

    fun selectAudioTrackByIndex(index: Int): Boolean = videoPlayer.selectAudioTrackByIndex(index)

    fun getSubtitleTracks(): List<SubtitleTrackInfo> = videoPlayer.getSubtitleTracks()

    fun getSelectedSubtitleTrackId(): String? = videoPlayer.getSelectedSubtitleTrackId()

    fun addExternalSubtitleTrack(label: String, language: String, vttOrSrtUrl: String) {
        videoPlayer.addExternalSubtitleTrack(label, language, vttOrSrtUrl)
    }

    fun selectSubtitleTrack(trackId: String?): Boolean = videoPlayer.selectSubtitleTrack(trackId)

    fun selectSubtitleTrackByIndex(index: Int?): Boolean = videoPlayer.selectSubtitleTrackByIndex(index)

    fun loadSubtitleContent(vttOrSrtContent: String, trackId: String? = null): List<SubtitleCue> =
        overlayHost.loadSubtitleContent(vttOrSrtContent, trackId)

    fun attachSubtitleCues(cues: List<SubtitleCue>, trackId: String? = null) {
        overlayHost.attachSubtitleCues(cues, trackId)
    }

    fun getActiveSubtitleCue(atTimeSeconds: Double = currentTimeSeconds): SubtitleCue? =
        videoPlayer.getActiveSubtitleCue(atTimeSeconds)

    fun getActiveSubtitleCueText(atTimeSeconds: Double = currentTimeSeconds): String =
        videoPlayer.getActiveSubtitleCueText(atTimeSeconds)

    // -------------------------------------------------------------------------
    // Internal Hook Management
    // -------------------------------------------------------------------------

    private fun resolveStageAspectRatio(): Pair<Int, Int> {
        val attrW = (videoPlayer.getAttribute("data-video-width")
            ?: videoPlayer.getAttribute("videoWidth"))?.toIntOrNull()
        val attrH = (videoPlayer.getAttribute("data-video-height")
            ?: videoPlayer.getAttribute("videoHeight"))?.toIntOrNull()
        if (attrW != null && attrH != null && attrW > 0 && attrH > 0) {
            return attrW to attrH
        }
        val wPx = videoPlayer.widthPx
        val hPx = videoPlayer.heightPx
        if (wPx != null && hPx != null && wPx > 0 && hPx > 0) {
            return wPx to hPx
        }
        val bounds = videoPlayer.getBounds()
        if (bounds.width > 0 && bounds.height > 0) {
            return bounds.width to bounds.height
        }
        return 16 to 9
    }

    private fun ensureFullscreenActionWired() {
        overlayHost.fullscreenStateOverride = { isFullscreen() }
        overlayHost.onToggleFullscreenOverride = { toggleFullscreen() }
        if (overlayHost.actions.onToggleFullscreen == null) {
            overlayHost.updateActions { existing ->
                existing.copy(
                    onToggleFullscreen = {
                        toggleFullscreen()
                        overlayHost.binder.syncStateFromPlayer()
                    }
                )
            }
        }
    }

    private fun installHooks() {
        val target = (fragmentHost as? AbstractComponent)
            ?: (hostingWindow as? AbstractComponent)
            ?: (parent as? AbstractComponent)
        if (hookedFullscreenTarget !== target) {
            detachHostFullscreenHook()
            if (target != null && !target.isDisposed) {
                previousFullscreenListener = target.onFullscreenChanged
                target.onFullscreenChanged = fullscreenListenerForwarder
                hookedFullscreenTarget = target
            }
        }
        if (!hookedOverlayHostFullscreen && !overlayHost.isDisposed &&
            overlayHost.onFullscreenChanged !== overlayHostFullscreenForwarder
        ) {
            previousOverlayHostFullscreenListener = overlayHost.onFullscreenChanged
            overlayHost.onFullscreenChanged = overlayHostFullscreenForwarder
            hookedOverlayHostFullscreen = true
        }
        if (!hookedPlayerFullscreen && !videoPlayer.isDisposed &&
            videoPlayer.onFullscreenChanged !== playerFullscreenForwarder
        ) {
            previousPlayerFullscreenListener = videoPlayer.onFullscreenChanged
            videoPlayer.onFullscreenChanged = playerFullscreenForwarder
            hookedPlayerFullscreen = true
        }
    }

    private fun detachHostFullscreenHook() {
        val prev = hookedFullscreenTarget
        val prevListener = previousFullscreenListener
        hookedFullscreenTarget = null
        previousFullscreenListener = null
        if (prev != null && prev.onFullscreenChanged === fullscreenListenerForwarder) {
            prev.onFullscreenChanged = prevListener
        }
    }

    private fun cleanupHooks() {
        detachHostFullscreenHook()
        if (hookedOverlayHostFullscreen && overlayHost.onFullscreenChanged === overlayHostFullscreenForwarder) {
            overlayHost.onFullscreenChanged = previousOverlayHostFullscreenListener
        }
        previousOverlayHostFullscreenListener = null
        hookedOverlayHostFullscreen = false
        if (hookedPlayerFullscreen && videoPlayer.onFullscreenChanged === playerFullscreenForwarder) {
            videoPlayer.onFullscreenChanged = previousPlayerFullscreenListener
        }
        previousPlayerFullscreenListener = null
        hookedPlayerFullscreen = false
    }
}
