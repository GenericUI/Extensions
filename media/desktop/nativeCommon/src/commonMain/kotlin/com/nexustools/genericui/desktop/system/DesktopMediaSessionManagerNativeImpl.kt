package com.nexustools.genericui.desktop.system

import com.nexustools.genericui.component.system.MediaSessionMetadata
import com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl

/**
 * Shared desktop base implementation of [MediaSessionManagerNativeImpl] for Windows SMTC
 * (`SystemMediaTransportControls`), macOS `MPNowPlayingInfoCenter`, and Linux `org.mpris.MediaPlayer2`.
 */
open class DesktopMediaSessionManagerNativeImpl(
    val backendToolkit: String = "Desktop"
) : MediaSessionManagerNativeImpl {

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
        currentMetadata = metadata
    }

    override fun updatePlaybackState(isPlaying: Boolean, positionSeconds: Double, durationSeconds: Double) {
        this.playing = isPlaying
        this.positionSeconds = positionSeconds
        this.durationSeconds = durationSeconds
    }

    override fun setTransportCallbacks(
        onPlay: (() -> Unit)?,
        onPause: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onPrevious: (() -> Unit)?,
        onSeekTo: ((Double) -> Unit)?
    ) {
        onPlayCallback = onPlay
        onPauseCallback = onPause
        onNextCallback = onNext
        onPreviousCallback = onPrevious
        onSeekToCallback = onSeekTo
    }

    override fun dispose() {
        isDisposed = true
    }
}
