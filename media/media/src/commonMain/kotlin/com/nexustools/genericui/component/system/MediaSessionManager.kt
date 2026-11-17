package com.nexustools.genericui.component.system

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl

/**
 * High-level manager for OS-level media transport controls and Now Playing metadata.
 */
class MediaSessionManager : Disposable {
    private var nativeImpl: MediaSessionManagerNativeImpl? =
        MediaExtension.createMediaSessionManagerNative()

    fun updateMetadata(metadata: MediaSessionMetadata) {
        nativeImpl?.updateMetadata(metadata)
    }

    fun updatePlaybackState(isPlaying: Boolean, positionSeconds: Double, durationSeconds: Double) {
        nativeImpl?.updatePlaybackState(isPlaying, positionSeconds, durationSeconds)
    }

    fun setTransportCallbacks(
        onPlay: (() -> Unit)? = null,
        onPause: (() -> Unit)? = null,
        onNext: (() -> Unit)? = null,
        onPrevious: (() -> Unit)? = null,
        onSeekTo: ((Double) -> Unit)? = null
    ) {
        nativeImpl?.setTransportCallbacks(onPlay, onPause, onNext, onPrevious, onSeekTo)
    }

    override fun dispose() {
        nativeImpl?.dispose()
        nativeImpl = null
    }
}
