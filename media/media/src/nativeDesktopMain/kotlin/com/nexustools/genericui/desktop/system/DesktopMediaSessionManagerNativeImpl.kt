package com.nexustools.genericui.desktop.system

import com.nexustools.genericui.component.system.MediaSessionMetadata
import com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl

/**
 * Shared desktop implementation of [MediaSessionManagerNativeImpl] delegating OS integration to
 * [DesktopMediaSessionPlatformBridge] (Windows `WH_KEYBOARD_LL` / taskbar progress, macOS
 * `MPNowPlayingInfoCenter` + `MPRemoteCommandCenter`, and Linux MPRIS2 D-Bus via `libgio`).
 */
open class DesktopMediaSessionManagerNativeImpl(
    val backendToolkit: String = "Desktop"
) : MediaSessionManagerNativeImpl {

    private val platformBridge = DesktopMediaSessionPlatformBridge(backendToolkit)

    override var isDisposed: Boolean = false
        protected set

    var currentMetadata: MediaSessionMetadata? = null
        protected set
    var playing: Boolean = false
        protected set
    var positionSeconds: Double = 0.0
        protected set
    var durationSeconds: Double = 0.0
        protected set

    protected var onPlayCallback: (() -> Unit)? = null
    protected var onPauseCallback: (() -> Unit)? = null
    protected var onNextCallback: (() -> Unit)? = null
    protected var onPreviousCallback: (() -> Unit)? = null
    protected var onSeekToCallback: ((Double) -> Unit)? = null

    override fun updateMetadata(metadata: MediaSessionMetadata) {
        if (isDisposed) return
        currentMetadata = metadata
        platformBridge.updateMetadata(metadata)
    }

    override fun updatePlaybackState(isPlaying: Boolean, positionSeconds: Double, durationSeconds: Double) {
        if (isDisposed) return
        this.playing = isPlaying
        this.positionSeconds = positionSeconds.coerceAtLeast(0.0)
        this.durationSeconds = durationSeconds.coerceAtLeast(0.0)
        platformBridge.updatePlaybackState(isPlaying, this.positionSeconds, this.durationSeconds)
    }

    override fun setTransportCallbacks(
        onPlay: (() -> Unit)?,
        onPause: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onPrevious: (() -> Unit)?,
        onSeekTo: ((Double) -> Unit)?
    ) {
        if (isDisposed) return
        onPlayCallback = onPlay
        onPauseCallback = onPause
        onNextCallback = onNext
        onPreviousCallback = onPrevious
        onSeekToCallback = onSeekTo
        platformBridge.setTransportCallbacks(onPlay, onPause, onNext, onPrevious, onSeekTo)
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        platformBridge.dispose()
        onPlayCallback = null
        onPauseCallback = null
        onNextCallback = null
        onPreviousCallback = null
        onSeekToCallback = null
        currentMetadata = null
    }
}
