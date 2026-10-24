package com.nexustools.genericui.swing

import com.nexustools.genericui.component.system.MediaSessionMetadata
import com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl

/**
 * Java Swing implementation of [MediaSessionManagerNativeImpl].
 */
class SwingMediaSessionManagerImpl : MediaSessionManagerNativeImpl {
    override fun updateMetadata(metadata: MediaSessionMetadata) {}
    override fun updatePlaybackState(isPlaying: Boolean, positionSeconds: Double, durationSeconds: Double) {}
    override fun setTransportCallbacks(
        onPlay: (() -> Unit)?,
        onPause: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onPrevious: (() -> Unit)?,
        onSeekTo: ((Double) -> Unit)?
    ) {}
    override fun dispose() {}
}
