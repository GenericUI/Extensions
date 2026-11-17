@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.nexustools.genericui.web

@JsFun("(title, artist, album, artworkUrl) => { " +
    "if (typeof navigator !== 'undefined' && navigator && navigator.mediaSession && typeof MediaMetadata !== 'undefined') { " +
    "  try { " +
    "    const artwork = artworkUrl ? [{ src: artworkUrl }] : []; " +
    "    navigator.mediaSession.metadata = new MediaMetadata({ title, artist, album, artwork }); " +
    "  } catch (e) {} " +
    "} " +
    "}")
private external fun wasmSyncSessionMetadata(title: String, artist: String, album: String, artworkUrl: String)

@JsFun("(state, posSec, durSec) => { " +
    "if (typeof navigator !== 'undefined' && navigator && navigator.mediaSession) { " +
    "  const ms = navigator.mediaSession; " +
    "  try { ms.playbackState = state; } catch (e) {} " +
    "  if (durSec > 0 && typeof ms.setPositionState === 'function') { " +
    "    try { ms.setPositionState({ duration: durSec, playbackRate: 1.0, position: Math.max(0, Math.min(posSec, durSec)) }); } catch (e) {} " +
    "  } " +
    "} " +
    "}")
private external fun wasmSyncSessionPlayback(state: String, posSec: Double, durSec: Double)

@JsFun("(hasPlay, hasPause, hasNext, hasPrevious, hasSeek, onPlay, onPause, onNext, onPrevious, onSeekTo) => { " +
    "if (typeof navigator === 'undefined' || !navigator || !navigator.mediaSession) return; " +
    "const ms = navigator.mediaSession; " +
    "try { ms.setActionHandler('play', hasPlay ? (() => { onPlay(); }) : null); } catch (e) {} " +
    "try { ms.setActionHandler('pause', hasPause ? (() => { onPause(); }) : null); } catch (e) {} " +
    "try { ms.setActionHandler('nexttrack', hasNext ? (() => { onNext(); }) : null); } catch (e) {} " +
    "try { ms.setActionHandler('previoustrack', hasPrevious ? (() => { onPrevious(); }) : null); } catch (e) {} " +
    "try { ms.setActionHandler('seekto', hasSeek ? ((d) => { if (d && typeof d.seekTime === 'number') onSeekTo(d.seekTime); }) : null); } catch (e) {} " +
    "}")
private external fun wasmInstallSessionHandlers(
    hasPlay: Boolean,
    hasPause: Boolean,
    hasNext: Boolean,
    hasPrevious: Boolean,
    hasSeek: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Double) -> Unit
)

@JsFun("() => { " +
    "if (typeof navigator === 'undefined' || !navigator || !navigator.mediaSession) return; " +
    "const ms = navigator.mediaSession; " +
    "const acts = ['play', 'pause', 'nexttrack', 'previoustrack', 'seekto']; " +
    "for (let i = 0; i < acts.length; i++) { try { ms.setActionHandler(acts[i], null); } catch (e) {} } " +
    "try { ms.playbackState = 'none'; } catch (e) {} " +
    "}")
private external fun wasmClearMediaSession()

internal actual object WebMediaSessionBridge {
    actual fun syncMetadata(title: String, artist: String, album: String, artworkUrl: String) {
        if (!WebBrowserDomBridge.isBrowserEnvironment) return
        wasmSyncSessionMetadata(title, artist, album, artworkUrl)
    }

    actual fun syncPlaybackState(playbackState: String, positionSeconds: Double, durationSeconds: Double) {
        if (!WebBrowserDomBridge.isBrowserEnvironment) return
        wasmSyncSessionPlayback(playbackState, positionSeconds, durationSeconds)
    }

    actual fun installHandlers(
        onPlay: (() -> Unit)?,
        onPause: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onPrevious: (() -> Unit)?,
        onSeekTo: ((Double) -> Unit)?
    ) {
        if (!WebBrowserDomBridge.isBrowserEnvironment) return
        wasmInstallSessionHandlers(
            hasPlay = onPlay != null,
            hasPause = onPause != null,
            hasNext = onNext != null,
            hasPrevious = onPrevious != null,
            hasSeek = onSeekTo != null,
            onPlay = { onPlay?.invoke() },
            onPause = { onPause?.invoke() },
            onNext = { onNext?.invoke() },
            onPrevious = { onPrevious?.invoke() },
            onSeekTo = { sec -> onSeekTo?.invoke(sec.coerceAtLeast(0.0)) }
        )
    }

    actual fun clearSession() {
        if (!WebBrowserDomBridge.isBrowserEnvironment) return
        wasmClearMediaSession()
    }
}
