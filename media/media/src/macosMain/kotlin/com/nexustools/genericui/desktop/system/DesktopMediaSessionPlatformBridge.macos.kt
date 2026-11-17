package com.nexustools.genericui.desktop.system

import com.nexustools.genericui.apple.AppleMediaSessionBridge
import com.nexustools.genericui.component.system.MediaSessionMetadata

/**
 * macOS actual [DesktopMediaSessionPlatformBridge] backed by [AppleMediaSessionBridge]
 * (`MPNowPlayingInfoCenter` + `MPRemoteCommandCenter`).
 */
internal actual class DesktopMediaSessionPlatformBridge actual constructor(
    private val backendToolkit: String
) {
    private val appleBridge = AppleMediaSessionBridge()

    actual fun updateMetadata(metadata: MediaSessionMetadata) {
        appleBridge.updateMetadata(metadata)
    }

    actual fun updatePlaybackState(isPlaying: Boolean, positionSeconds: Double, durationSeconds: Double) {
        appleBridge.updatePlaybackState(isPlaying, positionSeconds, durationSeconds)
    }

    actual fun setTransportCallbacks(
        onPlay: (() -> Unit)?,
        onPause: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onPrevious: (() -> Unit)?,
        onSeekTo: ((Double) -> Unit)?
    ) {
        appleBridge.setTransportCallbacks(onPlay, onPause, onNext, onPrevious, onSeekTo)
    }

    actual fun dispose() {
        appleBridge.dispose()
    }
}
