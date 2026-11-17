package com.nexustools.genericui.swing

import com.nexustools.genericui.component.system.MediaSessionMetadata
import com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

/**
 * Java/AWT Swing implementation of [MediaSessionManagerNativeImpl] maintaining active session
 * metadata/playback snapshots and dispatching multimedia AWT [KeyEvent]s (`0xB0..0xB3`,
 * `XF86Audio*`) via [KeyboardFocusManager].
 */
class SwingMediaSessionManagerImpl : MediaSessionManagerNativeImpl {

    private companion object {
        const val RAW_VK_MEDIA_NEXT_TRACK: Int = 0xB0
        const val RAW_VK_MEDIA_PREV_TRACK: Int = 0xB1
        const val RAW_VK_MEDIA_PLAY_PAUSE: Int = 0xB3
        const val XF86_AUDIO_PLAY: Int = 0x1008FF14
        const val XF86_AUDIO_PREV: Int = 0x1008FF16
        const val XF86_AUDIO_NEXT: Int = 0x1008FF17
        const val XF86_AUDIO_PAUSE: Int = 0x1008FF31
    }

    var currentMetadata: MediaSessionMetadata? = null
        private set
    var isPlaying: Boolean = false
        private set
    var positionSeconds: Double = 0.0
        private set
    var durationSeconds: Double = 0.0
        private set
    override var isDisposed: Boolean = false
        private set

    private var onPlay: (() -> Unit)? = null
    private var onPause: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null
    private var onPrevious: (() -> Unit)? = null
    private var onSeekTo: ((Double) -> Unit)? = null
    private var dispatcherInstalled: Boolean = false

    private val keyDispatcher = KeyEventDispatcher { event ->
        if (event.id == KeyEvent.KEY_PRESSED) {
            handleAwtMediaKeyCodes(event.keyCode, event.extendedKeyCode)
        } else {
            false
        }
    }

    override fun updateMetadata(metadata: MediaSessionMetadata) {
        if (isDisposed) return
        currentMetadata = metadata
    }

    override fun updatePlaybackState(isPlaying: Boolean, positionSeconds: Double, durationSeconds: Double) {
        if (isDisposed) return
        this.isPlaying = isPlaying
        this.positionSeconds = positionSeconds.coerceAtLeast(0.0)
        this.durationSeconds = durationSeconds.coerceAtLeast(0.0)
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
        syncDispatcherState()
    }

    fun handleAwtMediaKeyCodes(keyCode: Int, extendedKeyCode: Int = keyCode): Boolean {
        if (isDisposed) return false
        val code = if (keyCode != 0) keyCode else extendedKeyCode
        return when (code) {
            RAW_VK_MEDIA_PLAY_PAUSE, XF86_AUDIO_PLAY -> {
                val cb = if (isPlaying) onPause else onPlay
                cb?.invoke()
                cb != null
            }
            XF86_AUDIO_PAUSE -> {
                val cb = onPause ?: return false
                cb()
                true
            }
            RAW_VK_MEDIA_NEXT_TRACK, XF86_AUDIO_NEXT -> {
                val cb = onNext ?: return false
                cb()
                true
            }
            RAW_VK_MEDIA_PREV_TRACK, XF86_AUDIO_PREV -> {
                val cb = onPrevious ?: return false
                cb()
                true
            }
            else -> false
        }
    }

    fun dispatchSeekTo(seconds: Double): Boolean {
        if (isDisposed) return false
        val cb = onSeekTo ?: return false
        cb(seconds.coerceAtLeast(0.0))
        return true
    }

    private fun syncDispatcherState() {
        val want = !isDisposed && (onPlay != null || onPause != null || onNext != null || onPrevious != null)
        runCatching {
            val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            if (want && !dispatcherInstalled) {
                kfm.addKeyEventDispatcher(keyDispatcher)
                dispatcherInstalled = true
            } else if (!want && dispatcherInstalled) {
                kfm.removeKeyEventDispatcher(keyDispatcher)
                dispatcherInstalled = false
            }
        }
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        if (dispatcherInstalled) {
            runCatching {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher)
            }
            dispatcherInstalled = false
        }
        onPlay = null
        onPause = null
        onNext = null
        onPrevious = null
        onSeekTo = null
        currentMetadata = null
    }
}
