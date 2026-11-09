@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.libvlc

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.component.media.MediaTrackInfo
import com.nexustools.genericui.component.media.SubtitleTrackInfo
import com.nexustools.genericui.desktop.core.DesktopBaseNativeImpl
import com.nexustools.genericui.desktop.core.DesktopInteractiveSurfaceSpec
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.CodecSupportLevel
import com.nexustools.genericui.enums.media.MediaContainer
import com.nexustools.genericui.enums.media.MediaPreloadMode
import com.nexustools.genericui.enums.media.VideoCodec
import com.nexustools.genericui.enums.media.VideoResizeMode
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import platform.posix.memset
import kotlin.math.abs

/**
 * Shared desktop [VideoPlayerNativeImpl] backed directly by native `libvlc` ([LibVlcBindings]),
 * shared by `:extensions:media:desktop:qt`, `:extensions:media:desktop:gtk`,
 * `:extensions:media:desktop:windows`, and `:extensions:media:desktop:macos`.
 */
open class DesktopLibVlcVideoPlayerImpl<out W : DesktopNativeWidget>(
    nativeWidget: W,
    val platformName: String = "Desktop",
    private val libVlcArgs: List<String> = listOf(
        "--no-video-title-show",
        "--no-sub-autodetect-file",
        "--no-osd",
        "--no-stats",
        "--avcodec-hw=any",
        "--avcodec-fast",
        "--http-reconnect",
        "--network-caching=3000",
        "--file-caching=60",
        "--quiet"
    )
) : DesktopBaseNativeImpl<W>(nativeWidget, "libvlc-video-player"), VideoPlayerNativeImpl<W> {

    private var initializedVlcInstance: COpaquePointer? = null
    private var initializedVlcMediaPlayer: COpaquePointer? = null
    private var vlcInitAttempted: Boolean = false
    private var pendingWin32Hwnd: Long = 0L
    private var pendingMacosNsView: Long = 0L
    private var pendingXWindowId: UInt = 0u

    val videoFrameWidth: Int = 1920
    val videoFrameHeight: Int = 1080
    val videoFrameBytes: Int = videoFrameWidth * videoFrameHeight * 4
    private val videoFrameBuffer = ByteArray(videoFrameBytes)

    private var antialiasingEnabled: Boolean = true
    private var mediaReadyFlag: Boolean = false
    private var onMediaReadyListener: (() -> Unit)? = null
    private var onBeforeFramePresentListener: ((Long, Double) -> Unit)? = null

    // 16-slot native heap BGRA ring-pool for lock/unlock/display PTS synchronization (supports B-frame reordering)
    private val frameSlotCount: Int = 16
    private val frameSlots = arrayOfNulls<CPointer<ByteVar>>(frameSlotCount)
    private val slotState = IntArray(frameSlotCount)
    private val slotDecodeSeq = LongArray(frameSlotCount)
    private val slotFrameSeq = LongArray(frameSlotCount)
    private val bufferPoolLock = kotlin.concurrent.AtomicInt(0)
    private var decodeCounter: Long = 0L
    private var latestDisplayedSlot: Int = -1
    private var currentReadSlot: Int = 0
    private var lastManualLockSlot: Int = 0

    @kotlin.concurrent.Volatile
    internal var hasNewFrameFlag: Int = 0

    private var selfStableRef: StableRef<DesktopLibVlcVideoPlayerImpl<*>>? = null
    internal var frameSwapCounter: Long = 0L
    private var lastVBlankDispatchedSwapCount: Long = 0L
    private var isPrerollingFirstFrame: Boolean = false
    private var prerollTargetSwapCount: Long = 0L
    private var preloadModeState: MediaPreloadMode = MediaPreloadMode.AUTO
    private var adaptivePreloadBufferingEnabledState: Boolean = true
    private var vblankTickCounter: Int = 0

    private var currentSourceUrl: String = ""
    private var endedFiredForCurrentMedia: Boolean = false
    private var lastReportedTimeSec: Double = -1.0
    private var lastReportedDurationSec: Double = -1.0
    private var lastNotifiedBufferedEndSec: Double = -1.0
    private var cachedPlayingState: Boolean = false
    private var cachedBufferingState: Boolean = false
    private var lastVlcPollMark: kotlin.time.TimeSource.Monotonic.ValueTimeMark? = null
    private var cachedIsPlaying: Boolean = false
    private var cachedTimeSec: Double = 0.0
    private var cachedDurationSec: Double = 0.0
    private var vblankFrameCounter: Long = 0L
    private var mediaLoadVBlankFrame: Long = 0L
    private var streamReconnectAttempts: Int = 0
    private var lastReconnectAttemptFrame: Long = -1000L
    private var lastProgressResetTimeSec: Double = 0.0

    private var pendingAudioTrackSelection: String? = null
    private var pendingSubtitleTrackSelection: String? = null
    private var disableSpuByDefault: Boolean = false
    private var spuDisabledAppliedForMedia: Boolean = false

    protected val vlcInstance: COpaquePointer?
        get() = initializedVlcInstance

    protected val vlcMediaPlayer: COpaquePointer?
        get() = initializedVlcMediaPlayer

    protected var currentVlcMedia: COpaquePointer? = null

    init {
        DesktopInteractiveSurfaceSpec(
            surfaceWidth = { videoFrameWidth.coerceAtLeast(1) },
            surfaceHeight = { videoFrameHeight.coerceAtLeast(1) },
            swapCount = { frameSwapCounter },
            acquireBgraPtr = { acquireReadFrameBgraPtr() },
            captureBgra = { getLatestVideoFrameBgra() },
            captureRgba = { getLatestVideoFrameRgba() },
            resizeSurface = { w, h ->
                if (w > 0 && h > 0 && (w != videoFrameWidth || h != videoFrameHeight) && frameSwapCounter == 0L) {
                    // keep decoded resolution when active, or update initial size
                }
            },
            preferHardwareStretchBlit = true,
            suppressDefaultBorder = true,
            defaultAspectRatio = 16f / 9f,
            isActiveStream = { isPlaying() },
            activeCaptionText = { (nativeWidget.attributes["activeSubtitleCueText"] as? String)?.trim()?.takeIf { it.isNotEmpty() } },
            onBeforePresentFrame = { sc, ts ->
                onBeforeFramePresentListener?.invoke(sc, ts ?: (cachedTimeSec * 1000.0).coerceAtLeast(0.0))
            }
        ).attachTo(nativeWidget)
        nativeWidget.attributes["vblankDispatch"] = { _: Double? -> tickVBlank() }
        nativeWidget.attributes["antialiasing"] = true
    }

    private inline fun <T> withBufferPoolLock(block: () -> T): T {
        while (!bufferPoolLock.compareAndSet(0, 1)) {
            // spin briefly
        }
        return try {
            block()
        } finally {
            bufferPoolLock.value = 0
        }
    }

    private fun ensureFramePoolAllocated() {
        if (frameSlots[0] == null) {
            for (i in 0 until frameSlotCount) {
                val buf = nativeHeap.allocArray<ByteVar>(videoFrameBytes)
                memset(buf, 0, videoFrameBytes.toULong())
                frameSlots[i] = buf
                slotState[i] = SLOT_FREE
                slotDecodeSeq[i] = 0L
                slotFrameSeq[i] = 0L
            }
            decodeCounter = 0L
            latestDisplayedSlot = -1
            currentReadSlot = 0
            lastManualLockSlot = 0
        }
    }

    fun acquireReadFrameBgraPtr(): Pair<COpaquePointer?, Int> {
        ensureFramePoolAllocated()
        val ptr = withBufferPoolLock {
            val displayed = latestDisplayedSlot
            if (displayed in 0 until frameSlotCount) {
                val prevRead = currentReadSlot
                if (prevRead != displayed && prevRead in 0 until frameSlotCount && slotState[prevRead] == SLOT_DISPLAYED) {
                    slotState[prevRead] = SLOT_FREE
                }
                currentReadSlot = displayed
            }
            frameSlots[currentReadSlot]?.reinterpret<ByteVar>() as COpaquePointer?
        }
        return Pair(ptr, videoFrameBytes)
    }

    fun getLatestVideoFrameBgra(): ByteArray {
        val (ptr, len) = acquireReadFrameBgraPtr()
        if (ptr != null && len == videoFrameBuffer.size) {
            videoFrameBuffer.usePinned { pinned ->
                memcpy(pinned.addressOf(0), ptr, len.toULong())
            }
        }
        return videoFrameBuffer
    }

    fun getLatestVideoFrameRgba(): ByteArray {
        val bgra = getLatestVideoFrameBgra()
        val rgba = ByteArray(bgra.size)
        var i = 0
        while (i + 3 < bgra.size) {
            rgba[i] = bgra[i + 2]
            rgba[i + 1] = bgra[i + 1]
            rgba[i + 2] = bgra[i]
            rgba[i + 3] = bgra[i + 3]
            i += 4
        }
        return rgba
    }

    internal fun lockVideoFrameBuffer(): COpaquePointer? {
        ensureFramePoolAllocated()
        return withBufferPoolLock {
            var chosen = -1
            var oldestFrameSeq = Long.MAX_VALUE
            for (i in 0 until frameSlotCount) {
                if (slotState[i] == SLOT_FREE && i != latestDisplayedSlot && i != currentReadSlot) {
                    if (slotFrameSeq[i] < oldestFrameSeq) {
                        oldestFrameSeq = slotFrameSeq[i]
                        chosen = i
                    }
                }
            }
            if (chosen < 0) {
                // Only if all 16 slots are exhausted, recycle the oldest pending slot (never currentReadSlot or latestDisplayedSlot)
                var oldestDecodeSeq = Long.MAX_VALUE
                for (i in 0 until frameSlotCount) {
                    if (slotState[i] == SLOT_DECODED_PENDING_DISPLAY && i != latestDisplayedSlot && i != currentReadSlot) {
                        if (slotDecodeSeq[i] < oldestDecodeSeq) {
                            oldestDecodeSeq = slotDecodeSeq[i]
                            chosen = i
                        }
                    }
                }
            }
            if (chosen < 0) {
                for (i in 0 until frameSlotCount) {
                    if (i != currentReadSlot && i != latestDisplayedSlot) {
                        chosen = i
                        break
                    }
                }
            }
            if (chosen < 0) {
                chosen = (currentReadSlot + 1) % frameSlotCount
            }
            decodeCounter += 1L
            slotState[chosen] = SLOT_LOCKED_DECODING
            slotDecodeSeq[chosen] = decodeCounter
            lastManualLockSlot = chosen
            frameSlots[chosen]?.reinterpret()
        }
    }

    private fun resolveSlotIndexByPtr(picture: COpaquePointer?): Int {
        if (picture != null) {
            val targetPtr: CPointer<ByteVar> = picture.reinterpret()
            for (i in 0 until frameSlotCount) {
                val slotPtr = frameSlots[i]
                if (slotPtr != null && slotPtr == targetPtr) {
                    return i
                }
            }
        }
        return lastManualLockSlot
    }

    internal fun onVlcFrameUnlocked(picture: COpaquePointer?) {
        withBufferPoolLock {
            val idx = resolveSlotIndexByPtr(picture)
            if (idx in 0 until frameSlotCount && slotState[idx] == SLOT_LOCKED_DECODING) {
                slotState[idx] = SLOT_DECODED_PENDING_DISPLAY
            }
        }
    }

    internal fun onVlcFrameDisplayed(picture: COpaquePointer?) {
        var newSwapCount = 0L
        var updatedFrame = false
        withBufferPoolLock {
            val idx = resolveSlotIndexByPtr(picture)
            if (idx in 0 until frameSlotCount) {
                // Do NOT free other SLOT_DECODED_PENDING_DISPLAY slots here:
                // H.264/HEVC B-frame reordering decodes future P-frames (lower decodeSeq) BEFORE B-frames (higher decodeSeq)
                // and holds those P-frames in VLC's picture FIFO until their PTS arrives!
                val prevDisplayed = latestDisplayedSlot
                if (prevDisplayed in 0 until frameSlotCount && prevDisplayed != idx && prevDisplayed != currentReadSlot) {
                    if (slotState[prevDisplayed] == SLOT_DISPLAYED) {
                        slotState[prevDisplayed] = SLOT_FREE
                    }
                }
                slotState[idx] = SLOT_DISPLAYED
                frameSwapCounter += 1L
                newSwapCount = frameSwapCounter
                slotFrameSeq[idx] = frameSwapCounter
                latestDisplayedSlot = idx
                hasNewFrameFlag = 1
                updatedFrame = true
            }
        }
        if (updatedFrame) {
            if (isPrerollingFirstFrame && newSwapCount >= prerollTargetSwapCount) {
                isPrerollingFirstFrame = false
                val player = vlcMediaPlayer
                if (!playingFallback && player != null) {
                    LibVlcBindings.libvlc_media_player_set_pause(player, 1)
                    LibVlcBindings.libvlc_audio_set_mute(player, if (mutedState) 1 else 0)
                    LibVlcBindings.libvlc_audio_set_volume(player, (volumeLevel * 100f).toInt())
                }
            }
            nativeWidget.attributes["vblankDirtied"] = true
            if (!mediaReadyFlag) {
                mediaReadyFlag = true
                onMediaReadyListener?.invoke()
            }
        }
    }

    internal fun onVlcFrameDecoded() {
        onVlcFrameDisplayed(null)
    }

    private fun ensureVlcPlayer(): COpaquePointer? {
        if (!vlcInitAttempted) {
            vlcInitAttempted = true
            val inst = LibVlcBindings.libvlc_new(libVlcArgs)
            initializedVlcInstance = inst
            val player = LibVlcBindings.libvlc_media_player_new(inst)
            initializedVlcMediaPlayer = player
            if (player != null) {
                if (pendingWin32Hwnd != 0L) {
                    LibVlcBindings.libvlc_media_player_set_hwnd(player, pendingWin32Hwnd.toCPointer())
                } else if (pendingMacosNsView != 0L) {
                    LibVlcBindings.libvlc_media_player_set_nsobject(player, pendingMacosNsView.toCPointer())
                } else if (pendingXWindowId != 0u) {
                    LibVlcBindings.libvlc_media_player_set_xwindow(player, pendingXWindowId)
                } else {
                    ensureFramePoolAllocated()
                    if (selfStableRef == null) {
                        selfStableRef = StableRef.create(this)
                    }
                    LibVlcBindings.libvlc_video_set_format(
                        player = player,
                        chroma = "RV32",
                        width = videoFrameWidth,
                        height = videoFrameHeight,
                        pitch = videoFrameWidth * 4
                    )
                    LibVlcBindings.libvlc_video_set_callbacks(
                        player = player,
                        lockCb = vlcVideoLockCallback.reinterpret(),
                        unlockCb = vlcVideoUnlockCallback.reinterpret(),
                        displayCb = vlcVideoDisplayCallback.reinterpret(),
                        opaque = selfStableRef?.asCPointer()
                    )
                }
            }
        }
        return initializedVlcMediaPlayer
    }

    private var currentSecFallback: Double = 0.0
    private var durationSecOverride: Double? = null
    private var playingFallback: Boolean = false
    private var playbackRate: Float = 1.0f
    private var volumeLevel: Float = 1.0f
    private var mutedState: Boolean = false
    private var resizeModeState: VideoResizeMode = VideoResizeMode.FIT
    private val networkCachingBufferSec: Double = 15.0
    private var bufferedRangesState: List<Pair<Double, Double>> = emptyList()

    private val audioTracksList = mutableListOf<MediaTrackInfo>()
    private val subtitleTracksList = mutableListOf<SubtitleTrackInfo>()
    private var attachedAnalyzer: AudioAnalyzer? = null

    private var onTimeUpdateListener: ((Double) -> Unit)? = null
    private var onDurationChangedListener: ((Double) -> Unit)? = null
    private var onEndedListener: ((() -> Unit))? = null
    private var onBufferingChangedListener: ((Boolean) -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    private var onPlayingStateChangedListener: ((Boolean) -> Unit)? = null
    private var onBufferingStateChangedListener: ((Boolean) -> Unit)? = null
    private var onBufferedRangesChangedListener: ((List<Pair<Double, Double>>) -> Unit)? = null
    private var onTracksChangedListener: (() -> Unit)? = null

    /**
     * Binds a Win32 `HWND` window handle to the underlying `libvlc_media_player_t*` (`libvlc_media_player_set_hwnd`).
     */
    fun bindWin32Hwnd(hwndAddress: Long) {
        pendingWin32Hwnd = hwndAddress
        val player = vlcMediaPlayer
        if (hwndAddress != 0L && player != null) {
            LibVlcBindings.libvlc_media_player_set_hwnd(player, hwndAddress.toCPointer())
        }
    }

    /**
     * Binds a macOS `NSView*` / `VLCVideoView*` pointer to the underlying `libvlc_media_player_t*` (`libvlc_media_player_set_nsobject`).
     */
    fun bindMacosNsView(nsViewAddress: Long) {
        pendingMacosNsView = nsViewAddress
        val player = vlcMediaPlayer
        if (nsViewAddress != 0L && player != null) {
            LibVlcBindings.libvlc_media_player_set_nsobject(player, nsViewAddress.toCPointer())
        }
    }

    /**
     * Binds an X11 Window drawable ID to the underlying `libvlc_media_player_t*` (`libvlc_media_player_set_xwindow`).
     */
    fun bindXWindow(xWindowId: UInt) {
        pendingXWindowId = xWindowId
        val player = vlcMediaPlayer
        if (xWindowId != 0u && player != null) {
            LibVlcBindings.libvlc_media_player_set_xwindow(player, xWindowId)
        }
    }

    override fun getMediaCapabilities(): MediaCapabilitiesReport = MediaCapabilitiesReport(
        videoCodecs = VideoCodec.entries.associateWith { CodecSupportLevel.HARDWARE_ACCELERATED },
        audioCodecs = AudioCodec.entries.associateWith { CodecSupportLevel.HARDWARE_ACCELERATED },
        containers = MediaContainer.entries.toSet(),
        supportsHdr10 = true,
        supportsDolbyVision = true,
        supportsHlg = true,
        supportsSurround51 = true,
        supportsSurround71 = true,
        supportsAudioTrackSelection = true,
        supportsSubtitleTrackSelection = true,
        hardwareAccelerationBackend = "libvlc [$platformName]"
    )

    override fun isVideoCodecSupported(codec: VideoCodec): Boolean = true

    override fun isAudioCodecSupported(codec: AudioCodec): Boolean = true

    override fun getSupportedVideoCodecs(): Set<VideoCodec> = VideoCodec.entries.toSet()

    override fun getSupportedAudioCodecs(): Set<AudioCodec> = AudioCodec.entries.toSet()

    override fun supportsAudioTrackSelection(): Boolean = true

    override fun supportsSubtitleTrackSelection(): Boolean = true

    override fun setSource(url: String, seekOffsetSeconds: Double, durationOverrideSeconds: Double?) {
        if (url.isNotBlank() && url == currentSourceUrl && currentVlcMedia != null && vlcMediaPlayer != null &&
            abs(seekOffsetSeconds - currentSecFallback) < 1.0
        ) {
            durationSecOverride = durationOverrideSeconds
            if (durationOverrideSeconds != null && durationOverrideSeconds > 0.0) {
                cachedDurationSec = durationOverrideSeconds
                lastReportedDurationSec = durationOverrideSeconds
                onDurationChangedListener?.invoke(durationOverrideSeconds)
            }
            return
        }
        currentSourceUrl = url
        mediaReadyFlag = false
        isPrerollingFirstFrame = false
        prerollTargetSwapCount = 0L
        lastVBlankDispatchedSwapCount = 0L
        withBufferPoolLock {
            latestDisplayedSlot = -1
            hasNewFrameFlag = 0
            frameSwapCounter = 0L
            for (i in 0 until frameSlotCount) {
                if (slotState[i] == SLOT_DISPLAYED || slotState[i] == SLOT_DECODED_PENDING_DISPLAY) {
                    slotState[i] = SLOT_FREE
                }
            }
        }
        endedFiredForCurrentMedia = false
        spuDisabledAppliedForMedia = false
        audioTracksList.clear()
        subtitleTracksList.clear()
        lastReportedTimeSec = -1.0
        lastReportedDurationSec = -1.0
        lastNotifiedBufferedEndSec = -1.0
        lastVlcPollMark = null
        nativeWidget.attributes["videoSource"] = url
        nativeWidget.attributes["src"] = url
        currentSecFallback = seekOffsetSeconds.coerceAtLeast(0.0)
        cachedTimeSec = currentSecFallback
        lastProgressResetTimeSec = currentSecFallback
        streamReconnectAttempts = 0
        durationSecOverride = durationOverrideSeconds
        cachedDurationSec = durationOverrideSeconds ?: 0.0

        if (url.isNotBlank()) {
            val player = ensureVlcPlayer()
            loadMediaIntoPlayer(player, url, seekOffsetSeconds)
            if (LibVlcBindings.isAvailable && player != null) {
                if (!playingFallback && preloadModeState != MediaPreloadMode.NONE) {
                    isPrerollingFirstFrame = true
                    prerollTargetSwapCount = 1L
                    LibVlcBindings.libvlc_audio_set_mute(player, 1)
                    LibVlcBindings.libvlc_media_player_play(player)
                    cachedPlayingState = false
                    cachedIsPlaying = false
                    nativeWidget.attributes["playing"] = false
                    nativeWidget.attributes["isPlaying"] = false
                }
            } else {
                mediaReadyFlag = true
                onMediaReadyListener?.invoke()
            }
        }

        if (durationOverrideSeconds != null) {
            onDurationChangedListener?.invoke(durationOverrideSeconds)
        }
        updateAndNotifyBufferedHorizon(currentSecFallback)
    }

    private fun loadMediaIntoPlayer(player: COpaquePointer?, resolvedUri: String, startSec: Double) {
        mediaLoadVBlankFrame = vblankFrameCounter
        LibVlcBindings.libvlc_media_release(currentVlcMedia)
        val isRemoteOrUri = resolvedUri.contains("://")
        val media = if (isRemoteOrUri) {
            LibVlcBindings.libvlc_media_new_location(vlcInstance, resolvedUri)
        } else {
            val strippedUri = if (
                resolvedUri.length >= 3 &&
                (resolvedUri[0] == '/' || resolvedUri[0] == '\\') &&
                resolvedUri[2] == ':'
            ) {
                resolvedUri.substring(1)
            } else {
                resolvedUri
            }
            val normalizedPath = if (
                platformName.contains("Windows", ignoreCase = true) ||
                (strippedUri.length >= 2 && strippedUri[1] == ':')
            ) {
                strippedUri.replace('/', '\\')
            } else {
                strippedUri
            }
            LibVlcBindings.libvlc_media_new_path(vlcInstance, normalizedPath)
        }
        if (resolvedUri.startsWith("http://", ignoreCase = true) || resolvedUri.startsWith("https://", ignoreCase = true)) {
            LibVlcBindings.libvlc_media_add_option(media, ":http-reconnect")
            LibVlcBindings.libvlc_media_add_option(media, ":network-caching=3000")
        }
        if (startSec > 0.0) {
            LibVlcBindings.libvlc_media_add_option(media, ":start-time=$startSec")
        }
        if (disableSpuByDefault) {
            LibVlcBindings.libvlc_media_add_option(media, ":no-spu")
            spuDisabledAppliedForMedia = true
        }
        currentVlcMedia = media
        LibVlcBindings.libvlc_media_player_set_media(player, media)
    }

    override fun play() {
        endedFiredForCurrentMedia = false
        lastReportedTimeSec = -1.0
        lastReportedDurationSec = -1.0
        val wasPrerolling = isPrerollingFirstFrame
        isPrerollingFirstFrame = false
        playingFallback = true
        cachedPlayingState = true
        cachedIsPlaying = true
        val player = vlcMediaPlayer ?: if (currentSourceUrl.isNotBlank()) ensureVlcPlayer() else null
        if (player != null) {
            if (wasPrerolling || LibVlcBindings.isAvailable) {
                LibVlcBindings.libvlc_audio_set_mute(player, if (mutedState) 1 else 0)
                LibVlcBindings.libvlc_audio_set_volume(player, (volumeLevel * 100f).toInt())
            }
            val state = if (LibVlcBindings.isAvailable) LibVlcBindings.libvlc_media_player_get_state(player) else -1
            if (state == 4 || (wasPrerolling && state == 3)) {
                LibVlcBindings.libvlc_media_player_set_pause(player, 0)
            } else if (!wasPrerolling || state !in 0..3) {
                val res = LibVlcBindings.libvlc_media_player_play(player)
                if (res < 0 && LibVlcBindings.isAvailable) {
                    playingFallback = false
                    cachedPlayingState = false
                    cachedIsPlaying = false
                    nativeWidget.attributes["playing"] = false
                    nativeWidget.attributes["isPlaying"] = false
                    nativeWidget.notifyTreeInvalidated(visualOnly = true)
                    onPlayingStateChangedListener?.invoke(false)
                    onErrorListener?.invoke(LibVlcBindings.libvlc_errmsg() ?: "libvlc_media_player_play failed")
                    return
                }
            }
        }
        nativeWidget.attributes["playing"] = true
        nativeWidget.attributes["isPlaying"] = true
        nativeWidget.notifyTreeInvalidated(visualOnly = true)
        onPlayingStateChangedListener?.invoke(true)
    }

    override fun pause() {
        playingFallback = false
        cachedPlayingState = false
        cachedIsPlaying = false
        if (!isPrerollingFirstFrame) {
            LibVlcBindings.libvlc_media_player_set_pause(vlcMediaPlayer, 1)
        }
        nativeWidget.attributes["playing"] = false
        nativeWidget.attributes["isPlaying"] = false
        nativeWidget.notifyTreeInvalidated(visualOnly = true)
        onPlayingStateChangedListener?.invoke(false)
    }

    override fun stop() {
        isPrerollingFirstFrame = false
        playingFallback = false
        cachedPlayingState = false
        cachedIsPlaying = false
        currentSecFallback = 0.0
        cachedTimeSec = 0.0
        vlcMediaPlayer?.let { player ->
            LibVlcBindings.libvlc_audio_set_mute(player, if (mutedState) 1 else 0)
            LibVlcBindings.libvlc_media_player_stop(player)
        }
        nativeWidget.attributes["playing"] = false
        nativeWidget.attributes["isPlaying"] = false
        nativeWidget.notifyTreeInvalidated(visualOnly = true)
        onPlayingStateChangedListener?.invoke(false)
        onTimeUpdateListener?.invoke(0.0)
    }

    override fun seekTo(seconds: Double) {
        val clamped = seconds.coerceAtLeast(0.0)
        currentSecFallback = clamped
        cachedTimeSec = clamped
        lastProgressResetTimeSec = clamped
        val targetMs = (clamped * 1000.0).toLong()
        val player = vlcMediaPlayer ?: if (currentSourceUrl.isNotBlank()) ensureVlcPlayer() else null
        if (player != null && LibVlcBindings.isAvailable) {
            val state = LibVlcBindings.libvlc_media_player_get_state(player)
            val isTranscodeStream = currentSourceUrl.contains("mode=transcode", ignoreCase = true) ||
                currentSourceUrl.contains("?start=") ||
                currentSourceUrl.contains("&start=")
            if (!isTranscodeStream && state in 5..7 && currentSourceUrl.isNotBlank()) {
                endedFiredForCurrentMedia = false
                loadMediaIntoPlayer(player, currentSourceUrl, clamped)
                if (playingFallback) {
                    LibVlcBindings.libvlc_audio_set_mute(player, if (mutedState) 1 else 0)
                    LibVlcBindings.libvlc_audio_set_volume(player, (volumeLevel * 100f).toInt())
                    LibVlcBindings.libvlc_media_player_play(player)
                } else {
                    isPrerollingFirstFrame = true
                    prerollTargetSwapCount = frameSwapCounter + 1L
                    LibVlcBindings.libvlc_audio_set_mute(player, 1)
                    LibVlcBindings.libvlc_media_player_play(player)
                }
            } else if (!playingFallback) {
                if (state in 0..1 || state in 5..7) {
                    isPrerollingFirstFrame = true
                    prerollTargetSwapCount = frameSwapCounter + 1L
                    LibVlcBindings.libvlc_audio_set_mute(player, 1)
                    LibVlcBindings.libvlc_media_player_play(player)
                    LibVlcBindings.libvlc_media_player_set_time(player, targetMs)
                } else {
                    if (isPrerollingFirstFrame) {
                        prerollTargetSwapCount = frameSwapCounter + 1L
                    }
                    LibVlcBindings.libvlc_media_player_set_time(player, targetMs)
                }
            } else {
                LibVlcBindings.libvlc_media_player_set_time(player, targetMs)
            }
        } else {
            LibVlcBindings.libvlc_media_player_set_time(vlcMediaPlayer, targetMs)
        }
        onTimeUpdateListener?.invoke(clamped)
        updateAndNotifyBufferedHorizon(clamped)
    }

    override fun getCurrentTimeSeconds(): Double {
        val player = vlcMediaPlayer
        if (player != null && LibVlcBindings.isAvailable) {
            if (lastVlcPollMark == null) {
                val vlcMs = LibVlcBindings.libvlc_media_player_get_time(player)
                if (vlcMs >= 0L) {
                    currentSecFallback = vlcMs / 1000.0
                    cachedTimeSec = currentSecFallback
                }
            }
            return cachedTimeSec
        }
        return currentSecFallback
    }

    override fun getDurationSeconds(): Double {
        durationSecOverride?.let { return it }
        if (cachedDurationSec > 0.0) return cachedDurationSec
        val player = vlcMediaPlayer
        if (player != null && LibVlcBindings.isAvailable && lastVlcPollMark == null) {
            val vlcLenMs = LibVlcBindings.libvlc_media_player_get_length(player)
            if (vlcLenMs > 0L) {
                cachedDurationSec = vlcLenMs / 1000.0
                return cachedDurationSec
            }
        }
        return if (lastReportedDurationSec > 0.0) lastReportedDurationSec else 0.0
    }

    override fun isPlaying(): Boolean {
        val player = vlcMediaPlayer
        return if (player != null && LibVlcBindings.isAvailable) {
            cachedIsPlaying
        } else {
            playingFallback
        }
    }

    fun tickVBlank() {
        vblankTickCounter += 1
        vblankFrameCounter += 1L
        val player = vlcMediaPlayer
        val vlcLive = player != null && LibVlcBindings.isAvailable
        val swapCount = frameSwapCounter

        if (swapCount != lastVBlankDispatchedSwapCount || hasNewFrameFlag != 0) {
            lastVBlankDispatchedSwapCount = swapCount
            hasNewFrameFlag = 0
            if (isPrerollingFirstFrame && swapCount >= prerollTargetSwapCount) {
                isPrerollingFirstFrame = false
                if (!playingFallback && player != null) {
                    LibVlcBindings.libvlc_media_player_set_pause(player, 1)
                    LibVlcBindings.libvlc_audio_set_mute(player, if (mutedState) 1 else 0)
                    LibVlcBindings.libvlc_audio_set_volume(player, (volumeLevel * 100f).toInt())
                }
            }
            nativeWidget.attributes["vblankDirtied"] = true
            val tsMs = (cachedTimeSec * 1000.0).coerceAtLeast(0.0)
            onBeforeFramePresentListener?.invoke(swapCount, tsMs)
            nativeWidget.interactiveSurfaceSpec?.dispatchBeforePresentFrame(swapCount, tsMs)
            if (!mediaReadyFlag) {
                mediaReadyFlag = true
                onMediaReadyListener?.invoke()
            }
        }

        if (!vlcLive) {
            if (playingFallback) {
                currentSecFallback += 0.016
                cachedTimeSec = currentSecFallback
                cachedIsPlaying = true
                nativeWidget.attributes["playing"] = true
                nativeWidget.attributes["isPlaying"] = true
                if (abs(currentSecFallback - lastReportedTimeSec) >= 0.25) {
                    lastReportedTimeSec = currentSecFallback
                    onTimeUpdateListener?.invoke(currentSecFallback)
                    updateAndNotifyBufferedHorizon(currentSecFallback)
                    nativeWidget.attributes["vblankDirtied"] = true
                }
            }
            return
        }

        // Throttle native LibVLC time/duration/state/track queries to avoid UI-thread lock contention
        val pollIntervalMs = if (mediaReadyFlag && cachedDurationSec > 0.0) 350L else 150L
        val prevPoll = lastVlcPollMark
        if (prevPoll != null && prevPoll.elapsedNow().inWholeMilliseconds < pollIntervalMs) {
            return
        }
        lastVlcPollMark = kotlin.time.TimeSource.Monotonic.markNow()

        val state = LibVlcBindings.libvlc_media_player_get_state(player)
        val isBuffering = !isPrerollingFirstFrame && playingFallback && (state == 1 || state == 2)
        if (isBuffering != cachedBufferingState) {
            cachedBufferingState = isBuffering
            onBufferingChangedListener?.invoke(isBuffering)
            onBufferingStateChangedListener?.invoke(isBuffering)
        }
        if (!playingFallback && !isPrerollingFirstFrame && state == 3 && swapCount > 0L) {
            LibVlcBindings.libvlc_media_player_set_pause(player, 1)
            LibVlcBindings.libvlc_audio_set_mute(player, if (mutedState) 1 else 0)
            LibVlcBindings.libvlc_audio_set_volume(player, (volumeLevel * 100f).toInt())
        }
        val playing = if (isPrerollingFirstFrame || !playingFallback) {
            false
        } else {
            when {
                state == 3 -> true
                state in 4..7 -> false
                playingFallback && state in 0..2 -> true
                else -> LibVlcBindings.libvlc_media_player_is_playing(player)
            }
        }
        cachedPlayingState = playing
        cachedIsPlaying = playing
        nativeWidget.attributes["playing"] = playing
        nativeWidget.attributes["isPlaying"] = playing

        if (playing || player != null) {
            val withinTrackDiscoveryWindow =
                audioTracksList.isEmpty() || (vblankFrameCounter - mediaLoadVBlankFrame) <= 300L
            if (pendingAudioTrackSelection != null && player != null && withinTrackDiscoveryWindow) {
                applyPendingAudioTrackIfReady(player)
            }
            if (disableSpuByDefault && !spuDisabledAppliedForMedia && state == 3 && player != null) {
                LibVlcBindings.libvlc_video_set_spu(player, -1)
                spuDisabledAppliedForMedia = true
            } else if (pendingSubtitleTrackSelection != null && player != null && withinTrackDiscoveryWindow) {
                applyPendingSubtitleTrackIfReady(player)
            }

            if (durationSecOverride == null && cachedDurationSec <= 0.0) {
                val vlcLenMs = LibVlcBindings.libvlc_media_player_get_length(player)
                if (vlcLenMs > 0L) {
                    cachedDurationSec = vlcLenMs / 1000.0
                }
            }

            if (lastReportedDurationSec <= 0.0) {
                val durSec = durationSecOverride ?: cachedDurationSec
                if (durSec > 0.0) {
                    lastReportedDurationSec = durSec
                    onDurationChangedListener?.invoke(durSec)
                }
            }
            val usesNativeWindowHandle = pendingWin32Hwnd != 0L || pendingMacosNsView != 0L || pendingXWindowId != 0u
            if (state == 3 && !mediaReadyFlag && !isPrerollingFirstFrame && (usesNativeWindowHandle || frameSwapCounter > 0L)) {
                mediaReadyFlag = true
                onMediaReadyListener?.invoke()
            }
            if (playing) {
                val vlcMs = LibVlcBindings.libvlc_media_player_get_time(player)
                if (vlcMs >= 0L) {
                    currentSecFallback = vlcMs / 1000.0
                    cachedTimeSec = currentSecFallback
                }
                if (state == 3 && cachedTimeSec - lastProgressResetTimeSec >= 2.0) {
                    streamReconnectAttempts = 0
                    lastProgressResetTimeSec = cachedTimeSec
                }
                val curSec = cachedTimeSec
                if (abs(curSec - lastReportedTimeSec) >= 0.5) {
                    lastReportedTimeSec = curSec
                    onTimeUpdateListener?.invoke(curSec)
                    updateAndNotifyBufferedHorizon(curSec)
                }
            }
            if ((state == 6 || state == 7) && (playingFallback || !endedFiredForCurrentMedia)) {
                val isHttpStream = currentSourceUrl.startsWith("http://", ignoreCase = true) ||
                    currentSourceUrl.startsWith("https://", ignoreCase = true)
                val effectiveDur = if (durationSecOverride != null && durationSecOverride!! > 0.0) {
                    durationSecOverride!!
                } else {
                    cachedDurationSec
                }
                val isPrematureDrop = isHttpStream && (
                    state == 7 || (
                        effectiveDur > 30.0 &&
                            cachedTimeSec > 1.0 &&
                            cachedTimeSec < effectiveDur - 15.0 &&
                            (cachedTimeSec / effectiveDur) < 0.90
                        )
                    )
                if (isPrematureDrop && streamReconnectAttempts < 3 && (vblankFrameCounter - lastReconnectAttemptFrame) >= 120L) {
                    streamReconnectAttempts += 1
                    lastReconnectAttemptFrame = vblankFrameCounter
                    endedFiredForCurrentMedia = false
                    loadMediaIntoPlayer(player, currentSourceUrl, cachedTimeSec)
                    LibVlcBindings.libvlc_audio_set_mute(player, if (mutedState) 1 else 0)
                    LibVlcBindings.libvlc_audio_set_volume(player, (volumeLevel * 100f).toInt())
                    LibVlcBindings.libvlc_media_player_play(player)
                    return
                }
                if (!endedFiredForCurrentMedia) {
                    endedFiredForCurrentMedia = true
                    playingFallback = false
                    cachedPlayingState = false
                    cachedIsPlaying = false
                    if (cachedBufferingState) {
                        cachedBufferingState = false
                        onBufferingChangedListener?.invoke(false)
                        onBufferingStateChangedListener?.invoke(false)
                    }
                    nativeWidget.attributes["playing"] = false
                    nativeWidget.attributes["isPlaying"] = false
                    onPlayingStateChangedListener?.invoke(false)
                    if (state == 7) {
                        onErrorListener?.invoke("Playback error")
                    } else {
                        onEndedListener?.invoke()
                    }
                    nativeWidget.attributes["vblankDirtied"] = true
                }
            }
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        playbackRate = speed.coerceIn(0.1f, 8.0f)
        LibVlcBindings.libvlc_media_player_set_rate(vlcMediaPlayer, playbackRate)
    }

    override fun getPlaybackSpeed(): Float {
        val player = vlcMediaPlayer
        return if (player != null && LibVlcBindings.isAvailable) {
            LibVlcBindings.libvlc_media_player_get_rate(player)
        } else {
            playbackRate
        }
    }

    override fun setVolume(volume: Float) {
        volumeLevel = volume.coerceIn(0f, 2f)
        LibVlcBindings.libvlc_audio_set_volume(vlcMediaPlayer, (volumeLevel * 100f).toInt())
    }

    override fun getVolume(): Float = volumeLevel

    override fun setMuted(muted: Boolean) {
        mutedState = muted
        LibVlcBindings.libvlc_audio_set_mute(vlcMediaPlayer, if (muted) 1 else 0)
    }

    override fun isMuted(): Boolean {
        val player = vlcMediaPlayer
        return if (player != null && LibVlcBindings.isAvailable) {
            LibVlcBindings.libvlc_audio_get_mute(player)
        } else {
            mutedState
        }
    }

    override fun setResizeMode(mode: VideoResizeMode) {
        resizeModeState = mode
        when (mode) {
            VideoResizeMode.FIT -> {
                LibVlcBindings.libvlc_video_set_aspect_ratio(vlcMediaPlayer, null)
                LibVlcBindings.libvlc_video_set_scale(vlcMediaPlayer, 0f)
            }
            VideoResizeMode.FILL -> {
                LibVlcBindings.libvlc_video_set_scale(vlcMediaPlayer, 0f)
            }
            VideoResizeMode.STRETCH -> {
                val w = nativeWidget.bounds.width.toInt().coerceAtLeast(16)
                val h = nativeWidget.bounds.height.toInt().coerceAtLeast(9)
                LibVlcBindings.libvlc_video_set_aspect_ratio(vlcMediaPlayer, "$w:$h")
            }
        }
    }

    override fun getResizeMode(): VideoResizeMode = resizeModeState

    override fun getAudioTracks(): List<MediaTrackInfo> {
        val player = vlcMediaPlayer
        if (player != null && LibVlcBindings.isAvailable) {
            val descriptions = LibVlcBindings.readAudioTrackDescriptions(player).filter { it.first != -1 }
            if (descriptions.isNotEmpty()) {
                val activeId = LibVlcBindings.libvlc_audio_get_track(player)
                audioTracksList.clear()
                for ((id, name) in descriptions) {
                    audioTracksList.add(
                        MediaTrackInfo(
                            id = id.toString(),
                            label = name,
                            language = "und",
                            isSelected = (id == activeId)
                        )
                    )
                }
            }
        }
        return audioTracksList.toList()
    }

    private fun applyPendingAudioTrackIfReady(player: COpaquePointer) {
        val target = pendingAudioTrackSelection ?: return
        val descriptions = LibVlcBindings.readAudioTrackDescriptions(player).filter { it.first != -1 }
        if (descriptions.isEmpty()) return
        val resolvedId: Int? = if (target.startsWith("index:")) {
            val idx = target.removePrefix("index:").toIntOrNull()
            if (idx != null) descriptions.getOrNull(idx)?.first else null
        } else {
            val exact = descriptions.firstOrNull { it.first.toString() == target }?.first
            exact ?: target.toIntOrNull()?.let { num ->
                descriptions.getOrNull(num)?.first ?: num
            }
        }
        if (resolvedId != null) {
            LibVlcBindings.libvlc_audio_set_track(player, resolvedId)
            pendingAudioTrackSelection = null
            getAudioTracks()
            onTracksChangedListener?.invoke()
        }
    }

    override fun selectAudioTrack(trackId: String): Boolean {
        pendingAudioTrackSelection = trackId
        val indexPrefix = if (trackId.startsWith("index:")) trackId.removePrefix("index:").toIntOrNull() else null
        for (i in audioTracksList.indices) {
            val item = audioTracksList[i]
            val selected = if (indexPrefix != null) (i == indexPrefix) else (item.id == trackId)
            audioTracksList[i] = item.copy(isSelected = selected)
        }
        val player = vlcMediaPlayer
        if (player != null && LibVlcBindings.isAvailable) {
            applyPendingAudioTrackIfReady(player)
        }
        onTracksChangedListener?.invoke()
        return true
    }

    override fun getSubtitleTracks(): List<SubtitleTrackInfo> {
        val player = vlcMediaPlayer
        if (player != null && LibVlcBindings.isAvailable && subtitleTracksList.none { it.externalUrl != null }) {
            val descriptions = LibVlcBindings.readSubtitleTrackDescriptions(player).filter { it.first != -1 }
            if (descriptions.isNotEmpty()) {
                val activeSpu = LibVlcBindings.libvlc_video_get_spu(player)
                subtitleTracksList.clear()
                for ((id, name) in descriptions) {
                    subtitleTracksList.add(
                        SubtitleTrackInfo(
                            id = id.toString(),
                            label = name,
                            language = "und",
                            isSelected = !disableSpuByDefault && (id == activeSpu)
                        )
                    )
                }
            }
        }
        return subtitleTracksList.toList()
    }

    override fun addExternalSubtitleTrack(label: String, language: String, vttOrSrtUrl: String) {
        val trackId = (subtitleTracksList.size + 1).toString()
        for (i in subtitleTracksList.indices) {
            subtitleTracksList[i] = subtitleTracksList[i].copy(isSelected = false)
        }
        subtitleTracksList.add(SubtitleTrackInfo(trackId, label, language, externalUrl = vttOrSrtUrl, isSelected = true))
        LibVlcBindings.libvlc_media_player_add_slave(
            player = vlcMediaPlayer,
            type = LibVlcBindings.SLAVE_TYPE_SUBTITLE,
            uri = vttOrSrtUrl,
            select = !disableSpuByDefault
        )
        onTracksChangedListener?.invoke()
    }

    private fun applyPendingSubtitleTrackIfReady(player: COpaquePointer) {
        val target = pendingSubtitleTrackSelection ?: return
        val descriptions = LibVlcBindings.readSubtitleTrackDescriptions(player).filter { it.first != -1 }
        if (descriptions.isEmpty()) return
        val resolvedId: Int? = if (target.startsWith("index:")) {
            val idx = target.removePrefix("index:").toIntOrNull()
            if (idx != null) descriptions.getOrNull(idx)?.first else null
        } else {
            val exact = descriptions.firstOrNull { it.first.toString() == target }?.first
            exact ?: target.toIntOrNull()?.let { num ->
                descriptions.getOrNull(num)?.first ?: num
            }
        }
        if (resolvedId != null) {
            LibVlcBindings.libvlc_video_set_spu(player, resolvedId)
            pendingSubtitleTrackSelection = null
            getSubtitleTracks()
            onTracksChangedListener?.invoke()
        }
    }

    override fun selectSubtitleTrack(trackId: String?): Boolean {
        if (trackId == null) {
            disableSpuByDefault = true
            pendingSubtitleTrackSelection = null
            for (i in subtitleTracksList.indices) {
                subtitleTracksList[i] = subtitleTracksList[i].copy(isSelected = false)
            }
            val player = vlcMediaPlayer
            if (player != null && LibVlcBindings.isAvailable) {
                LibVlcBindings.libvlc_video_set_spu(player, -1)
            }
            onTracksChangedListener?.invoke()
            return true
        }
        disableSpuByDefault = false
        pendingSubtitleTrackSelection = trackId
        val indexPrefix = if (trackId.startsWith("index:")) trackId.removePrefix("index:").toIntOrNull() else null
        for (i in subtitleTracksList.indices) {
            val item = subtitleTracksList[i]
            val selected = if (indexPrefix != null) (i == indexPrefix) else (item.id == trackId)
            subtitleTracksList[i] = item.copy(isSelected = selected)
        }
        val player = vlcMediaPlayer
        if (player != null && LibVlcBindings.isAvailable) {
            applyPendingSubtitleTrackIfReady(player)
        }
        onTracksChangedListener?.invoke()
        return true
    }

    private var internalSubtitlesEnabledState: Boolean = true

    override fun setInternalSubtitlesEnabled(enabled: Boolean) {
        internalSubtitlesEnabledState = enabled
        if (!enabled) {
            disableSpuByDefault = true
            pendingSubtitleTrackSelection = null
            val player = vlcMediaPlayer
            if (player != null && LibVlcBindings.isAvailable) {
                LibVlcBindings.libvlc_video_set_spu(player, -1)
            }
        }
    }

    override fun isInternalSubtitlesEnabled(): Boolean = internalSubtitlesEnabledState

    override fun supportsPictureInPicture(): Boolean = true
    override fun enterPictureInPicture(): Boolean = true
    override fun exitPictureInPicture() {}

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

    override fun getBufferedRanges(): List<Pair<Double, Double>> = bufferedRangesState

    override fun setAntialiasing(antialiasing: Boolean) {
        antialiasingEnabled = antialiasing
        nativeWidget.attributes["antialiasing"] = antialiasing
    }

    override fun isAntialiasing(): Boolean = antialiasingEnabled

    override fun setPreloadMode(mode: MediaPreloadMode) {
        preloadModeState = mode
    }

    override fun getPreloadMode(): MediaPreloadMode = preloadModeState

    override fun setAdaptivePreloadBufferingEnabled(enabled: Boolean) {
        adaptivePreloadBufferingEnabledState = enabled
    }

    override fun isAdaptivePreloadBufferingEnabled(): Boolean = adaptivePreloadBufferingEnabledState

    override fun setOnMediaReady(listener: (() -> Unit)?) {
        onMediaReadyListener = listener
    }

    override fun isMediaReady(): Boolean =
        mediaReadyFlag || frameSwapCounter > 0L || (!LibVlcBindings.isAvailable && getDurationSeconds() > 0.0)

    override fun getPresentedFrameCount(): Long = frameSwapCounter

    override fun setOnBeforeFramePresent(listener: ((frameIndex: Long, timestampMs: Double) -> Unit)?) {
        onBeforeFramePresentListener = listener
    }

    private fun updateAndNotifyBufferedHorizon(currentSec: Double) {
        val dur = getDurationSeconds()
        val bufEnd = if (dur > 0.0) {
            minOf(dur, currentSec + networkCachingBufferSec)
        } else {
            currentSec + networkCachingBufferSec
        }
        if (bufEnd > 0.0 && (lastNotifiedBufferedEndSec < 0.0 || abs(bufEnd - lastNotifiedBufferedEndSec) >= 1.0)) {
            lastNotifiedBufferedEndSec = bufEnd
            bufferedRangesState = listOf(0.0 to bufEnd)
            onBufferedRangesChangedListener?.invoke(bufferedRangesState)
        }
    }

    override fun dispose() {
        LibVlcBindings.libvlc_media_player_stop(vlcMediaPlayer)
        LibVlcBindings.libvlc_media_release(currentVlcMedia)
        currentVlcMedia = null
        LibVlcBindings.libvlc_media_player_release(vlcMediaPlayer)
        LibVlcBindings.libvlc_release(vlcInstance)
        selfStableRef?.dispose()
        selfStableRef = null
        withBufferPoolLock {
            for (i in 0 until frameSlotCount) {
                frameSlots[i]?.let { nativeHeap.free(it) }
                frameSlots[i] = null
                slotState[i] = SLOT_FREE
                slotDecodeSeq[i] = 0L
                slotFrameSeq[i] = 0L
            }
            latestDisplayedSlot = -1
            currentReadSlot = 0
        }
        super.dispose()
    }
}

private const val SLOT_FREE: Int = 0
private const val SLOT_LOCKED_DECODING: Int = 1
private const val SLOT_DECODED_PENDING_DISPLAY: Int = 2
private const val SLOT_DISPLAYED: Int = 3

private val vlcVideoLockCallback = staticCFunction { opaque: COpaquePointer?, planes: CPointer<COpaquePointerVar>? ->
    val impl = opaque?.asStableRef<DesktopLibVlcVideoPlayerImpl<*>>()?.get()
    val bufPtr = impl?.lockVideoFrameBuffer()
    if (planes != null && bufPtr != null) {
        planes[0] = bufPtr
    }
    bufPtr
}

private val vlcVideoUnlockCallback = staticCFunction { opaque: COpaquePointer?, picture: COpaquePointer?, _: CPointer<COpaquePointerVar>? ->
    opaque?.asStableRef<DesktopLibVlcVideoPlayerImpl<*>>()?.get()?.onVlcFrameUnlocked(picture)
    Unit
}

private val vlcVideoDisplayCallback = staticCFunction { opaque: COpaquePointer?, picture: COpaquePointer? ->
    opaque?.asStableRef<DesktopLibVlcVideoPlayerImpl<*>>()?.get()?.onVlcFrameDisplayed(picture)
    Unit
}
