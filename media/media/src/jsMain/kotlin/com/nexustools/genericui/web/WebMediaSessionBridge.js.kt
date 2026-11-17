package com.nexustools.genericui.web

internal actual object WebMediaSessionBridge {
    private fun hasMediaSession(): Boolean =
        WebBrowserDomBridge.isBrowserEnvironment &&
            (js("typeof navigator !== 'undefined' && navigator !== null && typeof navigator.mediaSession !== 'undefined' && navigator.mediaSession !== null") as Boolean)

    actual fun syncMetadata(title: String, artist: String, album: String, artworkUrl: String) {
        if (!hasMediaSession()) return
        runCatching {
            if (js("typeof MediaMetadata !== 'undefined'") as Boolean) {
                val initObj: dynamic = js("({})")
                initObj.title = title
                initObj.artist = artist
                initObj.album = album
                if (artworkUrl.isNotBlank()) {
                    val art: dynamic = js("({})")
                    art.src = artworkUrl
                    initObj.artwork = arrayOf(art)
                }
                val ms: dynamic = js("navigator.mediaSession")
                ms.metadata = js("new MediaMetadata(initObj)")
            }
        }
    }

    actual fun syncPlaybackState(playbackState: String, positionSeconds: Double, durationSeconds: Double) {
        if (!hasMediaSession()) return
        runCatching {
            val ms: dynamic = js("navigator.mediaSession")
            ms.playbackState = playbackState
            if (durationSeconds > 0.0 && (js("typeof navigator.mediaSession.setPositionState === 'function'") as Boolean)) {
                val posState: dynamic = js("({})")
                posState.duration = durationSeconds
                posState.position = positionSeconds.coerceIn(0.0, durationSeconds)
                posState.playbackRate = 1.0
                ms.setPositionState(posState)
            }
        }
    }

    actual fun installHandlers(
        onPlay: (() -> Unit)?,
        onPause: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onPrevious: (() -> Unit)?,
        onSeekTo: ((Double) -> Unit)?
    ) {
        if (!hasMediaSession()) return
        val ms: dynamic = js("navigator.mediaSession")
        runCatching { ms.setActionHandler("play", onPlay?.let { cb -> { _: dynamic -> cb() } }) }
        runCatching { ms.setActionHandler("pause", onPause?.let { cb -> { _: dynamic -> cb() } }) }
        runCatching { ms.setActionHandler("nexttrack", onNext?.let { cb -> { _: dynamic -> cb() } }) }
        runCatching { ms.setActionHandler("previoustrack", onPrevious?.let { cb -> { _: dynamic -> cb() } }) }
        runCatching {
            ms.setActionHandler(
                "seekto",
                onSeekTo?.let { cb ->
                    { details: dynamic ->
                        val sec = (details?.seekTime as? Number)?.toDouble()
                        if (sec != null) cb(sec.coerceAtLeast(0.0))
                    }
                }
            )
        }
    }

    actual fun clearSession() {
        if (!hasMediaSession()) return
        val ms: dynamic = js("navigator.mediaSession")
        for (act in arrayOf("play", "pause", "nexttrack", "previoustrack", "seekto")) {
            runCatching { ms.setActionHandler(act, null) }
        }
        runCatching { ms.playbackState = "none" }
    }
}
