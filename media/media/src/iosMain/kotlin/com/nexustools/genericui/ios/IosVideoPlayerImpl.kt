@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class
)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.component.media.MediaTrackInfo
import com.nexustools.genericui.component.media.SubtitleTrackInfo
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.CodecSupportLevel
import com.nexustools.genericui.enums.media.MediaContainer
import com.nexustools.genericui.enums.media.VideoCodec
import com.nexustools.genericui.enums.media.VideoResizeMode
import com.nexustools.genericui.ext.media.MediaScreenWakeController
import com.nexustools.genericui.ext.media.system.SystemMediaFrameRingBuffer
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CValue
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVLayerVideoGravityResize
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaCharacteristicAudible
import platform.AVFoundation.AVMediaCharacteristicLegible
import platform.AVFoundation.AVMediaSelectionOption
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemVideoOutput
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.CMTimeRangeValue
import platform.AVFoundation.addOutput
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.asset
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.loadedTimeRanges
import platform.AVFoundation.mediaSelectionGroupForMediaCharacteristic
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.presentationSize
import platform.AVFoundation.preventsDisplaySleepDuringVideoPlayback
import platform.AVFoundation.rate
import platform.AVFoundation.removeOutput
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.selectMediaOption
import platform.AVFoundation.volume
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.CMTimeRangeGetEnd
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSValue
import platform.Foundation.localeIdentifier
import platform.Foundation.numberWithUnsignedInt
import platform.UIKit.UIColor
import platform.UIKit.UIView
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong

/**
 * Apple iOS & iPadOS (`BackendTarget.IOS` and `BackendTarget.IPadOS`) implementation of
 * [VideoPlayerNativeImpl] backed by `AVPlayer`, `AVPlayerLayer` inside a genuine `UIView`,
 * and `AVAudioSession`.
 */
