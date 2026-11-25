@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.mediacontrols.apple

import com.nexustools.genericui.ext.mediacontrols.MediaControlAction
import com.nexustools.genericui.ext.mediacontrols.MediaControlButtonConfig
import com.nexustools.genericui.ext.mediacontrols.MediaControlMetadata
import com.nexustools.genericui.ext.mediacontrols.MediaPlaybackStatus
import com.nexustools.genericui.threading.GenericUiThreading
import platform.Foundation.NSThread
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyDefaultPlaybackRate
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPNowPlayingPlaybackState
import platform.MediaPlayer.MPNowPlayingPlaybackStatePaused
import platform.MediaPlayer.MPNowPlayingPlaybackStatePlaying
import platform.MediaPlayer.MPNowPlayingPlaybackStateStopped
import platform.MediaPlayer.MPRemoteCommand
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
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
    private var disposed: Boolean = false
    private var latestButtonConfig: MediaControlButtonConfig = MediaControlButtonConfig()
    private val commandTargets: MutableList<Pair<MPRemoteCommand, Any>> = mutableListOf()

    private fun runOnAppleMain(block: () -> Unit) {
        if (NSThread.isMainThread) {
            block()
        } else {
            dispatch_async(dispatch_get_main_queue(), block)
        }
    }

    fun ensureRemoteCommandsInstalled(buttonConfig: MediaControlButtonConfig) {
        if (disposed) return
        latestButtonConfig = buttonConfig
        runOnAppleMain {
            if (disposed) return@runOnAppleMain
            val commandCenter = MPRemoteCommandCenter.sharedCommandCenter()
            if (!remoteCommandsInstalled) {
                val installed = ArrayList<Pair<MPRemoteCommand, Any>>(7)
                try {
                    fun registerActionCommand(command: MPRemoteCommand, action: MediaControlAction) {
                        val token = command.addTargetWithHandler { _ ->
                            if (!isActiveProvider()) {
                                return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                            }
                            GenericUiThreading.runOnLogic { onRemoteCommand(action, null) }
                            MPRemoteCommandHandlerStatusSuccess
                        }
                        installed.add(command to token)
                    }

                    registerActionCommand(commandCenter.playCommand, MediaControlAction.PLAY)
                    registerActionCommand(commandCenter.pauseCommand, MediaControlAction.PAUSE)
                    registerActionCommand(commandCenter.togglePlayPauseCommand, MediaControlAction.PLAY_PAUSE)
                    registerActionCommand(commandCenter.stopCommand, MediaControlAction.STOP)
                    registerActionCommand(commandCenter.nextTrackCommand, MediaControlAction.NEXT)
                    registerActionCommand(commandCenter.previousTrackCommand, MediaControlAction.PREVIOUS)

                    val seekToken = commandCenter.changePlaybackPositionCommand.addTargetWithHandler { event ->
                        if (!isActiveProvider()) {
                            return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                        }
                        val posEvent = event as? MPChangePlaybackPositionCommandEvent
                            ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
                        val targetMs = (posEvent.positionTime * 1000.0).roundToLong().coerceAtLeast(0L)
                        GenericUiThreading.runOnLogic {
                            onRemoteCommand(MediaControlAction.SEEK_TO, targetMs)
                        }
                        MPRemoteCommandHandlerStatusSuccess
                    }
                    installed.add(commandCenter.changePlaybackPositionCommand to seekToken)

                    commandTargets.addAll(installed)
                    remoteCommandsInstalled = true
                } catch (_: Throwable) {
                    for ((command, token) in installed) {
                        runCatching { command.removeTarget(token) }
                    }
                    return@runOnAppleMain
                }
            }
            if (isActiveProvider()) {
                activeBridges.add(this)
            }
            applyRemoteCommandButtonsOnMain(commandCenter, latestButtonConfig, active = isActiveProvider())
        }
    }

    fun syncRemoteCommandButtons(config: MediaControlButtonConfig) {
        if (disposed) return
        latestButtonConfig = config
        runOnAppleMain {
            if (disposed) return@runOnAppleMain
            val active = isActiveProvider()
            if (active) {
                activeBridges.add(this)
            } else {
                activeBridges.remove(this)
            }
            if (!active && activeBridges.isNotEmpty()) return@runOnAppleMain
            applyRemoteCommandButtonsOnMain(MPRemoteCommandCenter.sharedCommandCenter(), latestButtonConfig, active)
        }
    }

    fun syncNowPlayingInfo(
        metadata: MediaControlMetadata,
        status: MediaPlaybackStatus,
        positionMs: Long,
        durationMs: Long,
        playbackRate: Float
    ) {
        if (disposed || !isActiveProvider()) {
            clearNowPlayingInfo()
            return
        }
        val durationSec = durationMs / 1000.0
        val elapsedSec = (positionMs / 1000.0).coerceAtLeast(0.0)
        val rate = if (status == MediaPlaybackStatus.PLAYING) playbackRate.toDouble() else 0.0
        val resolvedTitle = metadata.title.trim().ifEmpty { DEFAULT_ACTIVE_TITLE }
        val mpPlaybackState = resolvePlaybackState(status)

        nowPlayingInfoDictionary["MPMediaItemPropertyTitle"] = resolvedTitle
        if (metadata.artist.isNotBlank()) {
            nowPlayingInfoDictionary["MPMediaItemPropertyArtist"] = metadata.artist
        } else {
            nowPlayingInfoDictionary.remove("MPMediaItemPropertyArtist")
        }
        if (metadata.album.isNotBlank()) {
            nowPlayingInfoDictionary["MPMediaItemPropertyAlbumTitle"] = metadata.album
        } else {
            nowPlayingInfoDictionary.remove("MPMediaItemPropertyAlbumTitle")
        }
        if (durationMs > 0L) {
            nowPlayingInfoDictionary["MPMediaItemPropertyPlaybackDuration"] = durationSec
        } else {
            nowPlayingInfoDictionary.remove("MPMediaItemPropertyPlaybackDuration")
        }
        nowPlayingInfoDictionary["MPNowPlayingInfoPropertyElapsedPlaybackTime"] = elapsedSec
        nowPlayingInfoDictionary["MPNowPlayingInfoPropertyPlaybackRate"] = rate
        nowPlayingInfoDictionary["MPNowPlayingInfoPropertyDefaultPlaybackRate"] = 1.0
        nowPlayingInfoDictionary["MPNowPlayingPlaybackState"] = when (mpPlaybackState) {
            MPNowPlayingPlaybackStatePlaying -> "PLAYING"
            MPNowPlayingPlaybackStatePaused -> "PAUSED"
            else -> "STOPPED"
        }
        nowPlayingInfoDictionary["MPNowPlayingPlaybackStatus"] = status.name

        runOnAppleMain {
            if (disposed || !isActiveProvider()) return@runOnAppleMain
            activeBridges.add(this)
            val infoCenter = MPNowPlayingInfoCenter.defaultCenter()
            val appleMap = LinkedHashMap<Any?, Any?>(8)
            appleMap[MPMediaItemPropertyTitle] = resolvedTitle
            if (metadata.artist.isNotBlank()) {
                appleMap[MPMediaItemPropertyArtist] = metadata.artist
            }
            if (metadata.album.isNotBlank()) {
                appleMap[MPMediaItemPropertyAlbumTitle] = metadata.album
            }
            if (durationMs > 0L) {
                appleMap[MPMediaItemPropertyPlaybackDuration] = durationSec
            }
            appleMap[MPNowPlayingInfoPropertyElapsedPlaybackTime] = elapsedSec
            appleMap[MPNowPlayingInfoPropertyPlaybackRate] = rate
            appleMap[MPNowPlayingInfoPropertyDefaultPlaybackRate] = 1.0
            infoCenter.nowPlayingInfo = appleMap
            infoCenter.playbackState = mpPlaybackState
        }
    }

    fun clearNowPlayingInfo(disableCommands: Boolean = false) {
        nowPlayingInfoDictionary.clear()
        runOnAppleMain {
            if (disposed) return@runOnAppleMain
            activeBridges.remove(this)
            if (activeBridges.isNotEmpty()) return@runOnAppleMain
            val infoCenter = MPNowPlayingInfoCenter.defaultCenter()
            infoCenter.nowPlayingInfo = null
            infoCenter.playbackState = MPNowPlayingPlaybackStateStopped
            if (disableCommands || !isActiveProvider()) {
                applyRemoteCommandButtonsOnMain(
                    MPRemoteCommandCenter.sharedCommandCenter(),
                    latestButtonConfig,
                    active = false
                )
            }
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        nowPlayingInfoDictionary.clear()
        runOnAppleMain {
            activeBridges.remove(this)
            val targetsSnapshot = commandTargets.toList()
            commandTargets.clear()
            remoteCommandsInstalled = false
            val noOtherActive = activeBridges.isEmpty()
            for ((command, token) in targetsSnapshot) {
                if (noOtherActive) {
                    command.enabled = false
                }
                command.removeTarget(token)
            }
            if (noOtherActive) {
                val infoCenter = MPNowPlayingInfoCenter.defaultCenter()
                infoCenter.nowPlayingInfo = null
                infoCenter.playbackState = MPNowPlayingPlaybackStateStopped
            }
        }
    }

    private fun resolvePlaybackState(status: MediaPlaybackStatus): MPNowPlayingPlaybackState = when (status) {
        // Keep macOS/iOS Now Playing active during buffering so media keys stay owned by this session.
        MediaPlaybackStatus.PLAYING,
        MediaPlaybackStatus.LOADING -> MPNowPlayingPlaybackStatePlaying
        MediaPlaybackStatus.PAUSED -> MPNowPlayingPlaybackStatePaused
        MediaPlaybackStatus.STOPPED -> MPNowPlayingPlaybackStateStopped
    }

    private fun applyRemoteCommandButtonsOnMain(
        commandCenter: MPRemoteCommandCenter,
        config: MediaControlButtonConfig,
        active: Boolean
    ) {
        commandCenter.playCommand.enabled = active && config.playPauseEnabled
        commandCenter.pauseCommand.enabled = active && config.playPauseEnabled
        commandCenter.togglePlayPauseCommand.enabled = active && config.playPauseEnabled
        commandCenter.stopCommand.enabled = active && config.stopEnabled
        commandCenter.nextTrackCommand.enabled = active && config.nextEnabled
        commandCenter.previousTrackCommand.enabled = active && config.previousEnabled
        commandCenter.changePlaybackPositionCommand.enabled = active && config.seekEnabled
    }

    companion object {
        private const val DEFAULT_ACTIVE_TITLE: String = "Media Playing"
        private val activeBridges: MutableSet<AppleNowPlayingBridge> = mutableSetOf()
    }
}
