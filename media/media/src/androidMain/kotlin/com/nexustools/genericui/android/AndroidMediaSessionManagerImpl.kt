package com.nexustools.genericui.android

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import com.nexustools.genericui.component.system.MediaSessionMetadata
import com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl

/**
 * Android `android.media.session.MediaSession` implementation of [MediaSessionManagerNativeImpl].
 */
class AndroidMediaSessionManagerImpl(
    contextProvider: () -> Context? = { AndroidViewHandle.activeContext },
    private val sessionTag: String = "GenericUIMediaSession"
) : MediaSessionManagerNativeImpl {

    private val resolveContext: () -> Context? = contextProvider
    private var platformSession: MediaSession? = null
    private var currentMetadata: MediaSessionMetadata? = null
    private var isPlaying: Boolean = false
    private var positionSec: Double = 0.0
    private var durationSec: Double = 0.0

    private var onPlay: (() -> Unit)? = null
    private var onPause: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null
    private var onPrevious: (() -> Unit)? = null
    private var onSeekTo: ((Double) -> Unit)? = null

    override var isDisposed: Boolean = false
        private set

    private val sessionCallback = object : MediaSession.Callback() {
        override fun onPlay() {
            onPlay?.invoke()
        }

        override fun onPause() {
            onPause?.invoke()
        }

        override fun onSkipToNext() {
            onNext?.invoke()
        }

        override fun onSkipToPrevious() {
            onPrevious?.invoke()
        }

        override fun onSeekTo(pos: Long) {
            onSeekTo?.invoke(pos.coerceAtLeast(0L) / 1000.0)
        }
    }

    override fun updateMetadata(metadata: MediaSessionMetadata) {
        if (isDisposed) return
        currentMetadata = metadata
        ensurePlatformSession()
        syncMetadata()
    }

    override fun updatePlaybackState(isPlaying: Boolean, positionSeconds: Double, durationSeconds: Double) {
        if (isDisposed) return
        this.isPlaying = isPlaying
        this.positionSec = positionSeconds.coerceAtLeast(0.0)
        this.durationSec = durationSeconds.coerceAtLeast(0.0)
        ensurePlatformSession()
        runCatching { platformSession?.isActive = true }
        syncMetadata()
        syncPlaybackState()
    }

    override fun setTransportCallbacks(
        onPlay: (() -> Unit)?,
        onPause: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onPrevious: (() -> Unit)?,
        onSeekTo: ((Double) -> Unit)?
    ) {
        if (isDisposed) return
        this.onPlay = onPlay
        this.onPause = onPause
        this.onNext = onNext
        this.onPrevious = onPrevious
        this.onSeekTo = onSeekTo
        ensurePlatformSession()
        syncPlaybackState()
    }

    fun dispatchRemotePlay(): Boolean {
        val cb = onPlay ?: return false
        cb()
        return true
    }

    fun dispatchRemotePause(): Boolean {
        val cb = onPause ?: return false
        cb()
        return true
    }

    fun dispatchRemoteNext(): Boolean {
        val cb = onNext ?: return false
        cb()
        return true
    }

    fun dispatchRemotePrevious(): Boolean {
        val cb = onPrevious ?: return false
        cb()
        return true
    }

    fun dispatchRemoteSeekTo(seconds: Double): Boolean {
        val cb = onSeekTo ?: return false
        cb(seconds.coerceAtLeast(0.0))
        return true
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

    private fun syncMetadata() {
        val session = platformSession ?: return
        val meta = currentMetadata ?: return
        val durMs = (durationSec * 1000.0).toLong().coerceAtLeast(0L)
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val builder = MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, meta.title)
                if (meta.artist.isNotEmpty()) {
                    builder.putString(MediaMetadata.METADATA_KEY_ARTIST, meta.artist)
                }
                if (meta.album.isNotEmpty()) {
                    builder.putString(MediaMetadata.METADATA_KEY_ALBUM, meta.album)
                }
                if (durMs > 0L) {
                    builder.putLong(MediaMetadata.METADATA_KEY_DURATION, durMs)
                }
                session.setMetadata(builder.build())
            }
        }
    }

    private fun syncPlaybackState() {
        val session = platformSession ?: return
        val posMs = (positionSec * 1000.0).toLong().coerceAtLeast(0L)
        var actions = 0L
        if (onPlay != null) actions = actions or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE
        if (onPause != null) actions = actions or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE
        if (onNext != null) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        if (onPrevious != null) actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        if (onSeekTo != null) actions = actions or PlaybackState.ACTION_SEEK_TO
        val stateCode = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val rate = if (isPlaying) 1.0f else 0.0f
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val pbState = PlaybackState.Builder()
                    .setActions(actions)
                    .setState(stateCode, posMs, rate)
                    .build()
                session.setPlaybackState(pbState)
            }
        }
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
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
        onPlay = null
        onPause = null
        onNext = null
        onPrevious = null
        onSeekTo = null
        currentMetadata = null
    }
}
