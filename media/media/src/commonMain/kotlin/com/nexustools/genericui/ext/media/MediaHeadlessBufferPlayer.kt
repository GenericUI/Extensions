@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.media

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.MediaTrackInfo
import com.nexustools.genericui.component.media.SubtitleCue
import com.nexustools.genericui.component.media.SubtitleTrackInfo
import com.nexustools.genericui.component.media.WebVttSrtParser
import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.enums.media.MediaPreloadMode
import com.nexustools.genericui.enums.media.VideoResizeMode
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl
import com.nexustools.genericui.res.DataUrlCodec
import com.nexustools.genericui.style.css.ComputedStyle
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/**
 * Pure-Kotlin cross-platform fallback & data-URI container player implementing [VideoPlayerNativeImpl].
 *
 * Parses RIFF/WAVE PCM streams (for accurate duration, sample rate, channel count, and
 * [AudioAnalyzer] spectrum feeding) and ISO-BMFF/MP4 headers (`mvhd`/`tkhd`/`mdhd`/`hdlr`/`stbl`)
 * to produce time-progressing RGBA_8888 [ImageBuffer] frames in headless and offscreen environments
 * without native runtime binaries or OS window surfaces.
 */
open class MediaHeadlessBufferPlayer : VideoPlayerNativeImpl<Any?> {
    override val nativeWidget: Any?
        get() = this

    override val componentClassPrefix: String = "video-player"

    private var classString: String = ""
    private var bootstrapType: BootstrapType? = null
    private var bootstrapOutline: Boolean = false
    private var bootstrapSize: BootstrapSize? = null
    private var backgroundType: BootstrapType? = null
    private var textColorType: BootstrapType? = null
    private var borderType: BootstrapType? = null
    private var textAlignment: TextAlignment? = null
    private var fontWeight: FontWeight? = null
    private var textTransform: TextTransform? = null
    private var textOverflow: TextOverflow? = null
    private var borderRadiusStyle: BorderRadiusStyle? = null
    private var shadowElevation: ShadowElevation? = null
    private var displayMode: DisplayMode? = null
    private var flexDirection: FlexDirection? = null
    private var flexWrap: FlexWrap? = null
    private var justifyContent: JustifyContent? = null
    private var alignItems: AlignItems? = null
    private var alignSelf: AlignSelf? = null
    private var overflowBehavior: OverflowBehavior? = null
    private var positionMode: PositionMode? = null
    private var marginAllScale: SpacingScale? = null
    private var paddingAllScale: SpacingScale? = null
    private var visibleFlag: Boolean = true
    private var enabledFlag: Boolean = true
    private var opacityValue: Float = 1f
    private var tooltipValue: String? = null
    private var preferredSizeValue: Size? = null
    private var paddingInsets: EdgeInsets = EdgeInsets.ZERO
    private var marginInsets: EdgeInsets = EdgeInsets.ZERO
    private var lastComputedStyle: ComputedStyle? = null

    override fun setClass(className: String) {
        classString = className.trim()
    }

    override fun getClass(): String = classString

    override fun addClass(className: String) {
        val clean = className.trim()
        if (clean.isEmpty() || hasClass(clean)) return
        classString = if (classString.isEmpty()) clean else "$classString $clean"
    }

    override fun removeClass(className: String) {
        val target = className.trim()
        if (target.isEmpty()) return
        classString = classString.split(Regex("\\s+")).filter { it.isNotEmpty() && it != target }.joinToString(" ")
    }

    override fun hasClass(className: String): Boolean {
        val target = className.trim()
        return target.isNotEmpty() && classString.split(Regex("\\s+")).any { it == target }
    }

