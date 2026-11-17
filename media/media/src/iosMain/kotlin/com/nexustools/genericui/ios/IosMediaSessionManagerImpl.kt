@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.apple.AppleMediaSessionBridge
import com.nexustools.genericui.component.system.MediaSessionMetadata
import com.nexustools.genericui.nativeimpl.system.MediaSessionManagerNativeImpl
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive

/**
 * Apple iOS & iPadOS (`BackendTarget.IOS` and `BackendTarget.IPadOS`) implementation of
 * [MediaSessionManagerNativeImpl] backed by `MPNowPlayingInfoCenter`, `MPRemoteCommandCenter`,
 * and `AVAudioSession`.
 */
class IosMediaSessionManagerImpl : MediaSessionManagerNativeImpl {
    private val bridge = AppleMediaSessionBridge()

    override var isDisposed: Boolean = false
        private set

    val nowPlayingInfoDictionary: Map<String, Any?>
        get() = bridge.nowPlayingInfoDictionary

    val currentMetadata: MediaSessionMetadata?
        get() = bridge.currentMetadata

    override fun updateMetadata(metadata: MediaSessionMetadata) {
        if (isDisposed) return
        ensurePlaybackAudioSessionActive(true)
        bridge.updateMetadata(metadata)
    }

    override fun updatePlaybackState(isPlaying: Boolean, positionSeconds: Double, durationSeconds: Double) {
        if (isDisposed) return
        if (isPlaying) {
            ensurePlaybackAudioSessionActive(true)
        }
        bridge.updatePlaybackState(isPlaying, positionSeconds, durationSeconds)
    }

    override fun setTransportCallbacks(
        onPlay: (() -> Unit)?,
        onPause: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onPrevious: (() -> Unit)?,
        onSeekTo: ((Double) -> Unit)?
    ) {
        if (isDisposed) return
        bridge.setTransportCallbacks(onPlay, onPause, onNext, onPrevious, onSeekTo)
    }

    fun dispatchRemotePlay(): Boolean = bridge.dispatchPlay()
    fun dispatchRemotePause(): Boolean = bridge.dispatchPause()
    fun dispatchRemoteNext(): Boolean = bridge.dispatchNext()
    fun dispatchRemotePrevious(): Boolean = bridge.dispatchPrevious()
    fun dispatchRemoteSeekTo(seconds: Double): Boolean = bridge.dispatchSeekTo(seconds)

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        bridge.dispose()
        ensurePlaybackAudioSessionActive(false)
    }

    private fun ensurePlaybackAudioSessionActive(active: Boolean) {
        runCatching {
            val session = AVAudioSession.sharedInstance()
            if (active) {
                session.setCategory(AVAudioSessionCategoryPlayback, error = null)
                session.setActive(true, error = null)
            } else {
                session.setActive(false, error = null)
            }
        }
    }
}
