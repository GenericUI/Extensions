package com.nexustools.genericui.ext.media.alien

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.MediaTrackInfo
import com.nexustools.genericui.component.media.SubtitleTrackInfo
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.media.MediaPreloadMode
import com.nexustools.genericui.enums.media.VideoResizeMode
import com.nexustools.genericui.ext.alien.AlienCssStyleResolver
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienResolvedVisualStyle
import com.nexustools.genericui.ext.alien.impl.AlienImpl
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.roundToInt

/**
 * Self-rendered Alien [VideoPlayerNativeImpl] (`<video>` element in an Alien scene graph).
 *
 * Wraps a headless-capable [VideoPlayerNativeImpl] delegate with offscreen RGBA frame capture
 * enabled (never attaching an OS child HWND/underlay hole), measures using intrinsic natural video
 * dimensions (defaulting to W3C `300x150` until known while respecting CSS sizing/aspect-ratio),
 * and composites decoded frames into the Alien [ImageBuffer2DContext] obeying [VideoResizeMode].
 */
class VideoPlayerAlienImpl(
    val videoPlayerComponent: VideoPlayer? = null,
    session: AlienRenderSession? = null,
    delegateOverride: VideoPlayerNativeImpl<*>? = null
) : AlienImpl(
    componentClassPrefix = "video-player",
    tagName = "video",
    session = session
), VideoPlayerNativeImpl<Any?> {

    val delegate: VideoPlayerNativeImpl<*> =
        delegateOverride ?: MediaExtension.createVideoPlayerNativeOrFallback()

    private var disposedFlag: Boolean = false
    override val isDisposed: Boolean
        get() = disposedFlag

    private var lastPaintedFrameVersion: Long = -1L

    private var upstreamOnTimeUpdate: ((Double) -> Unit)? = null
    private var upstreamOnDurationChanged: ((Double) -> Unit)? = null
    private var upstreamOnEnded: (() -> Unit)? = null
    private var upstreamOnBufferingChanged: ((Boolean) -> Unit)? = null
    private var upstreamOnError: ((String) -> Unit)? = null
    private var upstreamOnPlayingStateChanged: ((Boolean) -> Unit)? = null
    private var upstreamOnBufferingStateChanged: ((Boolean) -> Unit)? = null
    private var upstreamOnBufferedRangesChanged: ((List<Pair<Double, Double>>) -> Unit)? = null
    private var upstreamOnTracksChanged: (() -> Unit)? = null
    private var upstreamOnMediaReady: (() -> Unit)? = null
    private var upstreamOnBeforeFramePresent: ((Long, Double) -> Unit)? = null

    init {
        delegate.enableOffscreenFrameCapture(true)

        node.styleSnapshot = node.styleSnapshot.copy(display = DisplayMode.INLINE_BLOCK)
        node.intrinsicWidth = DEFAULT_VIDEO_WIDTH.toFloat()
        node.intrinsicHeight = DEFAULT_VIDEO_HEIGHT.toFloat()

        wireDelegateObservers()

        node.customMeasure = { availW, availH, style ->
            measureVideoContentSize(availW, availH, style)
        }

        node.customPaint = { ctx, bounds, style ->
            paintVideoFrame(ctx, bounds, style)
        }
    }

    private fun wireDelegateObservers() {
        delegate.setOnTimeUpdate { sec ->
            upstreamOnTimeUpdate?.invoke(sec)
        }
        delegate.setOnDurationChanged { dur ->
            syncNaturalDimensionsIfChanged()
            node.invalidateBounds()
            upstreamOnDurationChanged?.invoke(dur)
        }
        delegate.setOnEnded {
            node.hasContinuousAnimation = false
            node.invalidateBounds()
            upstreamOnEnded?.invoke()
        }
        delegate.setOnBufferingChanged { buffering ->
            upstreamOnBufferingChanged?.invoke(buffering)
        }
        delegate.setOnError { err ->
            node.hasContinuousAnimation = false
            node.invalidateBounds()
            upstreamOnError?.invoke(err)
        }
        delegate.setOnPlayingStateChanged { playing ->
            node.hasContinuousAnimation = playing
            node.invalidateBounds()
            upstreamOnPlayingStateChanged?.invoke(playing)
        }
        delegate.setOnBufferingStateChanged { buffering ->
            upstreamOnBufferingStateChanged?.invoke(buffering)
        }
        delegate.setOnBufferedRangesChanged { ranges ->
            upstreamOnBufferedRangesChanged?.invoke(ranges)
        }
        delegate.setOnTracksChanged {
            upstreamOnTracksChanged?.invoke()
        }
        delegate.setOnMediaReady {
            syncNaturalDimensionsIfChanged()
            node.invalidateBounds()
            upstreamOnMediaReady?.invoke()
        }
        delegate.setOnBeforeFramePresent { frameIdx, tsMs ->
            syncNaturalDimensionsIfChanged()
            node.invalidateBounds()
            upstreamOnBeforeFramePresent?.invoke(frameIdx, tsMs)
        }
    }

    private fun syncNaturalDimensionsIfChanged() {
        val nw = delegate.getNaturalVideoWidth()
        val nh = delegate.getNaturalVideoHeight()
        if (nw > 0 && nh > 0) {
            val nextW = nw.toFloat()
            val nextH = nh.toFloat()
            if (node.intrinsicWidth != nextW || node.intrinsicHeight != nextH) {
                node.intrinsicWidth = nextW
                node.intrinsicHeight = nextH
                node.markLayoutDirty()
                node.invalidateBounds()
            }
        }
    }

    private fun measureVideoContentSize(
        availW: Int,
        availH: Int,
        style: AlienResolvedVisualStyle
    ): Size {
        val natW = delegate.getNaturalVideoWidth().takeIf { it > 0 } ?: DEFAULT_VIDEO_WIDTH
        val natH = delegate.getNaturalVideoHeight().takeIf { it > 0 } ?: DEFAULT_VIDEO_HEIGHT
        val intrinsicRatio = (natW.toFloat() / natH.toFloat()).coerceAtLeast(1e-4f)
        val ratio = style.aspectRatio?.takeIf { it > 0f } ?: intrinsicRatio
        val pref = node.preferredSize

        val cssW = style.widthCss?.trim()?.takeUnless { it.equals("auto", ignoreCase = true) }?.let {
            AlienCssStyleResolver.parseLengthPx(it, style.fontSizePx, availW.toFloat())?.roundToInt()
        }?.takeIf { it > 0 } ?: pref?.width?.takeIf { it > 0 }

        val cssH = style.heightCss?.trim()?.takeUnless { it.equals("auto", ignoreCase = true) }?.let {
            AlienCssStyleResolver.parseLengthPx(it, style.fontSizePx, availH.toFloat())?.roundToInt()
        }?.takeIf { it > 0 } ?: pref?.height?.takeIf { it > 0 }

        val maxW = style.maxWidthCss?.trim()?.takeUnless { it.equals("none", ignoreCase = true) }?.let {
            AlienCssStyleResolver.parseLengthPx(it, style.fontSizePx, availW.toFloat())?.roundToInt()
        }?.takeIf { it > 0 }

        val maxH = style.maxHeightCss?.trim()?.takeUnless { it.equals("none", ignoreCase = true) }?.let {
            AlienCssStyleResolver.parseLengthPx(it, style.fontSizePx, availH.toFloat())?.roundToInt()
        }?.takeIf { it > 0 }

        var targetW: Int
        var targetH: Int
        when {
            cssW != null && cssH != null -> {
                targetW = cssW
                targetH = cssH
            }
            cssW != null -> {
                targetW = cssW
                targetH = (targetW / ratio).roundToInt().coerceAtLeast(1)
            }
            cssH != null -> {
                targetH = cssH
                targetW = (targetH * ratio).roundToInt().coerceAtLeast(1)
            }
            else -> {
                targetW = natW
                targetH = natH
            }
        }

        if (maxW != null && targetW > maxW) {
            targetW = maxW
            if (cssH == null) {
                targetH = (targetW / ratio).roundToInt().coerceAtLeast(1)
            }
        }
        if (maxH != null && targetH > maxH) {
            targetH = maxH
            if (cssW == null) {
                targetW = (targetH * ratio).roundToInt().coerceAtLeast(1)
            }
        }

        val padW = style.padding.left + style.padding.right
        val padH = style.padding.top + style.padding.bottom
        return Size(
            width = (targetW + padW).coerceAtLeast(1),
            height = (targetH + padH).coerceAtLeast(1)
        )
    }

    private fun paintVideoFrame(ctx: ImageBuffer2DContext, bounds: Rect, style: AlienResolvedVisualStyle) {
        val pad = style.padding
        val drawX = (bounds.x + pad.left).toFloat()
        val drawY = (bounds.y + pad.top).toFloat()
        val drawW = (bounds.width - pad.left - pad.right).coerceAtLeast(0).toFloat()
        val drawH = (bounds.height - pad.top - pad.bottom).coerceAtLeast(0).toFloat()
        if (drawW <= 0f || drawH <= 0f) return

        if (delegate.isPlaying()) {
            delegate.stepHeadlessPlaybackIfNeeded(1.0 / 60.0)
        }
        syncNaturalDimensionsIfChanged()

        val frame = delegate.copyLatestVideoFrame()
        lastPaintedFrameVersion = delegate.videoFrameVersion

        ctx.save()
        ctx.setGlobalAlpha(style.opacity.coerceIn(0f, 1f))
        ctx.setFillColor(RgbaColor(0, 0, 0, 255))
        ctx.fillRect(drawX, drawY, drawW, drawH)

        if (frame != null && frame.width > 0 && frame.height > 0) {
            val fw = frame.width.toFloat()
            val fh = frame.height.toFloat()
            ctx.imageSmoothingEnabled = delegate.isAntialiasing()
            when (delegate.getResizeMode()) {
                VideoResizeMode.FIT -> {
                    val scale = minOf(drawW / fw, drawH / fh)
                    val destW = fw * scale
                    val destH = fh * scale
                    val destX = drawX + (drawW - destW) / 2f
                    val destY = drawY + (drawH - destH) / 2f
                    ctx.drawImage(
                        image = frame,
                        sx = 0f,
                        sy = 0f,
                        sWidth = fw,
                        sHeight = fh,
                        dx = destX,
                        dy = destY,
                        dWidth = destW,
                        dHeight = destH
                    )
                }
                VideoResizeMode.FILL -> {
                    val targetAspect = drawW / drawH
                    val frameAspect = fw / fh
                    val sx: Float
                    val sy: Float
                    val sw: Float
                    val sh: Float
                    if (frameAspect > targetAspect) {
                        sh = fh
                        sw = fh * targetAspect
                        sx = (fw - sw) / 2f
                        sy = 0f
                    } else {
                        sw = fw
                        sh = fw / targetAspect
                        sx = 0f
                        sy = (fh - sh) / 2f
                    }
                    ctx.drawImage(
                        image = frame,
                        sx = sx,
                        sy = sy,
                        sWidth = sw,
                        sHeight = sh,
                        dx = drawX,
                        dy = drawY,
                        dWidth = drawW,
                        dHeight = drawH
                    )
                }
                VideoResizeMode.STRETCH -> {
                    ctx.drawImage(
                        image = frame,
                        sx = 0f,
                        sy = 0f,
                        sWidth = fw,
                        sHeight = fh,
                        dx = drawX,
                        dy = drawY,
                        dWidth = drawW,
                        dHeight = drawH
                    )
                }
            }
        }
        ctx.restore()
    }

    override fun setSource(url: String, seekOffsetSeconds: Double, durationOverrideSeconds: Double?) {
        delegate.enableOffscreenFrameCapture(true)
        delegate.setSource(url, seekOffsetSeconds, durationOverrideSeconds)
        syncNaturalDimensionsIfChanged()
        node.invalidateBounds()
    }

    override fun play() {
        delegate.play()
        node.hasContinuousAnimation = delegate.isPlaying()
        node.invalidateBounds()
    }

    override fun pause() {
        delegate.pause()
        node.hasContinuousAnimation = false
        node.invalidateBounds()
    }

    override fun stop() {
        delegate.stop()
        node.hasContinuousAnimation = false
        node.invalidateBounds()
    }

    override fun seekTo(seconds: Double) {
        delegate.seekTo(seconds)
        node.invalidateBounds()
    }

    override fun getCurrentTimeSeconds(): Double = delegate.getCurrentTimeSeconds()

    override fun getDurationSeconds(): Double = delegate.getDurationSeconds()

    override fun isPlaying(): Boolean = delegate.isPlaying()

    override fun setPlaybackSpeed(speed: Float) {
        delegate.setPlaybackSpeed(speed)
    }

    override fun getPlaybackSpeed(): Float = delegate.getPlaybackSpeed()

    override fun setVolume(volume: Float) {
        delegate.setVolume(volume)
    }

    override fun getVolume(): Float = delegate.getVolume()

    override fun setMuted(muted: Boolean) {
        delegate.setMuted(muted)
    }

    override fun isMuted(): Boolean = delegate.isMuted()

    override fun setResizeMode(mode: VideoResizeMode) {
        delegate.setResizeMode(mode)
        node.invalidateBounds()
    }

    override fun getResizeMode(): VideoResizeMode = delegate.getResizeMode()

    override fun setLoop(loop: Boolean) {
        delegate.setLoop(loop)
    }

    override fun isLoop(): Boolean = delegate.isLoop()

    override fun getAudioTracks(): List<MediaTrackInfo> = delegate.getAudioTracks()

    override fun getSelectedAudioTrackId(): String? = delegate.getSelectedAudioTrackId()

    override fun selectAudioTrack(trackId: String): Boolean = delegate.selectAudioTrack(trackId)

    override fun getSubtitleTracks(): List<SubtitleTrackInfo> = delegate.getSubtitleTracks()

    override fun getSelectedSubtitleTrackId(): String? = delegate.getSelectedSubtitleTrackId()

    override fun addExternalSubtitleTrack(label: String, language: String, vttOrSrtUrl: String) {
        delegate.addExternalSubtitleTrack(label, language, vttOrSrtUrl)
    }

    override fun selectSubtitleTrack(trackId: String?): Boolean = delegate.selectSubtitleTrack(trackId)

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        delegate.attachAudioAnalyzer(analyzer)
    }

    override fun setOnTimeUpdate(listener: ((Double) -> Unit)?) {
        upstreamOnTimeUpdate = listener
    }

    override fun setOnDurationChanged(listener: ((Double) -> Unit)?) {
        upstreamOnDurationChanged = listener
    }

    override fun setOnEnded(listener: (() -> Unit)?) {
        upstreamOnEnded = listener
    }

    override fun setOnBufferingChanged(listener: ((Boolean) -> Unit)?) {
        upstreamOnBufferingChanged = listener
    }

    override fun setOnError(listener: ((String) -> Unit)?) {
        upstreamOnError = listener
    }

    override fun setOnPlayingStateChanged(listener: ((Boolean) -> Unit)?) {
        upstreamOnPlayingStateChanged = listener
    }

    override fun setOnBufferingStateChanged(listener: ((Boolean) -> Unit)?) {
        upstreamOnBufferingStateChanged = listener
    }

    override fun setOnBufferedRangesChanged(listener: ((List<Pair<Double, Double>>) -> Unit)?) {
        upstreamOnBufferedRangesChanged = listener
    }

    override fun setOnTracksChanged(listener: (() -> Unit)?) {
        upstreamOnTracksChanged = listener
    }

    override fun setPreloadMode(mode: MediaPreloadMode) {
        delegate.setPreloadMode(mode)
    }

    override fun getPreloadMode(): MediaPreloadMode = delegate.getPreloadMode()

    override fun setAdaptivePreloadBufferingEnabled(enabled: Boolean) {
        delegate.setAdaptivePreloadBufferingEnabled(enabled)
    }

    override fun isAdaptivePreloadBufferingEnabled(): Boolean = delegate.isAdaptivePreloadBufferingEnabled()

    override fun getBufferedRanges(): List<Pair<Double, Double>> = delegate.getBufferedRanges()

    override fun getEstimatedNetworkSpeedRatio(): Double = delegate.getEstimatedNetworkSpeedRatio()

    override fun getRequiredSafeBufferSeconds(): Double = delegate.getRequiredSafeBufferSeconds()

    override fun setAntialiasing(antialiasing: Boolean) {
        delegate.setAntialiasing(antialiasing)
        node.invalidateBounds()
    }

    override fun isAntialiasing(): Boolean = delegate.isAntialiasing()

    override fun setOnMediaReady(listener: (() -> Unit)?) {
        upstreamOnMediaReady = listener
        if (delegate.isMediaReady()) {
            listener?.invoke()
        }
    }

    override fun isMediaReady(): Boolean = delegate.isMediaReady()

    override fun getPresentedFrameCount(): Long = delegate.getPresentedFrameCount()

    override fun setOnBeforeFramePresent(listener: ((frameIndex: Long, timestampMs: Double) -> Unit)?) {
        upstreamOnBeforeFramePresent = listener
    }

    override fun setInternalSubtitlesEnabled(enabled: Boolean) {
        delegate.setInternalSubtitlesEnabled(enabled)
    }

    override fun isInternalSubtitlesEnabled(): Boolean = delegate.isInternalSubtitlesEnabled()

    override fun setKeepScreenOn(keepScreenOn: Boolean) {
        delegate.setKeepScreenOn(keepScreenOn)
    }

    override fun isKeepScreenOn(): Boolean = delegate.isKeepScreenOn()

    override fun bindScreenWakeToken(token: Long) {
        delegate.bindScreenWakeToken(token)
    }

    override fun enableOffscreenFrameCapture(enabled: Boolean) {
        // Alien renders strictly inline without OS child windows; always keep delegate capture enabled.
        delegate.enableOffscreenFrameCapture(true)
    }

    override fun copyLatestVideoFrame(): ImageBuffer? {
        val frame = delegate.copyLatestVideoFrame()
        val currentVersion = delegate.videoFrameVersion
        if (currentVersion != lastPaintedFrameVersion) {
            node.invalidateBounds()
        }
        return frame
    }

    override val videoFrameVersion: Long
        get() = delegate.videoFrameVersion

    override fun getNaturalVideoWidth(): Int = delegate.getNaturalVideoWidth()

    override fun getNaturalVideoHeight(): Int = delegate.getNaturalVideoHeight()

    override fun stepHeadlessPlaybackIfNeeded(deltaSeconds: Double) {
        delegate.stepHeadlessPlaybackIfNeeded(deltaSeconds)
    }

    override fun dispose() {
        if (disposedFlag) return
        disposedFlag = true
        node.hasContinuousAnimation = false
        if (!delegate.isDisposed) {
            delegate.dispose()
        }
        super.dispose()
    }

    private companion object {
        const val DEFAULT_VIDEO_WIDTH: Int = 300
        const val DEFAULT_VIDEO_HEIGHT: Int = 150
    }
}
