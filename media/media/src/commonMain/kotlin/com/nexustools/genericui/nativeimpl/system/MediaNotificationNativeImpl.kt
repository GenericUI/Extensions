package com.nexustools.genericui.nativeimpl.system

import com.nexustools.genericui.component.system.MediaNotificationAction
import com.nexustools.genericui.component.system.MediaSessionMetadata
import com.nexustools.genericui.core.Disposable

/**
 * Platform-native implementation contract for [com.nexustools.genericui.component.system.MediaNotification].
 */
interface MediaNotificationNativeImpl : Disposable {
    fun configureChannel(channelId: String, channelName: String, description: String)
    fun updateMetadata(metadata: MediaSessionMetadata, durationSeconds: Double)
    fun updatePlaybackState(isPlaying: Boolean, isBuffering: Boolean, positionSeconds: Double, durationSeconds: Double)
    fun setActions(actions: List<MediaNotificationAction>)
    fun setCallbacks(
        onPlay: (() -> Unit)?,
        onPause: (() -> Unit)?,
        onTogglePlayPause: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onPrevious: (() -> Unit)?,
        onStop: (() -> Unit)?,
        onSeekTo: ((Double) -> Unit)?,
        onAction: ((String) -> Unit)?
    )
    fun show(notificationId: Int)
    fun bindForegroundService(service: Any, notificationId: Int)
    fun cancel(notificationId: Int)
}