class IosVideoPlayerImpl private constructor(
    private val hostView: AvPlayerHostView
) : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(hostView),
    componentClassPrefix = "video-player"
), VideoPlayerNativeImpl<UikitViewHandle> {

    private class AvPlayerHostView(
        frame: CValue<CGRect> = CGRectMake(0.0, 0.0, 320.0, 180.0)
    ) : UIView(frame = frame) {
        val playerLayer: AVPlayerLayer = AVPlayerLayer().apply {
            videoGravity = AVLayerVideoGravityResizeAspect
        }

        init {
            backgroundColor = UIColor.blackColor
            layer.addSublayer(playerLayer)
            playerLayer.setFrame(bounds)
        }

        override fun layoutSubviews() {
            super.layoutSubviews()
            playerLayer.setFrame(bounds)
        }
    }

    constructor() : this(AvPlayerHostView())

    val avPlayer: AVPlayer = AVPlayer().apply {
        preventsDisplaySleepDuringVideoPlayback = true
    }

    private var timeObserverToken: Any? = null
    private var endedNotificationObserver: Any? = null

    private var currentTimeSec: Double = 0.0
    private var durationSec: Double = 0.0
    private var playing: Boolean = false
    private var speed: Float = 1.0f
    private var volumeLevel: Float = 1.0f
    private var mutedFlag: Boolean = false
    private var resizeMode: VideoResizeMode = VideoResizeMode.FIT
    private var loopEnabled: Boolean = false
    private var antialiasingEnabled: Boolean = true
    private var keepScreenOnState: Boolean = true
    private var screenWakeToken: Long = MediaScreenWakeController.allocateToken()

    private var offscreenFrameCaptureEnabled: Boolean = false
    private var videoOutput: AVPlayerItemVideoOutput? = null
    private val frameRingLock = AtomicInt(0)
    private var offscreenRgbaRing = SystemMediaFrameRingBuffer(width = 16, height = 16, capacity = 3)
    private val frameVersionCounter = AtomicLong(0L)
    private val presentedFrameCounter = AtomicLong(0L)
    private var naturalWidthPx: Int = 0
    private var naturalHeightPx: Int = 0
    private var onBeforeFramePresentListener: ((Long, Double) -> Unit)? = null

    @kotlin.concurrent.Volatile
    private var nativePlayerDisposed: Boolean = false
    private var mediaReadyFlag: Boolean = false
    private var bufferedRangesState: List<Pair<Double, Double>> = emptyList()
    private val audioTracks = mutableListOf<MediaTrackInfo>()
    private val subtitleTracks = mutableListOf<SubtitleTrackInfo>()
    private var activeSubtitleTrackId: String? = null
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

    init {
        hostView.playerLayer.player = avPlayer
        installTimeObserver()
    }

    private fun syncWakeLockState() {
        val active = !nativePlayerDisposed && keepScreenOnState && playing
        MediaScreenWakeController.setTokenActive(screenWakeToken, active)
        runCatching {
            avPlayer.preventsDisplaySleepDuringVideoPlayback = !nativePlayerDisposed && keepScreenOnState
        }
    }

    private fun configurePlaybackAudioSession() {
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, error = null)
            session.setActive(true, error = null)
        }
    }

    private fun installTimeObserver() {
        runCatching {
            val interval = CMTimeMakeWithSeconds(0.25, 600)
            timeObserverToken = avPlayer.addPeriodicTimeObserverForInterval(interval, queue = null) { cmTime ->
                if (nativePlayerDisposed) return@addPeriodicTimeObserverForInterval
                val sec = CMTimeGetSeconds(cmTime)
                if (!sec.isNaN() && !sec.isInfinite() && sec >= 0.0) {
                    currentTimeSec = sec
                    onTimeUpdateListener?.invoke(sec)
                }
                syncItemDurationAndBuffersFromPlayer()
                if (offscreenFrameCaptureEnabled) {
                    captureLatestFrameFromVideoOutput()
                }
            }
        }
    }

    private fun attachVideoOutputToCurrentItem() {
        val item = avPlayer.currentItem ?: return
        val existing = videoOutput
        if (existing != null) {
            runCatching { item.removeOutput(existing) }
        }
        val formatKey = CFBridgingRelease(kCVPixelBufferPixelFormatTypeKey)
        val attrs = mapOf<Any?, Any?>(
            formatKey to NSNumber.numberWithUnsignedInt(kCVPixelFormatType_32BGRA)
        )
        val output = AVPlayerItemVideoOutput(pixelBufferAttributes = attrs)
        item.addOutput(output)
        videoOutput = output
    }

    private fun captureLatestFrameFromVideoOutput() {
        if (!offscreenFrameCaptureEnabled || nativePlayerDisposed) return
        val output = videoOutput ?: return
        val currentCmTime = avPlayer.currentTime()
        if (!output.hasNewPixelBufferForItemTime(currentCmTime)) return
        val pb = output.copyPixelBufferForItemTime(currentCmTime, itemTimeForDisplay = null) ?: return
        try {
            CVPixelBufferLockBaseAddress(pb, kCVPixelBufferLock_ReadOnly)
            try {
                val w = CVPixelBufferGetWidth(pb).toInt().coerceAtLeast(1)
                val h = CVPixelBufferGetHeight(pb).toInt().coerceAtLeast(1)
                val bytesPerRow = CVPixelBufferGetBytesPerRow(pb).toInt().coerceAtLeast(w * 4)
                val baseAddr = CVPixelBufferGetBaseAddress(pb)?.reinterpret<ByteVar>() ?: return
                naturalWidthPx = w
                naturalHeightPx = h
                while (!frameRingLock.compareAndSet(0, 1)) {
                    // brief spin
                }
                try {
                    val latest = offscreenRgbaRing.latestPresentedSlot()
                    if (latest.width != w || latest.height != h) {
                        offscreenRgbaRing = SystemMediaFrameRingBuffer(width = w, height = h, capacity = 3)
                    }
                    val dst = offscreenRgbaRing.acquireWriteSlot().bytes
                    for (y in 0 until h) {
                        val srcRow = y * bytesPerRow
                        val dstRow = y * w * 4
                        for (x in 0 until w) {
                            val sIdx = srcRow + (x shl 2)
                            val dIdx = dstRow + (x shl 2)
                            dst[dIdx] = baseAddr[sIdx + 2]
                            dst[dIdx + 1] = baseAddr[sIdx + 1]
                            dst[dIdx + 2] = baseAddr[sIdx]
                            dst[dIdx + 3] = baseAddr[sIdx + 3]
                        }
                    }
                } finally {
                    frameRingLock.store(0)
                }
                val frameIdx = presentedFrameCounter.addAndFetch(1L)
                frameVersionCounter.addAndFetch(1L)
                onBeforeFramePresentListener?.invoke(frameIdx, (currentTimeSec * 1000.0).coerceAtLeast(0.0))
            } finally {
                CVPixelBufferUnlockBaseAddress(pb, kCVPixelBufferLock_ReadOnly)
            }
        } finally {
            CVPixelBufferRelease(pb)
        }
    }

    private fun syncItemDurationAndBuffersFromPlayer() {
        val item = avPlayer.currentItem ?: return
        item.presentationSize.useContents {
            val w = width.toInt()
            val h = height.toInt()
            if (w > 0 && h > 0) {
                naturalWidthPx = w
                naturalHeightPx = h
            }
        }
        val dur = CMTimeGetSeconds(item.duration)
        if (!dur.isNaN() && !dur.isInfinite() && dur > 0.0 && dur != durationSec) {
            durationSec = dur
            onDurationChangedListener?.invoke(dur)
        }
        val ranges = item.loadedTimeRanges.mapNotNull { entry ->
            val value = entry as? NSValue ?: return@mapNotNull null
            val range = value.CMTimeRangeValue
            val end = CMTimeGetSeconds(CMTimeRangeGetEnd(range))
            if (!end.isNaN() && !end.isInfinite() && end > 0.0) 0.0 to end else null
        }
        if (ranges.isNotEmpty() && ranges != bufferedRangesState) {
            bufferedRangesState = ranges
            onBufferedRangesChangedListener?.invoke(ranges)
        }
        populateMediaTracksFromItem(item)
    }

    private fun populateMediaTracksFromItem(item: AVPlayerItem) {
        var changed = false
        val audibleGroup = runCatching {
            item.asset.mediaSelectionGroupForMediaCharacteristic(AVMediaCharacteristicAudible)
        }.getOrNull()
        if (audibleGroup != null && audioTracks.isEmpty()) {
            audibleGroup.options.forEachIndexed { idx, raw ->
                val opt = raw as? AVMediaSelectionOption ?: return@forEachIndexed
                audioTracks.add(
                    MediaTrackInfo(
                        id = "audio-$idx",
                        label = opt.displayName,
                        language = opt.locale?.localeIdentifier ?: "und",
                        isDefault = idx == 0,
                        isSelected = idx == 0
                    )
                )
                changed = true
            }
        }
        val legibleGroup = runCatching {
            item.asset.mediaSelectionGroupForMediaCharacteristic(AVMediaCharacteristicLegible)
        }.getOrNull()
        if (legibleGroup != null && subtitleTracks.isEmpty()) {
            legibleGroup.options.forEachIndexed { idx, raw ->
                val opt = raw as? AVMediaSelectionOption ?: return@forEachIndexed
                subtitleTracks.add(
                    SubtitleTrackInfo(
                        id = "caption-$idx",
                        label = opt.displayName,
                        language = opt.locale?.localeIdentifier ?: "en",
                        isSelected = false
                    )
                )
                changed = true
            }
        }
        if (changed) {
            onTracksChangedListener?.invoke()
        }
    }

    private fun resolveNsUrl(url: String): NSURL? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("/") || trimmed.startsWith("file://")) {
            val localPath = if (trimmed.startsWith("file://")) trimmed.removePrefix("file://") else trimmed
            NSURL.fileURLWithPath(localPath)
        } else {
            NSURL.URLWithString(trimmed) ?: NSURL.fileURLWithPath(trimmed)
        }
    }

    override fun setSource(url: String, seekOffsetSeconds: Double, durationOverrideSeconds: Double?) {
        currentTimeSec = seekOffsetSeconds.coerceAtLeast(0.0)
        endedNotificationObserver?.let { obs ->
            runCatching { NSNotificationCenter.defaultCenter.removeObserver(obs) }
            endedNotificationObserver = null
        }

        val nsUrl = resolveNsUrl(url)
        mediaReadyFlag = nsUrl != null
        if (nsUrl != null) {
            runCatching {
                val item = AVPlayerItem.playerItemWithURL(nsUrl)
                avPlayer.replaceCurrentItemWithPlayerItem(item)
                if (offscreenFrameCaptureEnabled) {
                    attachVideoOutputToCurrentItem()
                }
                if (currentTimeSec > 0.0) {
                    avPlayer.seekToTime(CMTimeMakeWithSeconds(currentTimeSec, 600))
                }
                endedNotificationObserver = NSNotificationCenter.defaultCenter.addObserverForName(
                    name = AVPlayerItemDidPlayToEndTimeNotification,
                    `object` = item,
                    queue = NSOperationQueue.mainQueue
                ) { _ ->
                    if (loopEnabled && !nativePlayerDisposed) {
                        avPlayer.seekToTime(CMTimeMakeWithSeconds(0.0, 600))
                        avPlayer.play()
                        if (speed != 1.0f) {
                            avPlayer.rate = speed
                        }
                        return@addObserverForName
                    }
                    playing = false
                    syncWakeLockState()
                    onPlayingStateChangedListener?.invoke(false)
                    onEndedListener?.invoke()
                }
            }
        } else {
            runCatching { avPlayer.replaceCurrentItemWithPlayerItem(null) }
        }

        if (durationOverrideSeconds != null) {
            durationSec = durationOverrideSeconds
            onDurationChangedListener?.invoke(durationSec)
        }
        if (mediaReadyFlag) {
            onMediaReadyListener?.invoke()
        }
        updateBufferedHorizon(currentTimeSec)
    }

    override fun play() {
        configurePlaybackAudioSession()
        playing = true
        runCatching {
            avPlayer.play()
            if (speed != 1.0f) {
                avPlayer.rate = speed
            }
        }
        syncWakeLockState()
        onPlayingStateChangedListener?.invoke(true)
        updateBufferedHorizon(currentTimeSec)
    }

    override fun pause() {
        playing = false
        runCatching { avPlayer.pause() }
        syncWakeLockState()
        onPlayingStateChangedListener?.invoke(false)
    }

    override fun stop() {
        playing = false
        currentTimeSec = 0.0
        runCatching {
            avPlayer.pause()
            avPlayer.seekToTime(CMTimeMakeWithSeconds(0.0, 600))
        }
        syncWakeLockState()
        onPlayingStateChangedListener?.invoke(false)
        onTimeUpdateListener?.invoke(0.0)
    }

    override fun seekTo(seconds: Double) {
        currentTimeSec = seconds.coerceAtLeast(0.0)
        runCatching {
            avPlayer.seekToTime(CMTimeMakeWithSeconds(currentTimeSec, 600))
        }
        onTimeUpdateListener?.invoke(currentTimeSec)
        updateBufferedHorizon(currentTimeSec)
    }

    override fun getCurrentTimeSeconds(): Double {
        val nativeSec = runCatching { CMTimeGetSeconds(avPlayer.currentTime()) }.getOrNull()
        if (nativeSec != null && !nativeSec.isNaN() && !nativeSec.isInfinite() && nativeSec > 0.0) {
            currentTimeSec = nativeSec
        }
        return currentTimeSec
    }

    override fun getDurationSeconds(): Double = durationSec
    override fun isPlaying(): Boolean = playing

    override fun setPlaybackSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.25f, 4.0f)
        if (playing) {
            runCatching { avPlayer.rate = this.speed }
        }
    }

    override fun getPlaybackSpeed(): Float = speed

    override fun setVolume(volume: Float) {
        volumeLevel = volume.coerceIn(0f, 1f)
        runCatching { avPlayer.volume = volumeLevel }
    }

    override fun getVolume(): Float = volumeLevel

    override fun setMuted(muted: Boolean) {
        mutedFlag = muted
        runCatching { avPlayer.muted = muted }
    }

    override fun isMuted(): Boolean = mutedFlag

    override fun setResizeMode(mode: VideoResizeMode) {
        resizeMode = mode
        hostView.playerLayer.videoGravity = when (mode) {
            VideoResizeMode.FIT -> AVLayerVideoGravityResizeAspect
            VideoResizeMode.FILL -> AVLayerVideoGravityResizeAspectFill
            VideoResizeMode.STRETCH -> AVLayerVideoGravityResize
        }
    }

    override fun getResizeMode(): VideoResizeMode = resizeMode

    override fun getAudioTracks(): List<MediaTrackInfo> = audioTracks.toList()

    override fun selectAudioTrack(trackId: String): Boolean {
        val indexPrefix = if (trackId.startsWith("index:")) trackId.removePrefix("index:").toIntOrNull() else null
        var selectedIndex = -1
        for (i in audioTracks.indices) {
            val item = audioTracks[i]
            val selected = if (indexPrefix != null) (i == indexPrefix) else (item.id == trackId)
            if (selected) selectedIndex = i
            audioTracks[i] = item.copy(isSelected = selected)
        }
        if (selectedIndex >= 0) {
            avPlayer.currentItem?.let { item ->
                runCatching {
                    val group = item.asset.mediaSelectionGroupForMediaCharacteristic(AVMediaCharacteristicAudible)
                    val opt = group?.options?.getOrNull(selectedIndex) as? AVMediaSelectionOption
                    if (group != null && opt != null) {
                        item.selectMediaOption(opt, inMediaSelectionGroup = group)
                    }
                }
            }
        }
        onTracksChangedListener?.invoke()
        return true
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
        var selectedIndex = -1
        for (i in subtitleTracks.indices) {
            val item = subtitleTracks[i]
            val selected = if (trackId == null) false else if (indexPrefix != null) (i == indexPrefix) else (item.id == trackId)
            if (selected) selectedIndex = i
            subtitleTracks[i] = item.copy(isSelected = selected)
        }
        avPlayer.currentItem?.let { item ->
            runCatching {
                val group = item.asset.mediaSelectionGroupForMediaCharacteristic(AVMediaCharacteristicLegible)
                if (group != null) {
                    val opt = if (selectedIndex >= 0) group.options.getOrNull(selectedIndex) as? AVMediaSelectionOption else null
                    item.selectMediaOption(opt, inMediaSelectionGroup = group)
                }
            }
        }
        onTracksChangedListener?.invoke()
        return true
    }

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        attachedAnalyzer = analyzer
    }

    override fun setOnTimeUpdate(listener: ((Double) -> Unit)?) { onTimeUpdateListener = listener }
    override fun setOnDurationChanged(listener: ((Double) -> Unit)?) { onDurationChangedListener = listener }
    override fun setOnEnded(listener: ((() -> Unit))?) { onEndedListener = listener }
    override fun setOnBufferingChanged(listener: ((Boolean) -> Unit)?) { onBufferingChangedListener = listener }
    override fun setOnError(listener: ((String) -> Unit)?) { onErrorListener = listener }
    override fun setOnPlayingStateChanged(listener: ((Boolean) -> Unit)?) { onPlayingStateChangedListener = listener }
    override fun setOnBufferingStateChanged(listener: ((Boolean) -> Unit)?) { onBufferingStateChangedListener = listener }
    override fun setOnBufferedRangesChanged(listener: ((List<Pair<Double, Double>>) -> Unit)?) {
        onBufferedRangesChangedListener = listener
    }
    override fun setOnTracksChanged(listener: (() -> Unit)?) { onTracksChangedListener = listener }
    override fun getBufferedRanges(): List<Pair<Double, Double>> = bufferedRangesState

    override fun setAntialiasing(antialiasing: Boolean) {
        antialiasingEnabled = antialiasing
        hostView.playerLayer.allowsEdgeAntialiasing = antialiasing
    }

    override fun isAntialiasing(): Boolean = antialiasingEnabled

    override fun setKeepScreenOn(keepScreenOn: Boolean) {
        keepScreenOnState = keepScreenOn
        syncWakeLockState()
    }

    override fun isKeepScreenOn(): Boolean = keepScreenOnState

    override fun bindScreenWakeToken(token: Long) {
        if (token > 0L && token != screenWakeToken) {
            val previous = screenWakeToken
            screenWakeToken = token
            MediaScreenWakeController.releaseToken(previous)
            syncWakeLockState()
        }
    }

    override fun dispose() {
        if (nativePlayerDisposed) return
        nativePlayerDisposed = true
        playing = false
        timeObserverToken?.let { tok ->
            runCatching { avPlayer.removeTimeObserver(tok) }
            timeObserverToken = null
        }
        endedNotificationObserver?.let { obs ->
            runCatching { NSNotificationCenter.defaultCenter.removeObserver(obs) }
            endedNotificationObserver = null
        }
        runCatching {
            videoOutput?.let { out -> avPlayer.currentItem?.removeOutput(out) }
            videoOutput = null
            avPlayer.pause()
            avPlayer.replaceCurrentItemWithPlayerItem(null)
        }
        hostView.playerLayer.player = null
        MediaScreenWakeController.releaseToken(screenWakeToken)
        super.dispose()
    }

    override fun setLoop(loop: Boolean) {
        loopEnabled = loop
    }

    override fun isLoop(): Boolean = loopEnabled

    override fun enableOffscreenFrameCapture(enabled: Boolean) {
        offscreenFrameCaptureEnabled = enabled
        if (enabled) {
            runCatching { attachVideoOutputToCurrentItem() }
        }
    }

    override fun copyLatestVideoFrame(): ImageBuffer? {
        if (offscreenFrameCaptureEnabled) {
            runCatching { captureLatestFrameFromVideoOutput() }
        }
        if (frameVersionCounter.load() <= 0L) return null
        while (!frameRingLock.compareAndSet(0, 1)) {
            // brief spin
        }
        return try {
            val slot = offscreenRgbaRing.latestPresentedSlot()
            ImageBuffer(
                width = slot.width,
                height = slot.height,
                pixelFormat = slot.pixelFormat,
                colorSpace = slot.colorSpace,
                bytes = slot.bytes.copyOf()
            )
        } finally {
            frameRingLock.store(0)
        }
    }

    override val videoFrameVersion: Long
        get() = frameVersionCounter.load()

    override fun getNaturalVideoWidth(): Int = naturalWidthPx

    override fun getNaturalVideoHeight(): Int = naturalHeightPx

    override fun getPresentedFrameCount(): Long = presentedFrameCounter.load()

    override fun setOnBeforeFramePresent(listener: ((frameIndex: Long, timestampMs: Double) -> Unit)?) {
        onBeforeFramePresentListener = listener
    }

    override fun setOnMediaReady(listener: (() -> Unit)?) {
        onMediaReadyListener = listener
        if (mediaReadyFlag) listener?.invoke()
    }

    override fun isMediaReady(): Boolean = mediaReadyFlag

    private fun updateBufferedHorizon(currentSec: Double) {
        if (bufferedRangesState.isNotEmpty()) return
        val bufEnd = if (durationSec > 0.0) minOf(durationSec, currentSec + 15.0) else currentSec + 15.0
        if (bufEnd > 0.0) {
            bufferedRangesState = listOf(0.0 to bufEnd)
            onBufferedRangesChangedListener?.invoke(bufferedRangesState)
        }
    }
}
