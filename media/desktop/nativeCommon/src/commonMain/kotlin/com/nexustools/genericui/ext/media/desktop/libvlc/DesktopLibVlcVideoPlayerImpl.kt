@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.libvlc

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.component.media.MediaTrackInfo
import com.nexustools.genericui.component.media.SubtitleTrackInfo
import com.nexustools.genericui.desktop.core.DesktopBaseNativeImpl
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.CodecSupportLevel
import com.nexustools.genericui.enums.media.MediaContainer
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
        "--network-caching=1500",
        "--file-caching=1000",
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
    private var vblankTickCounter: Int = 0

    private var currentSourceUrl: String = ""
    private var endedFiredForCurrentMedia: Boolean = false
    private var lastReportedTimeSec: Double = -1.0
    private var lastReportedDurationSec: Double = -1.0
    private var cachedPlayingState: Boolean = false
    private var lastVlcPollMark: kotlin.time.TimeSource.Monotonic.ValueTimeMark? = null
    private var cachedIsPlaying: Boolean = false
    private var cachedTimeSec: Double = 0.0
    private var cachedDurationSec: Double = 0.0

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
        nativeWidget.attributes["captureBgraPtr"] = { acquireReadFrameBgraPtr() }
        nativeWidget.attributes["captureBgra"] = { getLatestVideoFrameBgra() }
        nativeWidget.attributes["surfaceWidth"] = { videoFrameWidth }
        nativeWidget.attributes["surfaceHeight"] = { videoFrameHeight }
        nativeWidget.attributes["swapCount"] = { frameSwapCounter }
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
                hasNewFrameFlag = 0
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
                slotFrameSeq[idx] = frameSwapCounter
                latestDisplayedSlot = idx
                hasNewFrameFlag = 1
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

    private val audioTracksList = mutableListOf(
        MediaTrackInfo(id = "1", label = "Default Audio", language = "und", isSelected = true)
    )
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
        endedFiredForCurrentMedia = false
        spuDisabledAppliedForMedia = false
        lastReportedTimeSec = -1.0
        lastReportedDurationSec = -1.0
        lastVlcPollMark = null
        nativeWidget.attributes["videoSource"] = url
        nativeWidget.attributes["src"] = url
        currentSecFallback = seekOffsetSeconds.coerceAtLeast(0.0)
        cachedTimeSec = currentSecFallback
        durationSecOverride = durationOverrideSeconds
        cachedDurationSec = durationOverrideSeconds ?: 0.0

        if (url.isNotBlank()) {
            val player = ensureVlcPlayer()
            LibVlcBindings.libvlc_media_release(currentVlcMedia)
            val isRemoteOrUri = url.contains("://")
            val media = if (isRemoteOrUri) {
                LibVlcBindings.libvlc_media_new_location(vlcInstance, url)
            } else {
                LibVlcBindings.libvlc_media_new_path(vlcInstance, url)
            }
            if (seekOffsetSeconds > 0.0) {
                LibVlcBindings.libvlc_media_add_option(media, ":start-time=$seekOffsetSeconds")
            }
            if (disableSpuByDefault) {
                LibVlcBindings.libvlc_media_add_option(media, ":no-spu")
                spuDisabledAppliedForMedia = true
            }
            currentVlcMedia = media
            LibVlcBindings.libvlc_media_player_set_media(player, media)
            if (!LibVlcBindings.isAvailable || durationOverrideSeconds != null) {
                mediaReadyFlag = true
                onMediaReadyListener?.invoke()
            }
        }

        if (durationOverrideSeconds != null) {
            onDurationChangedListener?.invoke(durationOverrideSeconds)
        }
        updateAndNotifyBufferedHorizon(currentSecFallback)
    }

    override fun play() {
        endedFiredForCurrentMedia = false
        lastReportedTimeSec = -1.0
        lastReportedDurationSec = -1.0
        playingFallback = true
        cachedPlayingState = true
        cachedIsPlaying = true
        val player = vlcMediaPlayer ?: if (currentSourceUrl.isNotBlank()) ensureVlcPlayer() else null
        if (player != null) {
            val res = LibVlcBindings.libvlc_media_player_play(player)
            if (res < 0 && LibVlcBindings.isAvailable) {
                playingFallback = false
                cachedPlayingState = false
                cachedIsPlaying = false
                nativeWidget.attributes["playing"] = false
                nativeWidget.attributes["isPlaying"] = false
                nativeWidget.notifyTreeInvalidated()
                onPlayingStateChangedListener?.invoke(false)
                onErrorListener?.invoke(LibVlcBindings.libvlc_errmsg() ?: "libvlc_media_player_play failed")
                return
            }
        }
        nativeWidget.attributes["playing"] = true
        nativeWidget.attributes["isPlaying"] = true
        nativeWidget.notifyTreeInvalidated()
        onPlayingStateChangedListener?.invoke(true)
    }

    override fun pause() {
        playingFallback = false
        cachedPlayingState = false
        cachedIsPlaying = false
        LibVlcBindings.libvlc_media_player_set_pause(vlcMediaPlayer, 1)
        nativeWidget.attributes["playing"] = false
        nativeWidget.attributes["isPlaying"] = false
        nativeWidget.notifyTreeInvalidated()
        onPlayingStateChangedListener?.invoke(false)
    }

    override fun stop() {
        playingFallback = false
        cachedPlayingState = false
        cachedIsPlaying = false
        currentSecFallback = 0.0
        cachedTimeSec = 0.0
        LibVlcBindings.libvlc_media_player_stop(vlcMediaPlayer)
        nativeWidget.attributes["playing"] = false
        nativeWidget.attributes["isPlaying"] = false
        nativeWidget.notifyTreeInvalidated()
        onPlayingStateChangedListener?.invoke(false)
        onTimeUpdateListener?.invoke(0.0)
    }

    override fun seekTo(seconds: Double) {
        val clamped = seconds.coerceAtLeast(0.0)
        currentSecFallback = clamped
        cachedTimeSec = clamped
        LibVlcBindings.libvlc_media_player_set_time(vlcMediaPlayer, (clamped * 1000.0).toLong())
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
        val player = vlcMediaPlayer
        val vlcLive = player != null && LibVlcBindings.isAvailable

        if (hasNewFrameFlag != 0) {
            nativeWidget.attributes["vblankDirtied"] = true
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

        // Throttle native LibVLC time/duration/state/track queries to at most once every 200ms
        val prevPoll = lastVlcPollMark
        if (prevPoll != null && prevPoll.elapsedNow().inWholeMilliseconds < 200L) {
            return
        }
        lastVlcPollMark = kotlin.time.TimeSource.Monotonic.markNow()

        val state = LibVlcBindings.libvlc_media_player_get_state(player)
        val playing = LibVlcBindings.libvlc_media_player_is_playing(player) ||
            state == 3 || (playingFallback && state in 0..2)
        cachedPlayingState = playing
        cachedIsPlaying = playing
        nativeWidget.attributes["playing"] = playing
        nativeWidget.attributes["isPlaying"] = playing

        if (playing || player != null) {
            if (pendingAudioTrackSelection != null && player != null) {
                applyPendingAudioTrackIfReady(player)
            }
            if (disableSpuByDefault && !spuDisabledAppliedForMedia && state == 3 && player != null) {
                LibVlcBindings.libvlc_video_set_spu(player, -1)
                spuDisabledAppliedForMedia = true
            } else if (pendingSubtitleTrackSelection != null && player != null) {
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
                    if (!mediaReadyFlag) {
                        mediaReadyFlag = true
                        onMediaReadyListener?.invoke()
                    }
                    onDurationChangedListener?.invoke(durSec)
                }
            }
            if (state == 3 && !mediaReadyFlag) {
                mediaReadyFlag = true
                onMediaReadyListener?.invoke()
            }
            if (playing) {
                val vlcMs = LibVlcBindings.libvlc_media_player_get_time(player)
                if (vlcMs >= 0L) {
                    currentSecFallback = vlcMs / 1000.0
                    cachedTimeSec = currentSecFallback
                }
                val curSec = cachedTimeSec
                if (abs(curSec - lastReportedTimeSec) >= 0.5) {
                    lastReportedTimeSec = curSec
                    onTimeUpdateListener?.invoke(curSec)
                    updateAndNotifyBufferedHorizon(curSec)
                }
            }
            if (state == 6 && !endedFiredForCurrentMedia) {
                endedFiredForCurrentMedia = true
                playingFallback = false
                cachedPlayingState = false
                cachedIsPlaying = false
                nativeWidget.attributes["playing"] = false
                nativeWidget.attributes["isPlaying"] = false
                onPlayingStateChangedListener?.invoke(false)
                onEndedListener?.invoke()
                nativeWidget.attributes["vblankDirtied"] = true
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

    override fun setOnMediaReady(listener: (() -> Unit)?) {
        onMediaReadyListener = listener
    }

    override fun isMediaReady(): Boolean =
        mediaReadyFlag || frameSwapCounter > 0L || getDurationSeconds() > 0.0

    private fun updateAndNotifyBufferedHorizon(currentSec: Double) {
        val dur = getDurationSeconds()
        val bufEnd = if (dur > 0.0) {
            minOf(dur, currentSec + networkCachingBufferSec)
        } else {
            currentSec + networkCachingBufferSec
        }
        if (bufEnd > 0.0) {
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
