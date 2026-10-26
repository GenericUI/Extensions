package com.nexustools.genericui.ext.media.dsl

import com.nexustools.genericui.component.media.AudioAnalyzer
import com.nexustools.genericui.component.media.AudioPlayer
import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.media.VideoResizeMode
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.media.AudioAnalyzerExt
import com.nexustools.genericui.ext.media.AudioPlayerExt
import com.nexustools.genericui.ext.media.VideoPlayerExt
import com.nexustools.genericui.ext.media.overlay.VideoOverlayHost
import com.nexustools.genericui.ext.overlay.AbstractOverlay

/**
 * In-place [VideoPlayerExt] builder with `style { ... }` support.
 */
fun AbstractContainer.videoPlayerExt(
    sourceUrl: String? = null,
    volume: Float = 1.0f,
    muted: Boolean = false,
    playbackSpeed: Float = 1.0f,
    resizeMode: VideoResizeMode = VideoResizeMode.FIT,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker VideoPlayerExt.() -> Unit)? = null
): VideoPlayerExt {
    val player = VideoPlayerExt(
        sourceUrl = sourceUrl
    )
    player.volume = volume
    player.isMuted = muted
    player.playbackSpeed = playbackSpeed
    player.resizeMode = resizeMode
    if (!classes.isNullOrBlank()) {
        player.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        player.id = id
    }
    if (block != null) {
        player.block()
    }
    addChild(player)
    return player
}

/**
 * In-place [VideoPlayer] builder.
 */
fun AbstractContainer.videoPlayer(
    sourceUrl: String? = null,
    volume: Float = 1.0f,
    muted: Boolean = false,
    playbackSpeed: Float = 1.0f,
    resizeMode: VideoResizeMode = VideoResizeMode.FIT,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker VideoPlayer.() -> Unit)? = null
): VideoPlayer {
    val player = VideoPlayer(sourceUrl = sourceUrl)
    player.volume = volume
    player.isMuted = muted
    player.playbackSpeed = playbackSpeed
    player.resizeMode = resizeMode
    if (!classes.isNullOrBlank()) {
        player.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        player.id = id
    }
    if (block != null) {
        player.block()
    }
    addChild(player)
    return player
}

/**
 * In-place [AudioPlayerExt] builder.
 */
fun AbstractContainer.audioPlayerExt(
    sourceUrl: String? = null,
    volume: Float = 1.0f,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker AudioPlayerExt.() -> Unit)? = null
): AudioPlayerExt {
    val player = AudioPlayerExt(sourceUrl = sourceUrl)
    player.volume = volume
    if (!classes.isNullOrBlank()) {
        player.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        player.id = id
    }
    if (block != null) {
        player.block()
    }
    addChild(player)
    return player
}

/**
 * In-place [AudioPlayer] builder.
 */
fun AbstractContainer.audioPlayer(
    sourceUrl: String? = null,
    volume: Float = 1.0f,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker AudioPlayer.() -> Unit)? = null
): AudioPlayer {
    val player = AudioPlayer(sourceUrl = sourceUrl)
    player.volume = volume
    if (!classes.isNullOrBlank()) {
        player.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        player.id = id
    }
    if (block != null) {
        player.block()
    }
    addChild(player)
    return player
}

/**
 * Creates and configures an [AudioAnalyzerExt] within a container DSL scope.
 */
fun AbstractContainer.audioAnalyzerExt(
    fftSize: Int = 2048,
    block: (@GenericUiDslMarker AudioAnalyzerExt.() -> Unit)? = null
): AudioAnalyzerExt {
    val analyzer = AudioAnalyzerExt(fftSize = fftSize)
    if (block != null) {
        analyzer.block()
    }
    return analyzer
}

/**
 * Creates and configures a standalone [AudioAnalyzerExt].
 */
fun audioAnalyzerExt(
    fftSize: Int = 2048,
    block: (@GenericUiDslMarker AudioAnalyzerExt.() -> Unit)? = null
): AudioAnalyzerExt {
    val analyzer = AudioAnalyzerExt(fftSize = fftSize)
    if (block != null) {
        analyzer.block()
    }
    return analyzer
}

