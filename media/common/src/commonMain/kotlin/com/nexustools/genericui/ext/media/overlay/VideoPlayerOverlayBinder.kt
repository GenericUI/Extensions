package com.nexustools.genericui.ext.media.overlay

import com.nexustools.genericui.component.media.SubtitleCue
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.ext.overlay.OverlayBufferedRange
import com.nexustools.genericui.ext.overlay.OverlayHost
import com.nexustools.genericui.ext.overlay.OverlayTrackItem
import kotlin.math.roundToInt

/**
 * Synchronizes a [VideoPlayer] (or [com.nexustools.genericui.ext.media.VideoPlayerExt]) with an [OverlayHost],
 * pushing playback state, buffered ranges, tracks, and active WebVTT/SRT subtitle cues into
 * [OverlayHost.updateState], maintaining a 1000ms repeating UI timer while playing, and wiring
 * [com.nexustools.genericui.ext.overlay.OverlayActions] back to the [VideoPlayer].
 */
class VideoPlayerOverlayBinder(
    val videoPlayer: VideoPlayer,
    val host: OverlayHost
) : Disposable {

    private var pollTimerId: Int = -1
    private var bound: Boolean = false
    private var syncingState: Boolean = false

    override var isDisposed: Boolean = false
        private set

    val isPollingActive: Boolean
        get() = pollTimerId > 0

    private val playerStateObserver: () -> Unit = {
        if (!isDisposed && bound && !syncingState) {
            syncStateFromPlayer()
        }
    }

    init {
        bind()
    }

    /**
     * Attaches the binder to [videoPlayer] and [host], wiring [OverlayHost.actions] and performing
     * an initial state synchronization.
     */
    fun bind() {
        if (isDisposed || bound) return
        bound = true
        videoPlayer.addStateChangeObserver(playerStateObserver)
        wireActionsToHost()
        syncStateFromPlayer()
    }

    /**
     * Detaches the binder from [videoPlayer] and stops the 1000ms playback polling timer.
     */
    fun unbind() {
        if (!bound) return
        bound = false
        stopPollingTimer()
        videoPlayer.removeStateChangeObserver(playerStateObserver)
    }

    private fun wireActionsToHost() {
        host.updateActions { existing ->
            existing.copy(
                onPlayPause = {
                    videoPlayer.togglePlayPause()
                    syncStateFromPlayer()
                },
                onPlay = {
                    videoPlayer.play()
                    syncStateFromPlayer()
                },
                onPause = {
                    videoPlayer.pause()
                    syncStateFromPlayer()
                },
                onSeekToSec = { targetSec ->
                    videoPlayer.seekTo(targetSec)
                    syncStateFromPlayer()
                },
                onSkipDeltaSec = { deltaSec ->
                    videoPlayer.skipDelta(deltaSec)
                    syncStateFromPlayer()
                },
                onVolumeChange = { percent ->
                    val clamped = percent.coerceIn(0, 200)
                    videoPlayer.volume = clamped / 100f
                    if (clamped > 0 && videoPlayer.isMuted) {
                        videoPlayer.isMuted = false
                    }
                    syncStateFromPlayer()
                },
                onMuteToggle = {
                    videoPlayer.isMuted = !videoPlayer.isMuted
                    syncStateFromPlayer()
                },
                onSpeedChange = { speed ->
                    videoPlayer.playbackSpeed = speed.toFloat()
                    syncStateFromPlayer()
                },
                onSelectAudioTrack = { trackId ->
                    videoPlayer.selectAudioTrack(trackId)
                    syncStateFromPlayer()
                },
                onSelectSubtitleTrack = { trackId ->
                    videoPlayer.selectSubtitleTrack(trackId)
                    syncStateFromPlayer()
                },
                onToggleFullscreen = {
                    videoPlayer.toggleFullscreen()
                    syncStateFromPlayer()
                }
            )
        }
    }

    /**
     * Parses WebVTT (`.vtt`) or SubRip (`.srt`) content via [com.nexustools.genericui.component.media.WebVttSrtParser],
     * attaches the cues to [videoPlayer], and refreshes [host]'s [com.nexustools.genericui.ext.overlay.OverlayState.subtitleCueText].
     */
    fun loadSubtitleContent(vttOrSrtContent: String, trackId: String? = null): List<SubtitleCue> =
        videoPlayer.loadSubtitleContent(vttOrSrtContent, trackId)

    /**
     * Attaches pre-parsed [SubtitleCue]s to [videoPlayer] and refreshes [host]'s subtitle cue state.
     */
    fun attachSubtitleCues(cues: List<SubtitleCue>, trackId: String? = null) {
        videoPlayer.attachSubtitleCues(cues, trackId)
    }

    /**
     * Synchronizes all playback, buffering, track, and subtitle state from [videoPlayer] into [host].
     */
    fun syncStateFromPlayer() {
        if (isDisposed) return
        syncingState = true
        try {
            val posSec = videoPlayer.currentTimeSeconds
            val durSec = videoPlayer.durationSeconds
            val playing = videoPlayer.isPlaying
            val loading = videoPlayer.isBuffering
            val ranges = videoPlayer.bufferedRanges.map { (start, end) ->
                OverlayBufferedRange(startSec = start, endSec = end)
            }
            val volPercent = (videoPlayer.volume * 100f).roundToInt().coerceIn(0, 200)
            val muted = videoPlayer.isMuted
            val speed = (videoPlayer.playbackSpeed * 100f).roundToInt() / 100.0
            val fullscreen = videoPlayer.isFullscreen
            val pip = videoPlayer.isInPictureInPictureMode
            val audioItems = videoPlayer.getAudioTracks().map { track ->
                OverlayTrackItem(
                    id = track.id,
                    label = track.label,
                    language = track.language,
                    selected = track.isSelected
                )
            }
            val subtitleItems = videoPlayer.getSubtitleTracks().map { track ->
                OverlayTrackItem(
                    id = track.id,
                    label = track.label,
                    language = track.language,
                    selected = track.isSelected
                )
            }
            val cueText = videoPlayer.getActiveSubtitleCueText(posSec)

            if (host.isInPictureInPictureMode != pip) {
                host.setPictureInPictureMode(pip)
            }

            host.updateState { current ->
                current.copy(
                    isPlaying = playing,
                    isLoading = loading,
                    positionSec = posSec,
                    durationSec = durSec,
                    bufferedRanges = ranges,
                    volumePercent = volPercent,
                    isMuted = muted,
                    playbackSpeed = speed,
                    isFullscreen = fullscreen,
                    isInPictureInPictureMode = pip,
                    subtitleCueText = cueText,
                    audioTracks = audioItems,
                    subtitleTracks = subtitleItems
                )
            }

            updatePollingTimer(playing)
        } finally {
            syncingState = false
        }
    }

    private fun updatePollingTimer(playing: Boolean) {
        if (playing && !isDisposed && bound) {
            if (pollTimerId <= 0) {
                pollTimerId = videoPlayer.setInterval(POLL_INTERVAL_MS) {
                    if (isDisposed || !bound || !videoPlayer.isPlaying) {
                        stopPollingTimer()
                    } else {
                        syncStateFromPlayer()
                    }
                }
            }
        } else {
            stopPollingTimer()
        }
    }

    private fun stopPollingTimer() {
        val id = pollTimerId
        pollTimerId = -1
        if (id > 0) {
            videoPlayer.clearInterval(id)
        }
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        unbind()
    }

    companion object {
        const val POLL_INTERVAL_MS: Int = 1000
    }
}
