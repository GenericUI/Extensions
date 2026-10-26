package com.nexustools.genericui.ext.media.overlay

import com.nexustools.genericui.component.media.SubtitleCue
import com.nexustools.genericui.ext.media.VideoPlayerExt
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.ext.overlay.OverlayHostBox

/**
 * Specialized [OverlayHostBox] wrapping a [VideoPlayerExt], binding its playback state and
 * actions through [VideoPlayerOverlayBinder], targeting the host container for fullscreen mode,
 * and suppressing the overlay when Picture-in-Picture mode is active.
 */
open class VideoOverlayHost(
    val videoPlayer: VideoPlayerExt,
    initialOverlay: AbstractOverlay? = null
) : OverlayHostBox(content = videoPlayer, initialOverlay = initialOverlay) {

    override var id: String?
        get() = super.id
        set(value) {
            super.id = value
            videoPlayer.setFullscreenTargetElementId(value)
        }

    val binder: VideoPlayerOverlayBinder = VideoPlayerOverlayBinder(
        videoPlayer = videoPlayer,
        host = this
    )

    init {
        htmlTag = "genericui:video-overlay-host"
        addClass("genericui-video-overlay-host")
        videoPlayer.attachedOverlayHost = this
        if (id.isNullOrBlank()) {
            id = "genericui-video-overlay-host-${++instanceCounter}"
        } else {
            videoPlayer.setFullscreenTargetElementId(id)
        }
    }

    /**
     * Parses WebVTT (`.vtt`) or SubRip (`.srt`) content and attaches the resulting [SubtitleCue]s
     * to [videoPlayer], updating [state]'s active subtitle cue text.
     */
    fun loadSubtitleContent(vttOrSrtContent: String, trackId: String? = null): List<SubtitleCue> =
        binder.loadSubtitleContent(vttOrSrtContent, trackId)

    /**
     * Attaches pre-parsed [SubtitleCue]s to [videoPlayer].
     */
    fun attachSubtitleCues(cues: List<SubtitleCue>, trackId: String? = null) {
        binder.attachSubtitleCues(cues, trackId)
    }

    override fun setPictureInPictureMode(enabled: Boolean) {
        super.setPictureInPictureMode(enabled)
        if (enabled) {
            if (!videoPlayer.isInPictureInPictureMode) {
                videoPlayer.enterPictureInPicture()
            }
        } else {
            if (videoPlayer.isInPictureInPictureMode) {
                videoPlayer.exitPictureInPicture()
            }
        }
    }

    override fun dispose() {
        if (videoPlayer.attachedOverlayHost === this) {
            videoPlayer.attachedOverlayHost = null
        }
        binder.dispose()
        super.dispose()
    }

    companion object {
        private var instanceCounter: Int = 0
    }
}