/**
 * Creates and configures a [AudioAnalyzer] within a container DSL scope.
 */
fun AbstractContainer.audioAnalyzer(
    fftSize: Int = 2048,
    block: (@GenericUiDslMarker AudioAnalyzer.() -> Unit)? = null
): AudioAnalyzer {
    val analyzer = AudioAnalyzer(fftSize = fftSize)
    if (block != null) {
        analyzer.block()
    }
    return analyzer
}

/**
 * In-place [VideoOverlayHost] builder wrapping a newly created [VideoPlayerExt] and optional [overlay].
 */
fun AbstractContainer.videoOverlayHost(
    sourceUrl: String? = null,
    overlay: AbstractOverlay? = null,
    volume: Float = 1.0f,
    muted: Boolean = false,
    playbackSpeed: Float = 1.0f,
    resizeMode: VideoResizeMode = VideoResizeMode.FIT,
    themeColorHex: String? = null,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker VideoOverlayHost.() -> Unit)? = null
): VideoOverlayHost {
    val player = VideoPlayerExt(sourceUrl = sourceUrl)
    player.volume = volume
    player.isMuted = muted
    player.playbackSpeed = playbackSpeed
    player.resizeMode = resizeMode
    val host = VideoOverlayHost(videoPlayer = player, initialOverlay = overlay)
    if (!themeColorHex.isNullOrBlank()) {
        host.themeColorHex = themeColorHex
    }
    if (!classes.isNullOrBlank()) {
        host.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        host.id = id
    }
    if (block != null) {
        host.block()
    }
    addChild(host)
    return host
}

/**
 * In-place [VideoOverlayHost] builder wrapping an existing [videoPlayer] and optional [overlay].
 */
fun AbstractContainer.videoOverlayHost(
    videoPlayer: VideoPlayerExt,
    overlay: AbstractOverlay? = null,
    themeColorHex: String? = null,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker VideoOverlayHost.() -> Unit)? = null
): VideoOverlayHost {
    val host = VideoOverlayHost(videoPlayer = videoPlayer, initialOverlay = overlay)
    if (!themeColorHex.isNullOrBlank()) {
        host.themeColorHex = themeColorHex
    }
    if (!classes.isNullOrBlank()) {
        host.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        host.id = id
    }
    if (block != null) {
        host.block()
    }
    addChild(host)
    return host
}

/**
 * Standalone [VideoOverlayHost] builder wrapping a newly created [VideoPlayerExt] and optional [overlay].
 */
fun videoOverlayHost(
    sourceUrl: String? = null,
    overlay: AbstractOverlay? = null,
    volume: Float = 1.0f,
    muted: Boolean = false,
    playbackSpeed: Float = 1.0f,
    resizeMode: VideoResizeMode = VideoResizeMode.FIT,
    themeColorHex: String? = null,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker VideoOverlayHost.() -> Unit)? = null
): VideoOverlayHost {
    val player = VideoPlayerExt(sourceUrl = sourceUrl)
    player.volume = volume
    player.isMuted = muted
    player.playbackSpeed = playbackSpeed
    player.resizeMode = resizeMode
    val host = VideoOverlayHost(videoPlayer = player, initialOverlay = overlay)
    if (!themeColorHex.isNullOrBlank()) {
        host.themeColorHex = themeColorHex
    }
    if (!classes.isNullOrBlank()) {
        host.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        host.id = id
    }
    if (block != null) {
        host.block()
    }
    return host
}

/**
 * Standalone [VideoOverlayHost] builder wrapping an existing [videoPlayer] and optional [overlay].
 */
fun videoOverlayHost(
    videoPlayer: VideoPlayerExt,
    overlay: AbstractOverlay? = null,
    themeColorHex: String? = null,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker VideoOverlayHost.() -> Unit)? = null
): VideoOverlayHost {
    val host = VideoOverlayHost(videoPlayer = videoPlayer, initialOverlay = overlay)
    if (!themeColorHex.isNullOrBlank()) {
        host.themeColorHex = themeColorHex
    }
    if (!classes.isNullOrBlank()) {
        host.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        host.id = id
    }
    if (block != null) {
        host.block()
    }
    return host
}
