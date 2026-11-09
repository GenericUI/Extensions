package com.nexustools.genericui.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import com.nexustools.genericui.component.system.MediaNotificationAction
import com.nexustools.genericui.component.system.MediaSessionMetadata
import com.nexustools.genericui.nativeimpl.system.MediaNotificationNativeImpl

/**
 * Android `MediaSession` + `Notification.MediaStyle` + foreground `Service` implementation
 * of [MediaNotificationNativeImpl].
 */
class AndroidMediaNotificationImpl : MediaNotificationNativeImpl {

    private var channelId: String = "genericui_media_playback"
    private var channelName: String = "Media Playback"
    private var channelDescription: String = "Ongoing media playback and casting controls"

    private var currentMetadata: MediaSessionMetadata = MediaSessionMetadata(title = "")
    private var isPlaying: Boolean = false
    private var isBuffering: Boolean = false
    private var positionSeconds: Double = 0.0
    private var durationSeconds: Double = 0.0
    private var actions: List<MediaNotificationAction> = emptyList()

    private var onPlay: (() -> Unit)? = null
    private var onPause: (() -> Unit)? = null
    private var onTogglePlayPause: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null
    private var onPrevious: (() -> Unit)? = null
    private var onStop: (() -> Unit)? = null
    private var onSeekTo: ((Double) -> Unit)? = null
    private var onAction: ((String) -> Unit)? = null

    private var activeNotificationId: Int = 1001
    private var isShowing: Boolean = false
    private var boundService: Service? = null
    private var mediaSession: MediaSession? = null
    private var actionReceiver: BroadcastReceiver? = null
    private var receiverContext: Context? = null

    override fun configureChannel(channelId: String, channelName: String, description: String) {
        this.channelId = channelId
        this.channelName = channelName
        this.channelDescription = description
        resolveContext()?.let { ensureNotificationChannel(it) }
    }

    override fun updateMetadata(metadata: MediaSessionMetadata, durationSeconds: Double) {
        this.currentMetadata = metadata
        if (durationSeconds >= 0.0) {
            this.durationSeconds = durationSeconds
        }
        refreshNotificationIfActive()
    }

    override fun updatePlaybackState(
        isPlaying: Boolean,
        isBuffering: Boolean,
        positionSeconds: Double,
        durationSeconds: Double
    ) {
        this.isPlaying = isPlaying
        this.isBuffering = isBuffering
        this.positionSeconds = positionSeconds.coerceAtLeast(0.0)
        this.durationSeconds = durationSeconds.coerceAtLeast(0.0)
        refreshNotificationIfActive()
    }

    override fun setActions(actions: List<MediaNotificationAction>) {
        this.actions = actions.toList()
        refreshNotificationIfActive()
    }

    override fun setCallbacks(
        onPlay: (() -> Unit)?,
        onPause: (() -> Unit)?,
        onTogglePlayPause: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onPrevious: (() -> Unit)?,
        onStop: (() -> Unit)?,
        onSeekTo: ((Double) -> Unit)?,
        onAction: ((String) -> Unit)?
    ) {
        this.onPlay = onPlay
        this.onPause = onPause
        this.onTogglePlayPause = onTogglePlayPause
        this.onNext = onNext
        this.onPrevious = onPrevious
        this.onStop = onStop
        this.onSeekTo = onSeekTo
        this.onAction = onAction
    }

    override fun show(notificationId: Int) {
        this.activeNotificationId = notificationId
        this.isShowing = true
        val context = resolveContext() ?: return
        runCatching {
            val notification = buildNotification(context) ?: return@runCatching
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(notificationId, notification)
        }
    }

    override fun bindForegroundService(service: Any, notificationId: Int) {
        this.activeNotificationId = notificationId
        this.isShowing = true
        val androidService = service as? Service
        if (androidService != null) {
            this.boundService = androidService
            runCatching {
                ensureNotificationChannel(androidService)
                val notification = buildNotification(androidService) ?: return@runCatching
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    androidService.startForeground(
                        notificationId,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    androidService.startForeground(notificationId, notification)
                }
            }
        } else {
            show(notificationId)
        }
    }

