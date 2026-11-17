@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.mediacontrols.apple

import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlButtonConfig
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus
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
import kotlin.math.roundToLong

/**
 * Shared Apple (`macosMain` & `iosMain` covering iOS and iPadOS) bridge for
 * `MPNowPlayingInfoCenter` and `MPRemoteCommandCenter`.
 */
internal class AppleNowPlayingBridge(
    private val isActiveProvider: () -> Boolean,
    private val onRemoteCommand: (action: MediaControlAction, seekPositionMs: Long?) -> Boolean
) {
    val nowPlayingInfoDictionary: MutableMap<String, Any?> = mutableMapOf()
    private var remoteCommandsInstalled: Boolean = false
    private val commandTargets: MutableList<Pair<Any, Any>> = mutableListOf()

    fun ensureRemoteCommandsInstalled(buttonConfig: MediaControlButtonConfig) {
        if (remoteCommandsInstalled) return
        remoteCommandsInstalled = true
        runCatching {
            val center = MPRemoteCommandCenter.sharedCommandCenter()
            commandTargets.add(
                center.playCommand to center.playCommand.addTargetWithHandler { _ ->
                    if (!isActiveProvider()) return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                    GenericUiThreading.runOnLogic { onRemoteCommand(MediaControlAction.PLAY, null) }
                    MPRemoteCommandHandlerStatusSuccess
                }
            )
            commandTargets.add(
                center.pauseCommand to center.pauseCommand.addTargetWithHandler { _ ->
                    if (!isActiveProvider()) return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                    GenericUiThreading.runOnLogic { onRemoteCommand(MediaControlAction.PAUSE, null) }
                    MPRemoteCommandHandlerStatusSuccess
                }
            )
            commandTargets.add(
                center.togglePlayPauseCommand to center.togglePlayPauseCommand.addTargetWithHandler { _ ->
                    if (!isActiveProvider()) return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                    GenericUiThreading.runOnLogic { onRemoteCommand(MediaControlAction.PLAY_PAUSE, null) }
                    MPRemoteCommandHandlerStatusSuccess
                }
            )
            commandTargets.add(
                center.stopCommand to center.stopCommand.addTargetWithHandler { _ ->
                    if (!isActiveProvider()) return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                    GenericUiThreading.runOnLogic { onRemoteCommand(MediaControlAction.STOP, null) }
                    MPRemoteCommandHandlerStatusSuccess
                }
            )
            commandTargets.add(
                center.nextTrackCommand to center.nextTrackCommand.addTargetWithHandler { _ ->
                    if (!isActiveProvider()) return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                    GenericUiThreading.runOnLogic { onRemoteCommand(MediaControlAction.NEXT, null) }
                    MPRemoteCommandHandlerStatusSuccess
                }
            )
            commandTargets.add(
                center.previousTrackCommand to center.previousTrackCommand.addTargetWithHandler { _ ->
                    if (!isActiveProvider()) return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                    GenericUiThreading.runOnLogic { onRemoteCommand(MediaControlAction.PREVIOUS, null) }
                    MPRemoteCommandHandlerStatusSuccess
                }
            )
            commandTargets.add(
                center.changePlaybackPositionCommand to center.changePlaybackPositionCommand.addTargetWithHandler { event ->
                    if (!isActiveProvider()) return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                    val posEvent = event as? MPChangePlaybackPositionCommandEvent
                        ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                    val targetMs = (posEvent.positionTime * 1000.0).roundToLong().coerceAtLeast(0L)
                    GenericUiThreading.runOnLogic {
                        onRemoteCommand(MediaControlAction.SEEK_TO, targetMs)
                    }
                    MPRemoteCommandHandlerStatusSuccess
                }
            )
            syncRemoteCommandButtons(buttonConfig)
        }
    }

    fun syncRemoteCommandButtons(config: MediaControlButtonConfig) {
        runCatching {
            val center = MPRemoteCommandCenter.sharedCommandCenter()
            center.playCommand.enabled = config.playPauseEnabled
            center.pauseCommand.enabled = config.playPauseEnabled
            center.togglePlayPauseCommand.enabled = config.playPauseEnabled
            center.stopCommand.enabled = config.stopEnabled
            center.nextTrackCommand.enabled = config.nextEnabled
            center.previousTrackCommand.enabled = config.previousEnabled
            center.changePlaybackPositionCommand.enabled = config.seekEnabled
        }
    }

    fun syncNowPlayingInfo(
        metadata: MediaControlMetadata,
        status: MediaPlaybackStatus,
        positionMs: Long,
        durationMs: Long,
        playbackRate: Float
    ) {
        if (!isActiveProvider()) {
            clearNowPlayingInfo()
            return
        }
        val durationSec = durationMs / 1000.0
        val elapsedSec = positionMs / 1000.0
        val rate = if (status == MediaPlaybackStatus.PLAYING) playbackRate.toDouble() else 0.0

        nowPlayingInfoDictionary["MPMediaItemPropertyTitle"] = metadata.title
        nowPlayingInfoDictionary["MPMediaItemPropertyArtist"] = metadata.artist
        nowPlayingInfoDictionary["MPMediaItemPropertyAlbumTitle"] = metadata.album
        nowPlayingInfoDictionary["MPMediaItemPropertyPlaybackDuration"] = durationSec
        nowPlayingInfoDictionary["MPNowPlayingInfoPropertyElapsedPlaybackTime"] = elapsedSec
        nowPlayingInfoDictionary["MPNowPlayingInfoPropertyPlaybackRate"] = rate
        nowPlayingInfoDictionary["MPNowPlayingPlaybackState"] = status.name

        runCatching {
            val center = MPNowPlayingInfoCenter.defaultCenter()
            val appleMap = LinkedHashMap<Any?, Any?>(8)
            appleMap[MPMediaItemPropertyTitle] = metadata.title
            appleMap[MPMediaItemPropertyArtist] = metadata.artist
            appleMap[MPMediaItemPropertyAlbumTitle] = metadata.album
            if (durationMs > 0L) {
                appleMap[MPMediaItemPropertyPlaybackDuration] = durationSec
            }
            appleMap[MPNowPlayingInfoPropertyElapsedPlaybackTime] = elapsedSec
            appleMap[MPNowPlayingInfoPropertyPlaybackRate] = rate
            center.nowPlayingInfo = appleMap
            center.playbackState = when (status) {
                MediaPlaybackStatus.PLAYING -> MPNowPlayingPlaybackStatePlaying
                MediaPlaybackStatus.PAUSED -> MPNowPlayingPlaybackStatePaused
                else -> MPNowPlayingPlaybackStateStopped
            }
        }
    }

    fun clearNowPlayingInfo() {
        nowPlayingInfoDictionary.clear()
        runCatching {
            val center = MPNowPlayingInfoCenter.defaultCenter()
            center.nowPlayingInfo = null
            center.playbackState = MPNowPlayingPlaybackStateStopped
        }
    }

    fun dispose() {
        clearNowPlayingInfo()
        if (remoteCommandsInstalled) {
            runCatching {
                val center = MPRemoteCommandCenter.sharedCommandCenter()
                center.playCommand.removeTarget(null)
                center.pauseCommand.removeTarget(null)
                center.togglePlayPauseCommand.removeTarget(null)
                center.stopCommand.removeTarget(null)
                center.nextTrackCommand.removeTarget(null)
                center.previousTrackCommand.removeTarget(null)
                center.changePlaybackPositionCommand.removeTarget(null)
            }
            commandTargets.clear()
            remoteCommandsInstalled = false
        }
    }
}
