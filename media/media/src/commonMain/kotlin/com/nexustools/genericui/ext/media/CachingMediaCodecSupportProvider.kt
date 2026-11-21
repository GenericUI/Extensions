@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.media

import com.nexustools.genericui.component.media.MediaCapabilitiesReport
import com.nexustools.genericui.enums.media.AudioCodec
import com.nexustools.genericui.enums.media.VideoCodec
import kotlin.concurrent.atomics.AtomicReference

/**
 * Lock-free [MediaCodecSupportProvider] base for non-LibVLC platforms (`Android`, `iOS`, `Web`).
 *
 * Uses copy-on-write [AtomicReference] caches so OS / DOM capability probes execute at most once
 * per codec and once for the aggregated [MediaCapabilitiesReport], without instantiating players.
 */
abstract class CachingMediaCodecSupportProvider : MediaCodecSupportProvider {
    private val cachedCapabilitiesRef = AtomicReference<MediaCapabilitiesReport?>(null)
    private val cachedVideoCodecSupportRef = AtomicReference<Map<VideoCodec, Boolean>>(emptyMap())
    private val cachedAudioCodecSupportRef = AtomicReference<Map<AudioCodec, Boolean>>(emptyMap())

    protected abstract fun probeMediaCapabilities(): MediaCapabilitiesReport

    protected abstract fun probeVideoCodecSupported(codec: VideoCodec): Boolean

    protected abstract fun probeAudioCodecSupported(codec: AudioCodec): Boolean

    override fun queryMediaCapabilities(): MediaCapabilitiesReport {
        cachedCapabilitiesRef.load()?.let { return it }
        val computed = probeMediaCapabilities()
        while (true) {
            val existing = cachedCapabilitiesRef.load()
            if (existing != null) return existing
            if (cachedCapabilitiesRef.compareAndSet(null, computed)) {
                return computed
            }
        }
    }

    override fun isVideoCodecSupported(codec: VideoCodec): Boolean {
        cachedVideoCodecSupportRef.load()[codec]?.let { return it }
        val supported = probeVideoCodecSupported(codec)
        while (true) {
            val current = cachedVideoCodecSupportRef.load()
            current[codec]?.let { return it }
            val updated = LinkedHashMap<VideoCodec, Boolean>(current.size + 1).apply {
                putAll(current)
                put(codec, supported)
            }
            if (cachedVideoCodecSupportRef.compareAndSet(current, updated)) {
                return supported
            }
        }
    }

    override fun isAudioCodecSupported(codec: AudioCodec): Boolean {
        cachedAudioCodecSupportRef.load()[codec]?.let { return it }
        val supported = probeAudioCodecSupported(codec)
        while (true) {
            val current = cachedAudioCodecSupportRef.load()
            current[codec]?.let { return it }
            val updated = LinkedHashMap<AudioCodec, Boolean>(current.size + 1).apply {
                putAll(current)
                put(codec, supported)
            }
            if (cachedAudioCodecSupportRef.compareAndSet(current, updated)) {
                return supported
            }
        }
    }

    override fun getSupportedVideoCodecs(): Set<VideoCodec> =
        VideoCodec.entries.filterTo(linkedSetOf()) { isVideoCodecSupported(it) }

    override fun getSupportedAudioCodecs(): Set<AudioCodec> =
        AudioCodec.entries.filterTo(linkedSetOf()) { isAudioCodecSupported(it) }
}
