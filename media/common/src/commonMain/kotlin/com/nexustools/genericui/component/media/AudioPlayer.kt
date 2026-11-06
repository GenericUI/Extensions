package com.nexustools.genericui.component.media

import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.MediaPreloadMode
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Cross-platform non-visual audio player implementing [Player], supporting seek offsets,
 * volume control, and real-time [AudioAnalyzer] attachment across all backends without
 * requiring mounting in a UI component tree.
 */
class AudioPlayer(
    sourceUrl: String? = null
) : Player {
    private var nativeImpl: AudioPlayerNativeImpl? = null
    private var nativeSourceSynced: Boolean = false

    override var sourceUrl: String? = sourceUrl
        private set

    override var seekOffsetSeconds: Double = 0.0
        private set

    override var durationOverrideSeconds: Double? = null
        private set

    private var nativeReportedDurationSeconds: Double = 0.0
    private var playingFallbackState: Boolean = false
    internal val stateChangeObservers = mutableListOf<() -> Unit>()

    override var volume: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 2f)
            ensureImpl()?.setVolume(if (isMuted) 0f else field)
            notifyStateChangeObservers()
        }

    override var isMuted: Boolean = false
        set(value) {
            field = value
            ensureImpl()?.setVolume(if (value) 0f else volume)
            notifyStateChangeObservers()
        }

    override var playbackSpeed: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.1f, 8.0f)
            notifyStateChangeObservers()
        }

    override var preloadMode: MediaPreloadMode = MediaPreloadMode.AUTO
        set(value) {
            field = value
            ensureImpl()?.setPreloadMode(value)
            notifyStateChangeObservers()
        }

    override var adaptivePreloadBufferingEnabled: Boolean = true
        set(value) {
            field = value
            ensureImpl()?.setAdaptivePreloadBufferingEnabled(value)
            notifyStateChangeObservers()
        }

    private var mediaReadyFallbackState: Boolean = false
    private val mediaReadyWaiters = mutableListOf<() -> Unit>()

    override val isMediaReady: Boolean
        get() = ensureImpl()?.isMediaReady() == true || mediaReadyFallbackState

    override var onMediaReady: (() -> Unit)? = null

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
     * Suspends until the current audio [sourceUrl] has loaded metadata/buffer and is ready to play,
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

    override var isBuffering: Boolean = false
        private set

    override var bufferedRanges: List<Pair<Double, Double>> = emptyList()
        get() {
            if (field.isNotEmpty()) return field
            return ensureImpl()?.getBufferedRanges().orEmpty()
        }
        private set

    override val bufferedEndSeconds: Double
        get() = bufferedRanges.maxOfOrNull { it.second } ?: 0.0

    override val estimatedNetworkSpeedRatio: Double
        get() = ensureImpl()?.getEstimatedNetworkSpeedRatio() ?: 1.0

    override fun getRequiredSafeBufferSeconds(): Double =
        ensureImpl()?.getRequiredSafeBufferSeconds() ?: 0.0

    override val currentTimeSeconds: Double
        get() = ensureImpl()?.getCurrentTimeSeconds() ?: seekOffsetSeconds

    override val durationSeconds: Double
        get() = durationOverrideSeconds
            ?: ensureImpl()?.getDurationSeconds()?.takeIf { it > 0.0 }
            ?: nativeReportedDurationSeconds

    override val isPlaying: Boolean
        get() = ensureImpl()?.isPlaying() ?: playingFallbackState

    override var onTimeUpdate: ((Double) -> Unit)? = null
    override var onDurationChanged: ((Double) -> Unit)? = null
    override var onPlayingStateChanged: ((Boolean) -> Unit)? = null
    override var onBufferingChanged: ((Boolean) -> Unit)? = null
    override var onBufferedRangesChanged: ((List<Pair<Double, Double>>) -> Unit)? = null
    override var onEnded: (() -> Unit)? = null
    override var onError: ((String) -> Unit)? = null

    private var attachedAnalyzer: AudioAnalyzer? = null

    init {
        if (!sourceUrl.isNullOrBlank()) {
            setSource(sourceUrl, 0.0, null)
        }
    }

    private fun ensureImpl(): AudioPlayerNativeImpl? {
        val existing = nativeImpl
        if (existing != null) return existing
        val created = MediaExtension.createAudioPlayerNative() ?: return null
        nativeImpl = created
        created.setPreloadMode(preloadMode)
        created.setAdaptivePreloadBufferingEnabled(adaptivePreloadBufferingEnabled)
        created.setVolume(if (isMuted) 0f else volume)
        created.setOnMediaReady {
            notifyMediaReady()
        }
        created.setOnTimeUpdate { timeSec ->
            seekOffsetSeconds = timeSec
            onTimeUpdate?.invoke(timeSec)
            notifyStateChangeObservers()
        }
        created.setOnDurationChanged { durSec ->
            nativeReportedDurationSeconds = durSec
            if (durSec > 0.0 && !mediaReadyFallbackState) {
                notifyMediaReady()
            }
            onDurationChanged?.invoke(durSec)
            notifyStateChangeObservers()
        }
        created.setOnBufferedRangesChanged { ranges ->
            bufferedRanges = ranges.toList()
            onBufferedRangesChanged?.invoke(bufferedRanges)
            notifyStateChangeObservers()
        }
        created.setOnEnded {
            playingFallbackState = false
            onPlayingStateChanged?.invoke(false)
            onEnded?.invoke()
            notifyStateChangeObservers()
        }
        created.setOnError { err ->
            playingFallbackState = false
            onError?.invoke(err)
            notifyStateChangeObservers()
        }
        attachedAnalyzer?.let { created.attachAudioAnalyzer(it) }
        val pendingUrl = sourceUrl
        if (!pendingUrl.isNullOrBlank() && !nativeSourceSynced) {
            nativeSourceSynced = true
            created.setSource(pendingUrl, seekOffsetSeconds)
            if (created.isMediaReady()) {
                notifyMediaReady()
            }
        }
        return created
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

    override fun getSupportedAudioCodecs(): Set<AudioCodec> =
        ensureImpl()?.getSupportedAudioCodecs() ?: emptySet()

    override fun isAudioCodecSupported(codec: AudioCodec): Boolean =
        ensureImpl()?.isAudioCodecSupported(codec) ?: false

    override fun setSource(url: String, seekOffsetSeconds: Double, durationOverrideSeconds: Double?) {
        this.sourceUrl = url
        this.seekOffsetSeconds = seekOffsetSeconds.coerceAtLeast(0.0)
        this.durationOverrideSeconds = durationOverrideSeconds
        this.mediaReadyFallbackState = false
        this.nativeSourceSynced = true
        val impl = ensureImpl()
        if (impl != null) {
            impl.setSource(url, this.seekOffsetSeconds)
            if (durationOverrideSeconds != null) {
                onDurationChanged?.invoke(durationOverrideSeconds)
            }
            if (impl.isMediaReady()) {
                notifyMediaReady()
            }
        } else {
            this.nativeSourceSynced = false
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
        val impl = ensureImpl()
        if (impl != null) {
            impl.play()
        }
        onPlayingStateChanged?.invoke(true)
        notifyStateChangeObservers()
    }

    override fun pause() {
        playingFallbackState = false
        val impl = ensureImpl()
        if (impl != null) {
            impl.pause()
        }
        onPlayingStateChanged?.invoke(false)
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
        val impl = ensureImpl()
        if (impl != null) {
            impl.stop()
        } else {
            onTimeUpdate?.invoke(0.0)
        }
        onPlayingStateChanged?.invoke(false)
        notifyStateChangeObservers()
    }

    override fun seekTo(seconds: Double) {
        val dur = durationSeconds
        val clamped = if (dur > 0.0) seconds.coerceIn(0.0, dur) else seconds.coerceAtLeast(0.0)
        seekOffsetSeconds = clamped
        val impl = ensureImpl()
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
        notifyStateChangeObservers()
    }

    override fun updateBufferedRanges(ranges: List<Pair<Double, Double>>) {
        bufferedRanges = ranges.toList()
        onBufferedRangesChanged?.invoke(bufferedRanges)
        notifyStateChangeObservers()
    }

    override fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        attachedAnalyzer = analyzer
        ensureImpl()?.attachAudioAnalyzer(analyzer)
    }

    override var isDisposed: Boolean = false
        private set

    override fun dispose() {
        isDisposed = true
        stop()
        nativeImpl?.dispose()
        nativeImpl = null
        nativeSourceSynced = false
    }
}
