package com.nexustools.genericui.android

import com.nexustools.genericui.component.system.MediaSessionMetadata
import com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl

/**
 * Android `android.media.session.MediaSession` / `androidx.media3.session.MediaSession` implementation of [MediaSessionManagerNativeImpl].
 */
class AndroidMediaSessionManagerImpl : MediaSessionManagerNativeImpl {
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
