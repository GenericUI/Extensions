@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.component.system.MediaSessionMetadata
import com.nexustools.genericui.threading.GenericUiThreading
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPNowPlayingPlaybackStatePaused
import platform.MediaPlayer.MPNowPlayingPlaybackStatePlaying
import platform.MediaPlayer.MPNowPlayingPlaybackStateStopped
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess

/**
 * Shared Apple (`macosMain` and `iosMain` covering `IOS` and `IPadOS`) bridge connecting
 * `MediaSessionManagerNativeImpl` to `MPNowPlayingInfoCenter` and `MPRemoteCommandCenter`.
 */
internal class AppleMediaSessionBridge {
    var currentMetadata: MediaSessionMetadata? = null
        private set
    var isPlaying: Boolean = false
        private set
    var positionSeconds: Double = 0.0
        private set
    var durationSeconds: Double = 0.0
        private set

    val nowPlayingInfoDictionary: MutableMap<String, Any?> = mutableMapOf()

    private var onPlay: (() -> Unit)? = null
    private var onPause: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null
    private var onPrevious: (() -> Unit)? = null
    private var onSeekTo: ((Double) -> Unit)? = null
    private var remoteCommandsInstalled: Boolean = false
    private val commandTargets: MutableList<Pair<Any, Any>> = mutableListOf()

    fun updateMetadata(metadata: MediaSessionMetadata) {
        currentMetadata = metadata
        syncNowPlaying()
    }

    fun updatePlaybackState(isPlaying: Boolean, positionSeconds: Double, durationSeconds: Double) {
        this.isPlaying = isPlaying
        this.positionSeconds = positionSeconds.coerceAtLeast(0.0)
        this.durationSeconds = durationSeconds.coerceAtLeast(0.0)
        syncNowPlaying()
    }

    fun setTransportCallbacks(
        onPlay: (() -> Unit)?,
        onPause: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onPrevious: (() -> Unit)?,
        onSeekTo: ((Double) -> Unit)?
    ) {
        this.onPlay = onPlay
        this.onPause = onPause
        this.onNext = onNext
        this.onPrevious = onPrevious
        this.onSeekTo = onSeekTo
        ensureRemoteCommandsInstalled()
        syncCommandEnabledFlags()
    }

    fun dispatchPlay(): Boolean {
        val cb = onPlay ?: return false
        cb()
        return true
    }

    fun dispatchPause(): Boolean {
        val cb = onPause ?: return false
        cb()
        return true
    }

    fun dispatchNext(): Boolean {
        val cb = onNext ?: return false
        cb()
        return true
    }

    fun dispatchPrevious(): Boolean {
        val cb = onPrevious ?: return false
        cb()
        return true
    }

    fun dispatchSeekTo(seconds: Double): Boolean {
        val cb = onSeekTo ?: return false
        cb(seconds.coerceAtLeast(0.0))
        return true
    }

    private fun ensureRemoteCommandsInstalled() {
        if (remoteCommandsInstalled) return
        remoteCommandsInstalled = true
        runCatching {
            val center = MPRemoteCommandCenter.sharedCommandCenter()
            commandTargets.add(
                center.playCommand to center.playCommand.addTargetWithHandler { _ ->
                    val cb = onPlay ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                    GenericUiThreading.runOnLogic { cb() }
                    MPRemoteCommandHandlerStatusSuccess
                }
            )
            commandTargets.add(
                center.pauseCommand to center.pauseCommand.addTargetWithHandler { _ ->
                    val cb = onPause ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                    GenericUiThreading.runOnLogic { cb() }
                    MPRemoteCommandHandlerStatusSuccess
                }
            )
            commandTargets.add(
                center.togglePlayPauseCommand to center.togglePlayPauseCommand.addTargetWithHandler { _ ->
                    val cb = if (isPlaying) onPause else onPlay
                    if (cb == null) return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                    GenericUiThreading.runOnLogic { cb() }
                    MPRemoteCommandHandlerStatusSuccess
                }
            )
            commandTargets.add(
                center.nextTrackCommand to center.nextTrackCommand.addTargetWithHandler { _ ->
                    val cb = onNext ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                    GenericUiThreading.runOnLogic { cb() }
                    MPRemoteCommandHandlerStatusSuccess
                }
            )
            commandTargets.add(
                center.previousTrackCommand to center.previousTrackCommand.addTargetWithHandler { _ ->
                    val cb = onPrevious ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                    GenericUiThreading.runOnLogic { cb() }
                    MPRemoteCommandHandlerStatusSuccess
                }
            )
            commandTargets.add(
                center.changePlaybackPositionCommand to center.changePlaybackPositionCommand.addTargetWithHandler { event ->
                    val cb = onSeekTo ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                    val posEvent = event as? MPChangePlaybackPositionCommandEvent
                        ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                    val targetSec = posEvent.positionTime.coerceAtLeast(0.0)
                    GenericUiThreading.runOnLogic { cb(targetSec) }
                    MPRemoteCommandHandlerStatusSuccess
                }
            )
        }
    }

