package com.nexustools.genericui.desktop.system

import com.nexustools.genericui.component.system.MediaSessionMetadata

/**
 * Per-OS native transport and Now Playing / MPRIS2 / Win32 bridge used by
 * [DesktopMediaSessionManagerNativeImpl].
 */
internal expect class DesktopMediaSessionPlatformBridge(backendToolkit: String) {
    fun updateMetadata(metadata: MediaSessionMetadata)
    fun updatePlaybackState(isPlaying: Boolean, positionSeconds: Double, durationSeconds: Double)
    fun setTransportCallbacks(
        onPlay: (() -> Unit)?,
        onPause: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onPrevious: (() -> Unit)?,
        onSeekTo: ((Double) -> Unit)?
    )
    fun dispose()
}
