package com.nexustools.genericui.web

import com.nexustools.genericui.component.system.MediaSessionMetadata
import com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl

/**
 * Web `navigator.mediaSession` implementation of [MediaSessionManagerNativeImpl].
 */
class WebMediaSessionManagerImpl : MediaSessionManagerNativeImpl {
    var currentMetadata: MediaSessionMetadata? = null
        private set
    var isPlaying: Boolean = false
        private set
    var positionSec: Double = 0.0
        private set
    var durationSec: Double = 0.0
        private set
    override var isDisposed: Boolean = false
        private set

    private var onPlay: (() -> Unit)? = null
    private var onPause: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null
    private var onPrevious: (() -> Unit)? = null
    private var onSeekTo: ((Double) -> Unit)? = null

    override fun updateMetadata(metadata: MediaSessionMetadata) {
        if (isDisposed) return
        currentMetadata = metadata
        WebMediaSessionBridge.syncMetadata(
            title = metadata.title,
            artist = metadata.artist,
            album = metadata.album,
            artworkUrl = metadata.artworkUrl ?: ""
        )
    }

    override fun updatePlaybackState(isPlaying: Boolean, positionSeconds: Double, durationSeconds: Double) {
        if (isDisposed) return
        this.isPlaying = isPlaying
        this.positionSec = positionSeconds.coerceAtLeast(0.0)
        this.durationSec = durationSeconds.coerceAtLeast(0.0)
        WebMediaSessionBridge.syncPlaybackState(
            playbackState = if (isPlaying) "playing" else "paused",
            positionSeconds = this.positionSec,
            durationSeconds = this.durationSec
        )
    }

    override fun setTransportCallbacks(
        onPlay: (() -> Unit)?,
        onPause: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onPrevious: (() -> Unit)?,
        onSeekTo: ((Double) -> Unit)?
    ) {
        if (isDisposed) return
        this.onPlay = onPlay
        this.onPause = onPause
        this.onNext = onNext
        this.onPrevious = onPrevious
        this.onSeekTo = onSeekTo
        WebMediaSessionBridge.installHandlers(
            onPlay = onPlay,
            onPause = onPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onSeekTo = onSeekTo
        )
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        WebMediaSessionBridge.clearSession()
        onPlay = null
        onPause = null
        onNext = null
        onPrevious = null
        onSeekTo = null
        currentMetadata = null
    }
}
