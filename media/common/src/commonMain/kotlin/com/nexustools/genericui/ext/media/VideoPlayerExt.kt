package com.nexustools.genericui.ext.media

import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.media.overlay.VideoOverlayHost
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.nativeimpl.media.VideoPlayerNativeImpl

/**
 * Video player component in `:extensions:media` extending [VideoPlayer] and resolving its
 * native implementation through [MediaExtension] (`libvlc` on native desktop backends,
 * `libvlcj` on `:swing`, and native platform media components on `:mobile` and `:web`).
 */
open class VideoPlayerExt(
    sourceUrl: String? = null
) : VideoPlayer(sourceUrl) {

    internal var attachedOverlayHost: VideoOverlayHost? = null

    /**
     * Wraps this [VideoPlayerExt] in a [VideoOverlayHost] (or reuses the existing host) and
     * attaches [overlay] to it.
     */
    fun attachVideoOverlay(overlay: AbstractOverlay): VideoOverlayHost {
        val existing = attachedOverlayHost
        if (existing != null) {
            existing.attachOverlay(overlay)
            return existing
        }
        return VideoOverlayHost(videoPlayer = this, initialOverlay = overlay)
    }

    /**
     * Wraps this [VideoPlayerExt] in a [VideoOverlayHost] (or reuses the existing host),
     * optionally attaching [overlay] if provided.
     */
    fun withOverlay(overlay: AbstractOverlay? = null): VideoOverlayHost {
        val existing = attachedOverlayHost
        if (existing != null) {
            if (overlay != null) {
                existing.attachOverlay(overlay)
            }
            return existing
        }
        return VideoOverlayHost(videoPlayer = this, initialOverlay = overlay)
    }

    override fun createNativeImpl(factory: NativeFactory): VideoPlayerNativeImpl<*> =
        MediaExtension.createVideoPlayerNative() ?: super.createNativeImpl(factory)
}