    override fun cancel(notificationId: Int) {
        isShowing = false
        val service = boundService
        if (service != null) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    service.stopForeground(true)
                }
            }
            boundService = null
        }
        val context = resolveContext() ?: return
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(notificationId)
        }
    }

    private fun resolveContext(): Context? =
        boundService ?: AndroidViewHandle.activeContext

    private fun refreshNotificationIfActive() {
        if (!isShowing && boundService == null) return
        val context = resolveContext() ?: return
        runCatching {
            val notification = buildNotification(context) ?: return@runCatching
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(activeNotificationId, notification)
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = channelDescription
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun ensureMediaSession(context: Context): MediaSession? = runCatching {
        val existing = mediaSession
        val session = if (existing != null) {
            existing
        } else {
            val appCtx = context.applicationContext ?: context
            MediaSession(appCtx, "GenericUIMediaSession").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        (this@AndroidMediaNotificationImpl.onPlay ?: onTogglePlayPause)?.invoke()
                    }

                    override fun onPause() {
                        (this@AndroidMediaNotificationImpl.onPause ?: onTogglePlayPause)?.invoke()
                    }

                    override fun onSkipToNext() {
                        onNext?.invoke()
                    }

                    override fun onSkipToPrevious() {
                        onPrevious?.invoke()
                    }

                    override fun onStop() {
                        this@AndroidMediaNotificationImpl.onStop?.invoke()
                    }

                    override fun onSeekTo(pos: Long) {
                        this@AndroidMediaNotificationImpl.onSeekTo?.invoke(pos.toDouble() / 1000.0)
                    }

                    override fun onCustomAction(action: String, extras: Bundle?) {
                        dispatchActionId(action)
                    }
                })
                isActive = true
                mediaSession = this
            }
        }

        val metaBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, currentMetadata.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, currentMetadata.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, currentMetadata.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, (durationSeconds * 1000.0).toLong().coerceAtLeast(0L))
        session.setMetadata(metaBuilder.build())

        val stateCode = when {
            isBuffering -> PlaybackState.STATE_BUFFERING
            isPlaying -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        val transportActions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SEEK_TO
        val pbState = PlaybackState.Builder()
            .setActions(transportActions)
            .setState(
                stateCode,
                (positionSeconds * 1000.0).toLong().coerceAtLeast(0L),
                if (isPlaying) 1.0f else 0.0f
            )
            .build()
        session.setPlaybackState(pbState)
        session
    }.getOrNull()

    private fun ensureBroadcastReceiver(context: Context) {
        if (actionReceiver != null) return
        runCatching {
            val appCtx = context.applicationContext ?: context
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action != ACTION_MEDIA_NOTIFICATION_BUTTON) return
                    val actionId = intent.getStringExtra(EXTRA_ACTION_ID) ?: return
                    dispatchActionId(actionId)
                }
            }
            val filter = IntentFilter(ACTION_MEDIA_NOTIFICATION_BUTTON)
            if (Build.VERSION.SDK_INT >= 33) {
                appCtx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appCtx.registerReceiver(receiver, filter)
            }
            actionReceiver = receiver
            receiverContext = appCtx
        }
    }

    private fun dispatchActionId(actionId: String) {
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

    private fun buildNotification(context: Context): Notification? = runCatching {
        ensureNotificationChannel(context)
        val session = ensureMediaSession(context)
        ensureBroadcastReceiver(context)

        val smallIcon = context.applicationInfo?.icon?.takeIf { it != 0 }
            ?: if (isPlaying) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        builder.setSmallIcon(smallIcon)
            .setContentTitle(currentMetadata.title.ifBlank { channelName })
            .setContentText(currentMetadata.artist.ifBlank { currentMetadata.album })
            .setOngoing(isPlaying || boundService != null)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        if (currentMetadata.album.isNotBlank() && currentMetadata.artist.isNotBlank()) {
            builder.setSubText(currentMetadata.album)
        }

        val launchIntent = context.packageManager?.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            launchIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val contentPi = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(contentPi)
        }

        val effectiveActions = actions.ifEmpty {
            listOf(
                MediaNotificationAction("prev", "Previous", android.R.drawable.ic_media_previous, true),
                MediaNotificationAction(
                    if (isPlaying) "pause" else "play",
                    if (isPlaying) "Pause" else "Play",
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    true
                ),
                MediaNotificationAction("next", "Next", android.R.drawable.ic_media_next, true)
            )
        }

        val compactIndices = mutableListOf<Int>()
        for ((index, action) in effectiveActions.withIndex()) {
            val iconRes = action.iconResId.takeIf { it != 0 } ?: resolveDefaultActionIcon(action.id, isPlaying)
            val broadcastIntent = Intent(ACTION_MEDIA_NOTIFICATION_BUTTON).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_ACTION_ID, action.id)
            }
            val actionPi = PendingIntent.getBroadcast(
                context,
                100 + index,
                broadcastIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notifAction = Notification.Action.Builder(iconRes, action.title, actionPi).build()
            builder.addAction(notifAction)
            if (action.showInCompactView && compactIndices.size < 3) {
                compactIndices.add(index)
            }
        }

        val mediaStyle = Notification.MediaStyle()
        if (session != null) {
            mediaStyle.setMediaSession(session.sessionToken)
        }
        if (compactIndices.isNotEmpty()) {
            mediaStyle.setShowActionsInCompactView(*compactIndices.toIntArray())
        }
        builder.setStyle(mediaStyle)
        builder.build()
    }.getOrNull()

    private fun resolveDefaultActionIcon(actionId: String, playing: Boolean): Int = when (actionId.lowercase()) {
        "prev", "previous", "skip_previous" -> android.R.drawable.ic_media_previous
        "next", "skip_next" -> android.R.drawable.ic_media_next
        "pause" -> android.R.drawable.ic_media_pause
        "play" -> android.R.drawable.ic_media_play
        "play_pause", "toggle_play_pause", "toggle" -> {
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        }
        "stop", "disconnect" -> android.R.drawable.ic_menu_close_clear_cancel
        else -> android.R.drawable.ic_media_play
    }

    override fun dispose() {
        cancel(activeNotificationId)
        val receiver = actionReceiver
        val ctx = receiverContext
        if (receiver != null && ctx != null) {
            runCatching { ctx.unregisterReceiver(receiver) }
        }
        actionReceiver = null
        receiverContext = null
        runCatching {
            mediaSession?.isActive = false
            mediaSession?.release()
        }
        mediaSession = null
    }

    companion object {
        const val ACTION_MEDIA_NOTIFICATION_BUTTON = "com.nexustools.genericui.MEDIA_NOTIFICATION_ACTION"
        const val EXTRA_ACTION_ID = "extra_action_id"
    }
}