    private fun syncCommandEnabledFlags() {
        runCatching {
            val center = MPRemoteCommandCenter.sharedCommandCenter()
            center.playCommand.enabled = onPlay != null
            center.pauseCommand.enabled = onPause != null
            center.togglePlayPauseCommand.enabled = onPlay != null || onPause != null
            center.nextTrackCommand.enabled = onNext != null
            center.previousTrackCommand.enabled = onPrevious != null
            center.changePlaybackPositionCommand.enabled = onSeekTo != null
        }
    }

    private fun syncNowPlaying() {
        val meta = currentMetadata
        nowPlayingInfoDictionary.clear()
        if (meta != null) {
            nowPlayingInfoDictionary["MPMediaItemPropertyTitle"] = meta.title
            nowPlayingInfoDictionary["MPMediaItemPropertyArtist"] = meta.artist
            nowPlayingInfoDictionary["MPMediaItemPropertyAlbumTitle"] = meta.album
        }
        if (durationSeconds > 0.0) {
            nowPlayingInfoDictionary["MPMediaItemPropertyPlaybackDuration"] = durationSeconds
        }
        nowPlayingInfoDictionary["MPNowPlayingInfoPropertyElapsedPlaybackTime"] = positionSeconds
        nowPlayingInfoDictionary["MPNowPlayingInfoPropertyPlaybackRate"] = if (isPlaying) 1.0 else 0.0

        runCatching {
            val center = MPNowPlayingInfoCenter.defaultCenter()
            if (meta != null) {
                val info = LinkedHashMap<Any?, Any?>(8)
                info[MPMediaItemPropertyTitle] = meta.title
                info[MPMediaItemPropertyArtist] = meta.artist
                info[MPMediaItemPropertyAlbumTitle] = meta.album
                if (durationSeconds > 0.0) {
                    info[MPMediaItemPropertyPlaybackDuration] = durationSeconds
                }
                info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = positionSeconds
                info[MPNowPlayingInfoPropertyPlaybackRate] = if (isPlaying) 1.0 else 0.0
                center.nowPlayingInfo = info
            } else {
                center.nowPlayingInfo = null
            }
            center.playbackState = if (isPlaying) {
                MPNowPlayingPlaybackStatePlaying
            } else if (meta != null || positionSeconds > 0.0) {
                MPNowPlayingPlaybackStatePaused
            } else {
                MPNowPlayingPlaybackStateStopped
            }
        }
    }

    fun dispose() {
        nowPlayingInfoDictionary.clear()
        runCatching {
            val center = MPNowPlayingInfoCenter.defaultCenter()
            center.nowPlayingInfo = null
            center.playbackState = MPNowPlayingPlaybackStateStopped
        }
        if (remoteCommandsInstalled) {
            runCatching {
                val center = MPRemoteCommandCenter.sharedCommandCenter()
                center.playCommand.removeTarget(null)
                center.pauseCommand.removeTarget(null)
                center.togglePlayPauseCommand.removeTarget(null)
                center.nextTrackCommand.removeTarget(null)
                center.previousTrackCommand.removeTarget(null)
                center.changePlaybackPositionCommand.removeTarget(null)
            }
            commandTargets.clear()
            remoteCommandsInstalled = false
        }
        onPlay = null
        onPause = null
        onNext = null
        onPrevious = null
        onSeekTo = null
        currentMetadata = null
        isPlaying = false
        positionSeconds = 0.0
        durationSeconds = 0.0
    }
}
