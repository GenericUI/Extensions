package com.nexustools.genericui.web

import com.nexustools.genericui.component.system.MediaSessionMetadata
import com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl

/**
 * Web `navigator.mediaSession` implementation of [MediaSessionManagerNativeImpl].
 */
class WebMediaSessionManagerImpl : MediaSessionManagerNativeImpl {
    private var currentMetadata: MediaSessionMetadata? = null
    private var isPlaying: Boolean = false
    private var positionSec: Double = 0.0
    private var durationSec: Double = 0.0

    override fun updateMetadata(metadata: MediaSessionMetadata) {
        currentMetadata = metadata
    }

    override fun updatePlaybackState(isPlaying: Boolean, positionSeconds: Double, durationSeconds: Double) {
        this.isPlaying = isPlaying
        this.positionSec = positionSeconds
        this.durationSec = durationSeconds
    }

    override fun setTransportCallbacks(
        onPlay: (() -> Unit)?,
        onPause: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onPrevious: (() -> Unit)?,
        onSeekTo: ((Double) -> Unit)?
    ) {}

    override fun dispose() {
        currentMetadata = null
    }
}
