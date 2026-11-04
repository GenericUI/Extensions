package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.MediaPreloadMode
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.nativeimpl.media.AudioPlayerNativeImpl
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Cross-platform audio player component supporting seek offsets, volume control,
 * and real-time [AudioAnalyzer] attachment.
 */
class AudioPlayer(
    sourceUrl: String? = null
) : AbstractComponent() {
    var sourceUrl: String? = sourceUrl
        private set

    var seekOffsetSeconds: Double = 0.0
        private set

    private var nativeReportedDurationSeconds: Double = 0.0
    private var playingFallbackState: Boolean = false
    internal val stateChangeObservers = mutableListOf<() -> Unit>()

    var volume: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 2f)
            typedNative<AudioPlayerNativeImpl<*>>()?.setVolume(field)
            notifyStateChangeObservers()
        }

    var preloadMode: MediaPreloadMode = MediaPreloadMode.AUTO
        set(value) {
            field = value
            typedNative<AudioPlayerNativeImpl<*>>()?.setPreloadMode(value)
            notifyStateChangeObservers()
        }

    var adaptivePreloadBufferingEnabled: Boolean = true
        set(value) {
            field = value
            typedNative<AudioPlayerNativeImpl<*>>()?.setAdaptivePreloadBufferingEnabled(value)
            notifyStateChangeObservers()
        }

    private var mediaReadyFallbackState: Boolean = false
    private val mediaReadyWaiters = mutableListOf<() -> Unit>()

    val isMediaReady: Boolean
        get() = typedNative<AudioPlayerNativeImpl<*>>()?.isMediaReady() == true || mediaReadyFallbackState

    var onMediaReady: (() -> Unit)? = null

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
    suspend fun awaitMediaReady(timeoutMs: Long = 8_000L): Boolean {
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

    var bufferedRanges: List<Pair<Double, Double>> = emptyList()
        get() {
            if (field.isNotEmpty()) return field
            return typedNative<AudioPlayerNativeImpl<*>>()?.getBufferedRanges().orEmpty()
        }
        private set

    val bufferedEndSeconds: Double
        get() = bufferedRanges.maxOfOrNull { it.second } ?: 0.0

    val estimatedNetworkSpeedRatio: Double
        get() = typedNative<AudioPlayerNativeImpl<*>>()?.getEstimatedNetworkSpeedRatio() ?: 1.0

    fun getRequiredSafeBufferSeconds(): Double =
        typedNative<AudioPlayerNativeImpl<*>>()?.getRequiredSafeBufferSeconds() ?: 0.0

    val currentTimeSeconds: Double
        get() = typedNative<AudioPlayerNativeImpl<*>>()?.getCurrentTimeSeconds() ?: seekOffsetSeconds

    val durationSeconds: Double
        get() = typedNative<AudioPlayerNativeImpl<*>>()?.getDurationSeconds()?.takeIf { it > 0.0 }
            ?: nativeReportedDurationSeconds

    val isPlaying: Boolean
        get() = typedNative<AudioPlayerNativeImpl<*>>()?.isPlaying() ?: playingFallbackState

    var onTimeUpdate: ((Double) -> Unit)? = null
    var onDurationChanged: ((Double) -> Unit)? = null
    var onBufferedRangesChanged: ((List<Pair<Double, Double>>) -> Unit)? = null
    var onEnded: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

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

    fun getSupportedAudioCodecs(): Set<AudioCodec> =
        typedNative<AudioPlayerNativeImpl<*>>()?.getSupportedAudioCodecs() ?: emptySet()

    fun setSource(url: String, seekOffsetSeconds: Double = 0.0) {
        this.sourceUrl = url
        this.seekOffsetSeconds = seekOffsetSeconds
        this.mediaReadyFallbackState = false
        val impl = typedNative<AudioPlayerNativeImpl<*>>()
        if (impl != null) {
            impl.setSource(url, seekOffsetSeconds)
            if (impl.isMediaReady()) {
                notifyMediaReady()
            }
        } else if (url.isNotBlank()) {
            notifyMediaReady()
        }
        notifyStateChangeObservers()
    }

    fun play() {
        playingFallbackState = true
        typedNative<AudioPlayerNativeImpl<*>>()?.play()
        notifyStateChangeObservers()
    }

    fun pause() {
        playingFallbackState = false
        typedNative<AudioPlayerNativeImpl<*>>()?.pause()
        notifyStateChangeObservers()
    }

    fun stop() {
        playingFallbackState = false
        seekOffsetSeconds = 0.0
        typedNative<AudioPlayerNativeImpl<*>>()?.stop()
        notifyStateChangeObservers()
    }

    fun seekTo(seconds: Double) {
        val dur = durationSeconds
        val clamped = if (dur > 0.0) seconds.coerceIn(0.0, dur) else seconds.coerceAtLeast(0.0)
        seekOffsetSeconds = clamped
        val impl = typedNative<AudioPlayerNativeImpl<*>>()
        if (impl != null) {
            impl.seekTo(clamped)
        } else {
            onTimeUpdate?.invoke(clamped)
        }
        notifyStateChangeObservers()
    }

    fun updateBufferedRanges(ranges: List<Pair<Double, Double>>) {
        bufferedRanges = ranges.toList()
        onBufferedRangesChanged?.invoke(bufferedRanges)
        notifyStateChangeObservers()
    }

    fun attachAudioAnalyzer(analyzer: AudioAnalyzer?) {
        attachedAnalyzer = analyzer
        typedNative<AudioPlayerNativeImpl<*>>()?.attachAudioAnalyzer(analyzer)
    }

    protected override fun createNativeImpl(factory: NativeFactory): AudioPlayerNativeImpl<*> =
        MediaExtension.createAudioPlayerNativeOrFallback()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<AudioPlayerNativeImpl<*>>() ?: return
        impl.setPreloadMode(preloadMode)
        impl.setAdaptivePreloadBufferingEnabled(adaptivePreloadBufferingEnabled)
        impl.setVolume(volume)
        impl.setOnMediaReady {
            notifyMediaReady()
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
        impl.setOnBufferedRangesChanged { ranges ->
            bufferedRanges = ranges.toList()
            onBufferedRangesChanged?.invoke(bufferedRanges)
            notifyStateChangeObservers()
        }
        impl.setOnEnded {
            playingFallbackState = false
            onEnded?.invoke()
            notifyStateChangeObservers()
        }
        impl.setOnError { err ->
            playingFallbackState = false
            onError?.invoke(err)
            notifyStateChangeObservers()
        }
        attachedAnalyzer?.let { impl.attachAudioAnalyzer(it) }
        sourceUrl?.let { impl.setSource(it, seekOffsetSeconds) }
    }
}
