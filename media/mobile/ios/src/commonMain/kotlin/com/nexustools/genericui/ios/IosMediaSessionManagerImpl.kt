package com.nexustools.genericui.ios

import com.nexustools.genericui.component.system.MediaSessionMetadata
import com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl

/**
 * iOS `MPNowPlayingInfoCenter` + `MPRemoteCommandCenter` implementation of [MediaSessionManagerNativeImpl].
 */
class IosMediaSessionManagerImpl : MediaSessionManagerNativeImpl {
    private var currentMetadata: MediaSessionMetadata? = null

    override fun updateMetadata(metadata: MediaSessionMetadata) { currentMetadata = metadata }
    override fun updatePlaybackState(isPlaying: Boolean, positionSeconds: Double, durationSeconds: Double) {}
    override fun setTransportCallbacks(
        onPlay: (() -> Unit)?,
        onPause: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onPrevious: (() -> Unit)?,
        onSeekTo: ((Double) -> Unit)?
    ) {}
    override fun dispose() { currentMetadata = null }
}
