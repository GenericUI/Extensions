package com.nexustools.genericui.ext.mediacontrols.web

private var activeKeyListener: dynamic = null

private fun hasNavigatorMediaSession(): Boolean =
    js("typeof navigator !== 'undefined' && navigator !== null && typeof navigator.mediaSession !== 'undefined' && navigator.mediaSession !== null") as Boolean

private fun hasWindowDocument(): Boolean =
    js("typeof window !== 'undefined' && window !== null") as Boolean

internal actual fun webInstallMediaSessionAndKeyListeners(impl: WebMediaControlsImpl) {
    if (hasNavigatorMediaSession()) {
        val ms: dynamic = js("navigator.mediaSession")
        val actions = arrayOf(
            "play",
            "pause",
            "stop",
            "previoustrack",
            "nexttrack",
            "seekto",
            "seekbackward",
            "seekforward"
        )
        for (act in actions) {
            runCatching {
                ms.setActionHandler(act) { details: dynamic ->
                    val seekSec = (details?.seekTime as? Number)?.toDouble()
                    impl.handleMediaSessionAction(act, seekSec)
                }
            }
        }
    }
    if (hasWindowDocument() && activeKeyListener == null) {
        val win: dynamic = js("window")
        val listener: dynamic = { ev: dynamic ->
            val key = (ev?.key as? String) ?: (ev?.code as? String) ?: ""
            if (impl.handleDomMediaKey(key)) {
                if (js("typeof ev.preventDefault === 'function'") as Boolean) {
                    ev.preventDefault()
                }
            }
        }
        activeKeyListener = listener
        win.addEventListener("keydown", listener)
    }
}

internal actual fun webUninstallMediaSessionAndKeyListeners(impl: WebMediaControlsImpl) {
    if (hasNavigatorMediaSession()) {
        val ms: dynamic = js("navigator.mediaSession")
        val actions = arrayOf(
            "play",
            "pause",
            "stop",
            "previoustrack",
            "nexttrack",
            "seekto",
            "seekbackward",
            "seekforward"
        )
        for (act in actions) {
            runCatching {
                ms.setActionHandler(act, null)
            }
        }
        runCatching {
            ms.playbackState = "none"
        }
    }
    if (hasWindowDocument() && activeKeyListener != null) {
        val win: dynamic = js("window")
        win.removeEventListener("keydown", activeKeyListener)
        activeKeyListener = null
    }
}

internal actual fun webSyncMediaSessionMetadata(
    title: String,
    artist: String,
    album: String,
    artworkUrl: String
) {
    if (!hasNavigatorMediaSession()) return
    runCatching {
        if (js("typeof MediaMetadata !== 'undefined'") as Boolean) {
            val initObj: dynamic = js("({})")
            initObj.title = title
            initObj.artist = artist
            initObj.album = album
            if (artworkUrl.isNotBlank()) {
                val artEntry: dynamic = js("({})")
                artEntry.src = artworkUrl
                initObj.artwork = arrayOf(artEntry)
            }
            val meta = js("new MediaMetadata(initObj)")
            val ms: dynamic = js("navigator.mediaSession")
            ms.metadata = meta
        }
    }
}

internal actual fun webSyncMediaSessionPlaybackState(
    playbackState: String,
    positionSeconds: Double,
    durationSeconds: Double,
    playbackRate: Double
) {
    if (!hasNavigatorMediaSession()) return
    runCatching {
        val ms: dynamic = js("navigator.mediaSession")
        ms.playbackState = playbackState
        if (durationSeconds > 0.0 && (js("typeof ms.setPositionState === 'function'") as Boolean)) {
            val posState: dynamic = js("({})")
            posState.duration = durationSeconds
            posState.playbackRate = playbackRate
            posState.position = positionSeconds.coerceIn(0.0, durationSeconds)
            ms.setPositionState(posState)
        }
    }
}
