package com.nexustools.genericui.ext.mediacontrols.android

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.view.KeyEvent
import com.nexustools.genericui.android.AndroidViewHandle
import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlButtonConfig
import com.nexustools.genericui.ext.mediacontrols.MediaControlEventSource
import com.nexustools.genericui.ext.mediacontrols.MediaControlListener
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaControlsCapabilities
import com.nexustools.genericui.ext.mediacontrols.MediaControlsNativeImpl
import com.nexustools.genericui.ext.mediacontrols.MediaControlsStateSupport
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus
import com.nexustools.genericui.window.IWindow

/**
 * Android implementation of [MediaControlsNativeImpl] backed by `android.media.session.MediaSession`,
 * [MediaMetadata], [PlaybackState], and hardware/Bluetooth media button [KeyEvent] dispatching.
 */
class AndroidMediaControlsImpl(
    contextProvider: () -> Context? = { AndroidViewHandle.activeContext },
    private val sessionTag: String = "GenericUIMediaControls"
) : MediaControlsNativeImpl {

    override val capabilities: MediaControlsCapabilities = MediaControlsCapabilities(
        supportsSystemTransportControls = true,
        supportsTaskbarThumbnailButtons = false,
        supportsKeyboardMediaKeyHook = true
    )

    private val state = MediaControlsStateSupport(capabilities)
    private val resolveContext: () -> Context? = contextProvider
    private var platformSession: MediaSession? = null

    val currentMetadata: MediaControlMetadata
        get() = state.currentMetadata

    val currentPlaybackStatus: MediaPlaybackStatus
        get() = state.currentPlaybackStatus

    val currentPositionMs: Long
        get() = state.currentPositionMs

    val currentDurationMs: Long
        get() = state.currentDurationMs

    val currentPlaybackRate: Float
        get() = state.currentPlaybackRate

    val currentButtonConfig: MediaControlButtonConfig
        get() = state.currentButtonConfig

    val keyboardMediaKeysEnabled: Boolean
        get() = state.keyboardMediaKeysEnabled

    val hasPlatformSession: Boolean
        get() = platformSession != null

    override val isDisposed: Boolean
        get() = state.isDisposed

    private val sessionCallback = object : MediaSession.Callback() {
        override fun onPlay() {
            dispatchTransportAction(MediaControlAction.PLAY)
        }

        override fun onPause() {
            dispatchTransportAction(MediaControlAction.PAUSE)
        }

        override fun onStop() {
            dispatchTransportAction(MediaControlAction.STOP)
        }

        override fun onSkipToNext() {
            dispatchTransportAction(MediaControlAction.NEXT)
        }

        override fun onSkipToPrevious() {
            dispatchTransportAction(MediaControlAction.PREVIOUS)
        }

        override fun onFastForward() {
            dispatchTransportAction(MediaControlAction.FAST_FORWARD)
        }

        override fun onRewind() {
            dispatchTransportAction(MediaControlAction.REWIND)
        }

        override fun onSeekTo(pos: Long) {
            dispatchTransportAction(MediaControlAction.SEEK_TO, pos.coerceAtLeast(0L))
        }

        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
            val keyEvent = extractKeyEvent(mediaButtonIntent) ?: return super.onMediaButtonEvent(mediaButtonIntent)
            return if (handleAndroidKeyEvent(keyEvent)) {
                true
            } else {
                super.onMediaButtonEvent(mediaButtonIntent)
            }
        }
    }

    override fun bindWindow(window: IWindow?) {
        state.bindWindow(window)
    }

    override fun setActive(active: Boolean) {
        if (isDisposed) return
        state.setActive(active)
        if (active) {
            ensurePlatformSession()
            runCatching { platformSession?.isActive = true }
            syncPlatformMetadata()
            syncPlatformPlaybackState()
        } else {
            runCatching { platformSession?.isActive = false }
        }
    }

    override fun isActive(): Boolean = state.isActive()

    override fun setKeyboardMediaKeysEnabled(enabled: Boolean) {
        state.setKeyboardMediaKeysEnabled(enabled)
    }

    override fun setTaskbarPreviewControlsEnabled(enabled: Boolean) {
        state.setTaskbarPreviewControlsEnabled(enabled)
    }

    override fun updateMetadata(metadata: MediaControlMetadata) {
        state.updateMetadata(metadata)
        syncPlatformMetadata()
    }

    override fun updatePlaybackStatus(
        status: MediaPlaybackStatus,
        positionMs: Long,
        durationMs: Long,
        playbackRate: Float
    ) {
        state.updatePlaybackStatus(status, positionMs, durationMs, playbackRate)
        syncPlatformPlaybackState()
    }

    override fun updateButtonConfig(config: MediaControlButtonConfig) {
        state.updateButtonConfig(config)
        syncPlatformPlaybackState()
    }

    override fun setListener(listener: MediaControlListener?) {
        state.setListener(listener)
    }

    override fun simulateMediaKeyEvent(
        action: MediaControlAction,
        source: MediaControlEventSource,
        seekPositionMs: Long?
    ) {
        state.dispatchEvent(action, source, seekPositionMs)
    }

    fun handleAndroidKeyEvent(event: KeyEvent): Boolean {
        val action = runCatching { event.action }.getOrElse { return false }
        val keyCode = runCatching { event.keyCode }.getOrElse { return false }
        return handleAndroidKeyCode(keyCode, action)
    }

    fun handleAndroidKeyCode(keyCode: Int, keyAction: Int = KeyEvent.ACTION_DOWN): Boolean {
        if (keyAction != KeyEvent.ACTION_DOWN) return false
        if (!isActive() || !state.keyboardMediaKeysEnabled) return false
        val mapped = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> MediaControlAction.PLAY_PAUSE
            KeyEvent.KEYCODE_MEDIA_PLAY -> MediaControlAction.PLAY
            KeyEvent.KEYCODE_MEDIA_PAUSE -> MediaControlAction.PAUSE
            KeyEvent.KEYCODE_MEDIA_STOP -> MediaControlAction.STOP
            KeyEvent.KEYCODE_MEDIA_NEXT -> MediaControlAction.NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> MediaControlAction.PREVIOUS
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> MediaControlAction.FAST_FORWARD
            KeyEvent.KEYCODE_MEDIA_REWIND -> MediaControlAction.REWIND
            KeyEvent.KEYCODE_VOLUME_MUTE -> MediaControlAction.MUTE_TOGGLE
            KeyEvent.KEYCODE_VOLUME_UP -> MediaControlAction.VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> MediaControlAction.VOLUME_DOWN
            else -> return false
        }
        return state.dispatchEvent(mapped, MediaControlEventSource.KEYBOARD_MEDIA_KEY)
    }

    fun dispatchTransportAction(
        action: MediaControlAction,
        seekPositionMs: Long? = null
    ): Boolean {
        if (!isActive()) return false
        val cfg = state.currentButtonConfig
        val allowed = when (action) {
            MediaControlAction.PLAY,
            MediaControlAction.PAUSE,
            MediaControlAction.PLAY_PAUSE -> cfg.playPauseEnabled
            MediaControlAction.NEXT -> cfg.nextEnabled
            MediaControlAction.PREVIOUS -> cfg.previousEnabled
            MediaControlAction.STOP -> cfg.stopEnabled
            MediaControlAction.SEEK_TO,
            MediaControlAction.FAST_FORWARD,
            MediaControlAction.REWIND -> cfg.seekEnabled
            MediaControlAction.MUTE_TOGGLE,
            MediaControlAction.VOLUME_UP,
            MediaControlAction.VOLUME_DOWN -> true
        }
        if (!allowed) return false
        return state.dispatchEvent(action, MediaControlEventSource.SYSTEM_TRANSPORT, seekPositionMs)
    }

    fun computeAllowedPlaybackActions(): Long {
        val cfg = state.currentButtonConfig
        var actions = 0L
        if (cfg.playPauseEnabled) {
            actions = actions or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE
        }
        if (cfg.nextEnabled) {
            actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        }
        if (cfg.previousEnabled) {
            actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        }
        if (cfg.stopEnabled) {
            actions = actions or PlaybackState.ACTION_STOP
        }
        if (cfg.seekEnabled) {
            actions = actions or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND
        }
        return actions
    }

    fun toAndroidPlaybackStateCode(status: MediaPlaybackStatus): Int = when (status) {
        MediaPlaybackStatus.PLAYING -> PlaybackState.STATE_PLAYING
        MediaPlaybackStatus.PAUSED -> PlaybackState.STATE_PAUSED
        MediaPlaybackStatus.LOADING -> PlaybackState.STATE_BUFFERING
        MediaPlaybackStatus.STOPPED -> PlaybackState.STATE_STOPPED
    }

    private fun ensurePlatformSession() {
        if (platformSession != null) return
        val ctx = resolveContext() ?: return
        AndroidViewHandle.runOnMainThread {
            if (platformSession != null) return@runOnMainThread
            runCatching {
                val created = MediaSession(ctx, sessionTag)
                created.setCallback(sessionCallback)
                platformSession = created
            }
        }
    }

    private fun syncPlatformMetadata() {
        val session = platformSession ?: return
        val meta = state.currentMetadata
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val builder = MediaMetadata.Builder()
                if (meta.title.isNotEmpty()) {
                    builder.putString(MediaMetadata.METADATA_KEY_TITLE, meta.title)
                }
                if (meta.artist.isNotEmpty()) {
                    builder.putString(MediaMetadata.METADATA_KEY_ARTIST, meta.artist)
                }
                if (meta.album.isNotEmpty()) {
                    builder.putString(MediaMetadata.METADATA_KEY_ALBUM, meta.album)
                }
                if (meta.subtitle.isNotEmpty()) {
                    builder.putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, meta.subtitle)
                }
                if (meta.durationMs > 0L) {
                    builder.putLong(MediaMetadata.METADATA_KEY_DURATION, meta.durationMs)
                }
                meta.artwork?.let { img ->
                    if (img.width > 0 && img.height > 0) {
                        val pixels = IntArray(img.width * img.height)
                        for (y in 0 until img.height) {
                            for (x in 0 until img.width) {
                                val px = img.getPixel(x, y)
                                pixels[y * img.width + x] = ((px.a and 0xFF) shl 24) or
                                    ((px.r and 0xFF) shl 16) or
                                    ((px.g and 0xFF) shl 8) or
                                    (px.b and 0xFF)
                            }
                        }
                        val bmp = Bitmap.createBitmap(pixels, img.width, img.height, Bitmap.Config.ARGB_8888)
                        builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bmp)
                    }
                }
                session.setMetadata(builder.build())
            }
        }
    }

    private fun syncPlatformPlaybackState() {
        val session = platformSession ?: return
        val statusCode = toAndroidPlaybackStateCode(state.currentPlaybackStatus)
        val pos = state.currentPositionMs.coerceAtLeast(0L)
        val rate = if (state.currentPlaybackStatus == MediaPlaybackStatus.PLAYING) {
            state.currentPlaybackRate
        } else {
            0.0f
        }
        val actions = computeAllowedPlaybackActions()
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val pbState = PlaybackState.Builder()
                    .setActions(actions)
                    .setState(statusCode, pos, rate)
                    .build()
                session.setPlaybackState(pbState)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun extractKeyEvent(intent: Intent): KeyEvent? = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }
    }.getOrNull()

    override fun dispose() {
        if (isDisposed) return
        val session = platformSession
        platformSession = null
        if (session != null) {
            AndroidViewHandle.runOnMainThread {
                runCatching {
                    session.isActive = false
                    session.setCallback(null)
                    session.release()
                }
            }
        }
        state.markDisposed()
    }
}
