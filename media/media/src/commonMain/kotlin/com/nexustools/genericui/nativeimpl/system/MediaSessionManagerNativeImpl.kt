package com.nexustools.genericui.nativeimpl.system

import com.nexustools.genericui.component.system.MediaSessionMetadata
import com.nexustools.genericui.core.Disposable

interface MediaSessionManagerNativeImpl : Disposable {
    fun updateMetadata(metadata: MediaSessionMetadata)
    fun updatePlaybackState(isPlaying: Boolean, positionSeconds: Double, durationSeconds: Double)
    fun setTransportCallbacks(
        onPlay: (() -> Unit)?,
        onPause: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onPrevious: (() -> Unit)?,
        onSeekTo: ((Double) -> Unit)?
    )
}
