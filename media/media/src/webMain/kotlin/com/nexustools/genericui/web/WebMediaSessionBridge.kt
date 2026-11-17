package com.nexustools.genericui.web

/**
 * Internal bridge to the W3C `navigator.mediaSession` API across JS and WasmJS targets.
 */
internal expect object WebMediaSessionBridge {
    fun syncMetadata(title: String, artist: String, album: String, artworkUrl: String)
    fun syncPlaybackState(playbackState: String, positionSeconds: Double, durationSeconds: Double)
    fun installHandlers(
        onPlay: (() -> Unit)?,
        onPause: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onPrevious: (() -> Unit)?,
        onSeekTo: ((Double) -> Unit)?
    )
    fun clearSession()
}
