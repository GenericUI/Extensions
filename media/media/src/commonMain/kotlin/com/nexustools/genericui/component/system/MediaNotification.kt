package com.nexustools.genericui.component.system

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.nativeimpl.system.MediaNotificationNativeImpl

/**
 * First-class multiplatform media playback and casting notification manager.
 *
 * On Android, integrates with `android.media.session.MediaSession`, `Notification.MediaStyle`,
 * `NotificationChannel`, and foreground `Service.startForeground(..., FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)`.
 */
class MediaNotification(
    initialChannelId: String = "genericui_media_playback",
    initialChannelName: String = "Media Playback",
    initialDescription: String = "Ongoing media playback and casting controls"
) : Disposable {

    private var nativeImpl: MediaNotificationNativeImpl? =
        MediaExtension.createMediaNotificationNative()

    var channelId: String = initialChannelId
        private set

    var channelName: String = initialChannelName
        private set

    var channelDescription: String = initialDescription
        private set

    var metadata: MediaSessionMetadata = MediaSessionMetadata(title = "")
        private set

    var isPlaying: Boolean = false
        private set

    var isBuffering: Boolean = false
        private set

    var positionSeconds: Double = 0.0
        private set

    var durationSeconds: Double = 0.0
        private set

    var actions: List<MediaNotificationAction> = emptyList()
        private set

    var isShowing: Boolean = false
        private set

    var activeNotificationId: Int = 1001
        private set

    var onPlay: (() -> Unit)? = null
        set(value) {
            field = value
            syncCallbacksToNative()
        }

    var onPause: (() -> Unit)? = null
        set(value) {
            field = value
            syncCallbacksToNative()
        }

    var onTogglePlayPause: (() -> Unit)? = null
        set(value) {
            field = value
            syncCallbacksToNative()
        }

    var onNext: (() -> Unit)? = null
        set(value) {
            field = value
            syncCallbacksToNative()
        }

    var onPrevious: (() -> Unit)? = null
        set(value) {
            field = value
            syncCallbacksToNative()
        }

    var onStop: (() -> Unit)? = null
        set(value) {
            field = value
            syncCallbacksToNative()
        }

    var onSeekTo: ((Double) -> Unit)? = null
        set(value) {
            field = value
            syncCallbacksToNative()
        }

    var onAction: ((String) -> Unit)? = null
        set(value) {
            field = value
            syncCallbacksToNative()
        }

    init {
        nativeImpl?.configureChannel(channelId, channelName, channelDescription)
    }

    fun configureChannel(channelId: String, channelName: String, description: String = "") {
        this.channelId = channelId
        this.channelName = channelName
        this.channelDescription = description
        nativeImpl?.configureChannel(channelId, channelName, description)
    }

    fun updateMetadata(metadata: MediaSessionMetadata, durationSeconds: Double = this.durationSeconds) {
        this.metadata = metadata
        if (durationSeconds >= 0.0) {
            this.durationSeconds = durationSeconds
        }
        nativeImpl?.updateMetadata(metadata, this.durationSeconds)
    }

    fun updatePlaybackState(
        isPlaying: Boolean,
        isBuffering: Boolean = false,
        positionSeconds: Double = this.positionSeconds,
        durationSeconds: Double = this.durationSeconds
    ) {
        this.isPlaying = isPlaying
        this.isBuffering = isBuffering
        this.positionSeconds = positionSeconds.coerceAtLeast(0.0)
        this.durationSeconds = durationSeconds.coerceAtLeast(0.0)
        nativeImpl?.updatePlaybackState(
            isPlaying = this.isPlaying,
            isBuffering = this.isBuffering,
            positionSeconds = this.positionSeconds,
            durationSeconds = this.durationSeconds
        )
    }

    fun setActions(actions: List<MediaNotificationAction>) {
        this.actions = actions.toList()
        nativeImpl?.setActions(this.actions)
    }

    fun setCallbacks(
        onPlay: (() -> Unit)? = this.onPlay,
        onPause: (() -> Unit)? = this.onPause,
        onTogglePlayPause: (() -> Unit)? = this.onTogglePlayPause,
        onNext: (() -> Unit)? = this.onNext,
        onPrevious: (() -> Unit)? = this.onPrevious,
        onStop: (() -> Unit)? = this.onStop,
        onSeekTo: ((Double) -> Unit)? = this.onSeekTo,
        onAction: ((String) -> Unit)? = this.onAction
    ) {
        this.onPlay = onPlay
        this.onPause = onPause
        this.onTogglePlayPause = onTogglePlayPause
        this.onNext = onNext
        this.onPrevious = onPrevious
        this.onStop = onStop
        this.onSeekTo = onSeekTo
        this.onAction = onAction
        syncCallbacksToNative()
    }

    fun show(notificationId: Int = activeNotificationId) {
        activeNotificationId = notificationId
        isShowing = true
        nativeImpl?.show(notificationId)
    }

    fun bindForegroundService(service: Any, notificationId: Int = activeNotificationId) {
        activeNotificationId = notificationId
        isShowing = true
        nativeImpl?.bindForegroundService(service, notificationId)
    }

    fun cancel(notificationId: Int = activeNotificationId) {
        isShowing = false
        nativeImpl?.cancel(notificationId)
    }

    /**
     * Triggers an action by its [actionId], invoking both the matching standard transport callback
     * and [onAction] if registered.
     */
    fun triggerAction(actionId: String) {
        when (actionId.lowercase()) {
            "play" -> (onPlay ?: onTogglePlayPause)?.invoke()
            "pause" -> (onPause ?: onTogglePlayPause)?.invoke()
            "play_pause", "toggle_play_pause", "toggle" -> {
                val toggle = onTogglePlayPause
                if (toggle != null) {
                    toggle.invoke()
                } else if (isPlaying) {
                    onPause?.invoke()
                } else {
                    onPlay?.invoke()
                }
            }
            "next", "skip_next" -> onNext?.invoke()
            "prev", "previous", "skip_previous" -> onPrevious?.invoke()
            "stop", "disconnect" -> onStop?.invoke()
        }
        onAction?.invoke(actionId)
    }

    private fun syncCallbacksToNative() {
        nativeImpl?.setCallbacks(
            onPlay = onPlay,
            onPause = onPause,
            onTogglePlayPause = onTogglePlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onStop = onStop,
            onSeekTo = onSeekTo,
            onAction = onAction
        )
    }

    override fun dispose() {
        isShowing = false
        nativeImpl?.dispose()
        nativeImpl = null
    }
}