    override fun setType(type: BootstrapType?) { bootstrapType = type }
    override fun getType(): BootstrapType? = bootstrapType
    override fun setOutline(outline: Boolean) { bootstrapOutline = outline }
    override fun isOutline(): Boolean = bootstrapOutline
    override fun setSize(size: BootstrapSize?) { bootstrapSize = size }
    override fun getSize(): BootstrapSize? = bootstrapSize
    override fun setBackgroundType(type: BootstrapType?) { backgroundType = type }
    override fun getBackgroundType(): BootstrapType? = backgroundType
    override fun setTextColorType(type: BootstrapType?) { textColorType = type }
    override fun getTextColorType(): BootstrapType? = textColorType
    override fun setBorderType(type: BootstrapType?) { borderType = type }
    override fun getBorderType(): BootstrapType? = borderType
    override fun setTextAlignment(alignment: TextAlignment?) { textAlignment = alignment }
    override fun getTextAlignment(): TextAlignment? = textAlignment
    override fun setFontWeight(weight: FontWeight?) { fontWeight = weight }
    override fun getFontWeight(): FontWeight? = fontWeight
    override fun setTextTransform(transform: TextTransform?) { textTransform = transform }
    override fun getTextTransform(): TextTransform? = textTransform
    override fun setTextOverflow(overflow: TextOverflow?) { textOverflow = overflow }
    override fun getTextOverflow(): TextOverflow? = textOverflow
    override fun setBorderRadius(radius: BorderRadiusStyle?) { borderRadiusStyle = radius }
    override fun getBorderRadius(): BorderRadiusStyle? = borderRadiusStyle
    override fun setShadow(shadow: ShadowElevation?) { shadowElevation = shadow }
    override fun getShadow(): ShadowElevation? = shadowElevation
    override fun setDisplay(display: DisplayMode?) { displayMode = display }
    override fun getDisplay(): DisplayMode? = displayMode
    override fun setFlexDirection(direction: FlexDirection?) { flexDirection = direction }
    override fun getFlexDirection(): FlexDirection? = flexDirection
    override fun setFlexWrap(wrap: FlexWrap?) { flexWrap = wrap }
    override fun getFlexWrap(): FlexWrap? = flexWrap
    override fun setJustifyContent(justify: JustifyContent?) { justifyContent = justify }
    override fun getJustifyContent(): JustifyContent? = justifyContent
    override fun setAlignItems(align: AlignItems?) { alignItems = align }
    override fun getAlignItems(): AlignItems? = alignItems
    override fun setAlignSelf(align: AlignSelf?) { alignSelf = align }
    override fun getAlignSelf(): AlignSelf? = alignSelf
    override fun setOverflow(overflow: OverflowBehavior?) { overflowBehavior = overflow }
    override fun getOverflow(): OverflowBehavior? = overflowBehavior
    override fun setPosition(position: PositionMode?) { positionMode = position }
    override fun getPosition(): PositionMode? = positionMode
    override fun setMarginAll(scale: SpacingScale?) { marginAllScale = scale }
    override fun getMarginAll(): SpacingScale? = marginAllScale
    override fun setPaddingAll(scale: SpacingScale?) { paddingAllScale = scale }
    override fun getPaddingAll(): SpacingScale? = paddingAllScale
    override fun setVisible(visible: Boolean) { visibleFlag = visible }
    override fun isVisible(): Boolean = visibleFlag
    override fun setEnabled(enabled: Boolean) { enabledFlag = enabled }
    override fun isEnabled(): Boolean = enabledFlag
    override fun setOpacity(opacity: Float) { opacityValue = opacity }
    override fun getOpacity(): Float = opacityValue
    override fun setTooltipText(tooltip: String?) { tooltipValue = tooltip }
    override fun getTooltipText(): String? = tooltipValue
    override fun setPreferredSize(size: Size?) { preferredSizeValue = size }
    override fun getBounds(): Rect = Rect(0, 0, naturalWidth, naturalHeight)
    override fun setPadding(padding: EdgeInsets) { paddingInsets = padding }
    override fun setMargin(margin: EdgeInsets) { marginInsets = margin }
    override fun applyComputedStyle(style: ComputedStyle) { lastComputedStyle = style }
    override fun setOnClick(listener: (() -> Unit)?) {}
    override fun setOnDoubleClick(listener: (() -> Unit)?) {}
    override fun setOnContextMenu(listener: ((PointerEvent) -> Unit)?) {}
    override fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?) {}
    override fun setOnPointerMove(listener: ((PointerEvent) -> Unit)?) {}
    override fun setOnPointerUp(listener: ((PointerEvent) -> Unit)?) {}
    override fun setOnWheel(listener: ((WheelEvent) -> Unit)?) {}
    override fun setOnKeyDown(listener: ((KeyEvent) -> Unit)?) {}
    override fun setOnKeyUp(listener: ((KeyEvent) -> Unit)?) {}

    private val frameLock = AtomicInt(0)
    private val frameSlots = arrayOfNulls<ImageBuffer>(3)
    private var ringWriteIndex = 0
    private var latestSlotIndex = -1
    private val frameVersionCounter = AtomicLong(0L)
    private val presentedFrameCounter = AtomicLong(0L)

    private var sourceUrl: String = ""
    private var durationSeconds: Double = 0.0
    private var currentTimeSeconds: Double = 0.0
    private var lastAnalyzerPushedSampleIndex: Int = 0
    private var playing: Boolean = false
    private var loopEnabled: Boolean = false
    private var playbackSpeed: Float = 1.0f
    private var volume: Float = 1.0f
    private var muted: Boolean = false
    private var resizeMode: VideoResizeMode = VideoResizeMode.FIT
    private var preloadMode: MediaPreloadMode = MediaPreloadMode.AUTO
    private var adaptivePreloadBufferingEnabled: Boolean = true
    private var antialiasing: Boolean = true
    private var internalSubtitlesEnabled: Boolean = true
    private var keepScreenOn: Boolean = true
    private var screenWakeToken: Long = 0L
    private var mediaReady: Boolean = false
    private var offscreenFrameCaptureEnabled: Boolean = false

    private var naturalWidth: Int = 0
    private var naturalHeight: Int = 0
    private var wavData: MediaWavData? = null
    private var mp4Metadata: MediaContainerMetadata? = null

    private val audioTracks = mutableListOf<MediaTrackInfo>()
    private var selectedAudioTrackId: String? = null
    private val subtitleTracks = mutableListOf<SubtitleTrackInfo>()
    private var selectedSubtitleTrackId: String? = null
    private val subtitleCuesByTrackId = linkedMapOf<String, List<SubtitleCue>>()

    private var attachedAnalyzer: AudioAnalyzer? = null
    private var lastClockMark = TimeSource.Monotonic.markNow()

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
    private var onBeforeFramePresentListener: ((Long, Double) -> Unit)? = null

    override fun setSource(url: String, seekOffsetSeconds: Double, durationOverrideSeconds: Double?) {
        sourceUrl = url
        playing = false
        mediaReady = false
        wavData = null
        mp4Metadata = null
        naturalWidth = 0
        naturalHeight = 0
        durationSeconds = 0.0
        currentTimeSeconds = seekOffsetSeconds.coerceAtLeast(0.0)
        lastAnalyzerPushedSampleIndex = 0
        audioTracks.clear()
        selectedAudioTrackId = null
        subtitleTracks.clear()
        selectedSubtitleTrackId = null
        subtitleCuesByTrackId.clear()
        clearFrameRing()
        lastClockMark = TimeSource.Monotonic.markNow()

        if (url.isBlank()) {
            onDurationChangedListener?.invoke(0.0)
            onTimeUpdateListener?.invoke(0.0)
            return
        }

        val resolvedBytes: ByteArray? = if (url.startsWith("data:", ignoreCase = true)) {
            DataUrlCodec.decodeBytes(url).also { decoded ->
                if (decoded == null) {
                    onErrorListener?.invoke("Failed to decode media data URL")
                }
            }
        } else {
            readLocalMediaFileBytes(url)
        }

        if (resolvedBytes != null) {
            val parsedWav = MediaWavDecoder.decode(resolvedBytes)
            if (parsedWav != null) {
                wavData = parsedWav
                durationSeconds = durationOverrideSeconds ?: parsedWav.durationSeconds
                audioTracks.add(
                    MediaTrackInfo(
                        id = "audio-0",
                        label = "PCM Audio",
                        language = "und",
                        codec = "pcm_${parsedWav.bitsPerSample}le",
                        channels = parsedWav.channels,
                        sampleRateHz = parsedWav.sampleRateHz,
                        isDefault = true,
                        isSelected = true
                    )
                )
                selectedAudioTrackId = "audio-0"
                mediaReady = true
            } else {
                val parsedMp4 = MediaMp4HeaderParser.parse(resolvedBytes)
                if (parsedMp4 != null) {
                    mp4Metadata = parsedMp4
                    naturalWidth = parsedMp4.naturalWidth
                    naturalHeight = parsedMp4.naturalHeight
                    durationSeconds = durationOverrideSeconds ?: parsedMp4.durationSeconds
                    audioTracks.addAll(parsedMp4.audioTracks)
                    selectedAudioTrackId = audioTracks.firstOrNull { it.isSelected }?.id
                    subtitleTracks.addAll(parsedMp4.subtitleTracks)
                    mediaReady = true
                } else {
                    if (durationOverrideSeconds != null) {
                        durationSeconds = durationOverrideSeconds
                        mediaReady = true
                    } else if (url.startsWith("data:", ignoreCase = true)) {
                        onErrorListener?.invoke("Unsupported data URL media container format")
                        return
                    }
                }
            }
        } else if (durationOverrideSeconds != null) {
            durationSeconds = durationOverrideSeconds
            mediaReady = true
        } else if (normalizeLocalMediaPath(url) != null) {
            onErrorListener?.invoke("Media source file not found or unreadable: $url")
            return
        }

        if (durationSeconds > 0.0) {
            currentTimeSeconds = currentTimeSeconds.coerceIn(0.0, durationSeconds)
        }
        lastAnalyzerPushedSampleIndex = timeToSampleIndex(currentTimeSeconds)
        if (offscreenFrameCaptureEnabled && naturalWidth > 0 && naturalHeight > 0) {
            renderAndPushFrame(currentTimeSeconds)
        }
        onDurationChangedListener?.invoke(durationSeconds)
        onTimeUpdateListener?.invoke(currentTimeSeconds)
        if (durationSeconds > 0.0) {
            onBufferedRangesChangedListener?.invoke(listOf(0.0 to durationSeconds))
        }
        onTracksChangedListener?.invoke()
        if (mediaReady) {
            onMediaReadyListener?.invoke()
        }
    }

    override fun play() {
        advanceFromClock()
        if (durationSeconds > 0.0 && currentTimeSeconds >= durationSeconds) {
            currentTimeSeconds = 0.0
            lastAnalyzerPushedSampleIndex = 0
        }
        playing = true
        lastClockMark = TimeSource.Monotonic.markNow()
        pushInitialWavWindowToAnalyzer()
        if (offscreenFrameCaptureEnabled && naturalWidth > 0 && naturalHeight > 0) {
            renderAndPushFrame(currentTimeSeconds)
        }
        onPlayingStateChangedListener?.invoke(true)
    }

    override fun pause() {
        advanceFromClock()
        playing = false
        lastClockMark = TimeSource.Monotonic.markNow()
        onPlayingStateChangedListener?.invoke(false)
    }

    override fun stop() {
        playing = false
        currentTimeSeconds = 0.0
        lastAnalyzerPushedSampleIndex = 0
        lastClockMark = TimeSource.Monotonic.markNow()
        if (offscreenFrameCaptureEnabled && naturalWidth > 0 && naturalHeight > 0) {
            renderAndPushFrame(0.0)
        }
        onPlayingStateChangedListener?.invoke(false)
        onTimeUpdateListener?.invoke(0.0)
    }

    override fun seekTo(seconds: Double) {
        val clamped = if (durationSeconds > 0.0) {
            seconds.coerceIn(0.0, durationSeconds)
        } else {
            seconds.coerceAtLeast(0.0)
        }
        currentTimeSeconds = clamped
        lastClockMark = TimeSource.Monotonic.markNow()
        lastAnalyzerPushedSampleIndex = timeToSampleIndex(clamped)
        pushInitialWavWindowToAnalyzer()
        if (offscreenFrameCaptureEnabled && naturalWidth > 0 && naturalHeight > 0) {
            renderAndPushFrame(currentTimeSeconds)
        }
        onTimeUpdateListener?.invoke(currentTimeSeconds)
    }

    override fun getCurrentTimeSeconds(): Double {
        advanceFromClock()
        return currentTimeSeconds
    }

    override fun getDurationSeconds(): Double = durationSeconds

    override fun isPlaying(): Boolean {
        advanceFromClock()
        return playing
    }

    override fun setPlaybackSpeed(speed: Float) {
        advanceFromClock()
        playbackSpeed = speed.coerceIn(0.1f, 8.0f)
    }

    override fun getPlaybackSpeed(): Float = playbackSpeed

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 2f)
    }

    override fun getVolume(): Float = volume

    override fun setMuted(muted: Boolean) {
        this.muted = muted
    }

    override fun isMuted(): Boolean = muted

    override fun setResizeMode(mode: VideoResizeMode) {
        resizeMode = mode
    }

    override fun getResizeMode(): VideoResizeMode = resizeMode

    override fun setLoop(loop: Boolean) {
        loopEnabled = loop
    }

    override fun isLoop(): Boolean = loopEnabled

    override fun getAudioTracks(): List<MediaTrackInfo> = audioTracks.toList()

    override fun selectAudioTrack(trackId: String): Boolean {
        val requestedIdx = if (trackId.startsWith("index:")) trackId.removePrefix("index:").toIntOrNull() else null
        var matched = false
        for (i in audioTracks.indices) {
            val item = audioTracks[i]
            val match = item.id == trackId || (requestedIdx != null && i == requestedIdx)
            if (match) {
                matched = true
                selectedAudioTrackId = item.id
            }
            audioTracks[i] = item.copy(isSelected = match)
        }
        if (matched) {
            onTracksChangedListener?.invoke()
        }
        return matched
    }

    override fun getSubtitleTracks(): List<SubtitleTrackInfo> = subtitleTracks.toList()

    override fun addExternalSubtitleTrack(label: String, language: String, vttOrSrtUrl: String) {
        val id = "sub-${subtitleTracks.size}"
        val lower = vttOrSrtUrl.lowercase()
        val format = if (lower.contains(".srt")) "srt" else "vtt"
        val selectNow = internalSubtitlesEnabled && selectedSubtitleTrackId == null
        if (selectNow) {
            selectedSubtitleTrackId = id
        }
        subtitleTracks.add(
            SubtitleTrackInfo(
                id = id,
                label = label,
                language = language,
                format = format,
                externalUrl = vttOrSrtUrl,
                isSelected = selectNow
            )
        )
        val rawText = if (vttOrSrtUrl.startsWith("data:", ignoreCase = true)) {
            DataUrlCodec.decodeUtf8Text(vttOrSrtUrl)
        } else {
            readLocalMediaFileBytes(vttOrSrtUrl)?.decodeToString()
        }
        if (!rawText.isNullOrBlank()) {
            subtitleCuesByTrackId[id] = WebVttSrtParser.parse(rawText)
        }
        onTracksChangedListener?.invoke()
    }

    override fun selectSubtitleTrack(trackId: String?): Boolean {
        if (trackId == null) {
            selectedSubtitleTrackId = null
            for (i in subtitleTracks.indices) {
                subtitleTracks[i] = subtitleTracks[i].copy(isSelected = false)
            }
            onTracksChangedListener?.invoke()
            return true
        }
        val requestedIdx = if (trackId.startsWith("index:")) trackId.removePrefix("index:").toIntOrNull() else null
        var matched = false
        for (i in subtitleTracks.indices) {
            val item = subtitleTracks[i]
            val match = item.id == trackId || (requestedIdx != null && i == requestedIdx)
            if (match) {
                matched = true
                selectedSubtitleTrackId = item.id
            }
            subtitleTracks[i] = item.copy(isSelected = match)
        }
        if (matched) {
            onTracksChangedListener?.invoke()
        }
        return matched
    }

    fun getSubtitleCues(trackId: String? = selectedSubtitleTrackId): List<SubtitleCue> =
        if (trackId != null) subtitleCuesByTrackId[trackId].orEmpty() else emptyList()

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        attachedAnalyzer = analyzer
        if (analyzer != null && playing) {
            pushInitialWavWindowToAnalyzer()
        }
    }

    override fun setOnTimeUpdate(listener: ((Double) -> Unit)?) {
        onTimeUpdateListener = listener
    }

    override fun setOnDurationChanged(listener: ((Double) -> Unit)?) {
        onDurationChangedListener = listener
    }

    override fun setOnEnded(listener: (() -> Unit)?) {
        onEndedListener = listener
    }

    override fun setOnBufferingChanged(listener: ((Boolean) -> Unit)?) {
        onBufferingChangedListener = listener
    }

    override fun setOnError(listener: ((String) -> Unit)?) {
        onErrorListener = listener
    }

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
    }

    override fun getPreloadMode(): MediaPreloadMode = preloadMode

    override fun setAdaptivePreloadBufferingEnabled(enabled: Boolean) {
        adaptivePreloadBufferingEnabled = enabled
    }

    override fun isAdaptivePreloadBufferingEnabled(): Boolean = adaptivePreloadBufferingEnabled

    override fun getBufferedRanges(): List<Pair<Double, Double>> =
        if (durationSeconds > 0.0) listOf(0.0 to durationSeconds) else emptyList()

    override fun setAntialiasing(antialiasing: Boolean) {
        this.antialiasing = antialiasing
    }

    override fun isAntialiasing(): Boolean = antialiasing

    override fun setOnMediaReady(listener: (() -> Unit)?) {
        onMediaReadyListener = listener
    }

    override fun isMediaReady(): Boolean = mediaReady

    override fun getPresentedFrameCount(): Long = presentedFrameCounter.load()

    override fun setOnBeforeFramePresent(listener: ((frameIndex: Long, timestampMs: Double) -> Unit)?) {
        onBeforeFramePresentListener = listener
    }

    override fun setInternalSubtitlesEnabled(enabled: Boolean) {
        internalSubtitlesEnabled = enabled
    }

    override fun isInternalSubtitlesEnabled(): Boolean = internalSubtitlesEnabled

    override fun setKeepScreenOn(keepScreenOn: Boolean) {
        this.keepScreenOn = keepScreenOn
    }

    override fun isKeepScreenOn(): Boolean = keepScreenOn

    override fun bindScreenWakeToken(token: Long) {
        screenWakeToken = token
    }

    override fun enableOffscreenFrameCapture(enabled: Boolean) {
        offscreenFrameCaptureEnabled = enabled
        if (enabled && mediaReady && naturalWidth > 0 && naturalHeight > 0 && latestSlotIndex < 0) {
            renderAndPushFrame(currentTimeSeconds)
        }
    }

    override fun copyLatestVideoFrame(): ImageBuffer? {
        advanceFromClock()
        return withFrameLock {
            val slot = if (latestSlotIndex >= 0) frameSlots[latestSlotIndex] else null
            slot?.let {
                ImageBuffer(
                    width = it.width,
                    height = it.height,
                    pixelFormat = it.pixelFormat,
                    colorSpace = it.colorSpace,
                    bytes = it.bytes.copyOf()
                )
            }
        }
    }

    override val videoFrameVersion: Long
        get() = frameVersionCounter.load()

    override fun getNaturalVideoWidth(): Int = naturalWidth

    override fun getNaturalVideoHeight(): Int = naturalHeight

    override fun stepHeadlessPlaybackIfNeeded(deltaSeconds: Double) {
        if (!playing || deltaSeconds <= 0.0) return
        val wallElapsed = lastClockMark.elapsedNow().inWholeNanoseconds / 1_000_000_000.0
        if (wallElapsed >= 0.002) {
            advancePlaybackByDelta(wallElapsed)
        } else {
            advancePlaybackByDelta(deltaSeconds)
        }
        lastClockMark = TimeSource.Monotonic.markNow()
    }

    override fun dispose() {
        playing = false
        attachedAnalyzer = null
        clearFrameRing()
        onTimeUpdateListener = null
        onDurationChangedListener = null
        onEndedListener = null
        onBufferingChangedListener = null
        onErrorListener = null
        onPlayingStateChangedListener = null
        onBufferingStateChangedListener = null
        onBufferedRangesChangedListener = null
        onTracksChangedListener = null
        onMediaReadyListener = null
        onBeforeFramePresentListener = null
    }

    private fun advanceFromClock() {
        if (!playing) return
        val elapsedSec = lastClockMark.elapsedNow().inWholeNanoseconds / 1_000_000_000.0
        if (elapsedSec <= 0.0001) return
        lastClockMark = TimeSource.Monotonic.markNow()
        advancePlaybackByDelta(elapsedSec)
    }

    private fun advancePlaybackByDelta(deltaSeconds: Double) {
        if (!playing || deltaSeconds <= 0.0) return
        val next = currentTimeSeconds + deltaSeconds * playbackSpeed.toDouble()
        if (durationSeconds > 0.0 && next >= durationSeconds) {
            if (loopEnabled) {
                currentTimeSeconds = next % durationSeconds
                lastAnalyzerPushedSampleIndex = timeToSampleIndex(currentTimeSeconds)
                pushInitialWavWindowToAnalyzer()
                if (offscreenFrameCaptureEnabled && naturalWidth > 0 && naturalHeight > 0) {
                    renderAndPushFrame(currentTimeSeconds)
                }
                onTimeUpdateListener?.invoke(currentTimeSeconds)
            } else {
                currentTimeSeconds = durationSeconds
                playing = false
                pushWavRangeToAnalyzer(lastAnalyzerPushedSampleIndex, timeToSampleIndex(currentTimeSeconds))
                lastAnalyzerPushedSampleIndex = timeToSampleIndex(currentTimeSeconds)
                if (offscreenFrameCaptureEnabled && naturalWidth > 0 && naturalHeight > 0) {
                    renderAndPushFrame(currentTimeSeconds)
                }
                onTimeUpdateListener?.invoke(currentTimeSeconds)
                onPlayingStateChangedListener?.invoke(false)
                onEndedListener?.invoke()
            }
            return
        }
        currentTimeSeconds = next
        val nextSampleIndex = timeToSampleIndex(currentTimeSeconds)
        if (nextSampleIndex > lastAnalyzerPushedSampleIndex) {
            pushWavRangeToAnalyzer(lastAnalyzerPushedSampleIndex, nextSampleIndex)
            lastAnalyzerPushedSampleIndex = nextSampleIndex
        }
        if (offscreenFrameCaptureEnabled && naturalWidth > 0 && naturalHeight > 0) {
            renderAndPushFrame(currentTimeSeconds)
        }
        onTimeUpdateListener?.invoke(currentTimeSeconds)
    }

    private fun timeToSampleIndex(timeSec: Double): Int {
        val wav = wavData ?: return 0
        if (wav.monoSamples.isEmpty()) return 0
        return (timeSec * wav.sampleRateHz.toDouble()).toInt().coerceIn(0, wav.monoSamples.size)
    }

    private fun pushInitialWavWindowToAnalyzer() {
        val analyzer = attachedAnalyzer ?: return
        val wav = wavData ?: return
        if (wav.monoSamples.isEmpty()) return
        val center = timeToSampleIndex(currentTimeSeconds).coerceIn(0, wav.monoSamples.size - 1)
        val windowSize = analyzer.fftSize.coerceAtLeast(256).coerceAtMost(wav.monoSamples.size)
        val start = (center).coerceAtMost((wav.monoSamples.size - windowSize).coerceAtLeast(0))
        val end = (start + windowSize).coerceAtMost(wav.monoSamples.size)
        pushWavRangeToAnalyzer(start, end)
    }

    private fun pushWavRangeToAnalyzer(startIndex: Int, endIndex: Int) {
        val analyzer = attachedAnalyzer ?: return
        val wav = wavData ?: return
        val samples = wav.monoSamples
        if (samples.isEmpty()) return
        val clampedStart = startIndex.coerceIn(0, samples.size)
        val clampedEnd = endIndex.coerceIn(clampedStart, samples.size)
        val count = clampedEnd - clampedStart
        if (count <= 0) return

        val gain = if (muted) 0f else volume.coerceIn(0f, 2f)
        val chunk = FloatArray(count)
        for (i in 0 until count) {
            chunk[i] = (samples[clampedStart + i] * gain).coerceIn(-1f, 1f)
        }
        analyzer.pushPcmSamples(chunk, channels = 1)
    }

    private fun renderAndPushFrame(timeSec: Double) {
        val width = naturalWidth.coerceAtLeast(1)
        val height = naturalHeight.coerceAtLeast(1)
        val frameIndex = presentedFrameCounter.addAndFetch(1L)
        val progress = if (durationSeconds > 0.0) {
            (timeSec / durationSeconds).coerceIn(0.0, 1.0)
        } else {
            ((timeSec * 0.25) % 1.0).coerceIn(0.0, 1.0)
        }
        val phaseShift = ((timeSec * 48.0).roundToInt() + frameIndex.toInt() * 7) and 0xFF
        val markerX = ((progress * (width - 1).coerceAtLeast(1))).roundToInt().coerceIn(0, width - 1)
        val markerHalfWidth = (width / 24).coerceAtLeast(3)

        withFrameLock {
            val existing = frameSlots[ringWriteIndex]
            val slot = if (existing != null && existing.width == width && existing.height == height) {
                existing
            } else {
                ImageBuffer(
                    width = width,
                    height = height,
                    pixelFormat = PixelFormat.RGBA_8888,
                    colorSpace = ImageColorSpace.SRGB
                ).also { frameSlots[ringWriteIndex] = it }
            }
            val bytes = slot.bytes
            for (y in 0 until height) {
                val rowBase = (y * width) shl 2
                val yNorm = (y * 255) / height
                for (x in 0 until width) {
                    val idx = rowBase + (x shl 2)
                    val dx = abs(x - markerX)
                    if (dx <= markerHalfWidth) {
                        bytes[idx] = 240.toByte()
                        bytes[idx + 1] = 210.toByte()
                        bytes[idx + 2] = 64.toByte()
                    } else {
                        val band = ((x * 255) / width + phaseShift) and 0xFF
                        val r = ((band * 3 + yNorm) ushr 2).coerceIn(24, 245)
                        val g = ((255 - band + ((frameIndex.toInt() * 19) and 0xFF)) ushr 1).coerceIn(24, 235)
                        val b = ((yNorm + ((phaseShift * 2) and 0xFF)) ushr 1).coerceIn(32, 240)
                        bytes[idx] = r.toByte()
                        bytes[idx + 1] = g.toByte()
                        bytes[idx + 2] = b.toByte()
                    }
                    bytes[idx + 3] = 255.toByte()
                }
            }
            latestSlotIndex = ringWriteIndex
            ringWriteIndex = (ringWriteIndex + 1) % frameSlots.size
        }
        frameVersionCounter.addAndFetch(1L)
        onBeforeFramePresentListener?.invoke(frameIndex, (timeSec * 1000.0).coerceAtLeast(0.0))
    }

    private fun clearFrameRing() {
        withFrameLock {
            for (i in frameSlots.indices) {
                frameSlots[i] = null
            }
            ringWriteIndex = 0
            latestSlotIndex = -1
        }
    }

    private inline fun <T> withFrameLock(block: () -> T): T {
        while (!frameLock.compareAndSet(0, 1)) {
            // brief spin
        }
        return try {
            block()
        } finally {
            frameLock.store(0)
        }
    }
}
