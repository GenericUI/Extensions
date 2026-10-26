@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.nexustools.genericui.ext.mediacontrols.web

@JsFun("(onAction, onKey) => { " +
    "if (typeof navigator !== 'undefined' && navigator && navigator.mediaSession) { " +
    "  const ms = navigator.mediaSession; " +
    "  const acts = ['play','pause','stop','previoustrack','nexttrack','seekto','seekbackward','seekforward']; " +
    "  for (let i = 0; i < acts.length; i++) { " +
    "    const a = acts[i]; " +
    "    try { ms.setActionHandler(a, (d) => { onAction(a, (d && typeof d.seekTime === 'number') ? d.seekTime : -1.0); }); } catch (e) {} " +
    "  } " +
    "} " +
    "if (typeof window !== 'undefined' && window) { " +
    "  if (window.__genericUiMediaKeyHandler) window.removeEventListener('keydown', window.__genericUiMediaKeyHandler); " +
    "  window.__genericUiMediaKeyHandler = (ev) => { " +
    "    const k = (ev && typeof ev.key === 'string' && ev.key) ? ev.key : ((ev && typeof ev.code === 'string') ? ev.code : ''); " +
    "    if (k && onKey(k) && typeof ev.preventDefault === 'function') ev.preventDefault(); " +
    "  }; " +
    "  window.addEventListener('keydown', window.__genericUiMediaKeyHandler); " +
    "} " +
    "}")
private external fun wasmInstallMediaSession(
    onAction: (String, Double) -> Unit,
    onKey: (String) -> Boolean
)

@JsFun("() => { " +
    "if (typeof navigator !== 'undefined' && navigator && navigator.mediaSession) { " +
    "  const ms = navigator.mediaSession; " +
    "  const acts = ['play','pause','stop','previoustrack','nexttrack','seekto','seekbackward','seekforward']; " +
    "  for (let i = 0; i < acts.length; i++) { try { ms.setActionHandler(acts[i], null); } catch (e) {} } " +
    "  try { ms.playbackState = 'none'; } catch (e) {} " +
    "} " +
    "if (typeof window !== 'undefined' && window && window.__genericUiMediaKeyHandler) { " +
    "  window.removeEventListener('keydown', window.__genericUiMediaKeyHandler); " +
    "  window.__genericUiMediaKeyHandler = null; " +
    "} " +
    "}")
private external fun wasmUninstallMediaSession()

@JsFun("(title, artist, album, artworkUrl) => { " +
    "if (typeof navigator !== 'undefined' && navigator && navigator.mediaSession && typeof MediaMetadata !== 'undefined') { " +
    "  try { " +
    "    const artwork = artworkUrl ? [{ src: artworkUrl }] : []; " +
    "    navigator.mediaSession.metadata = new MediaMetadata({ title, artist, album, artwork }); " +
    "  } catch (e) {} " +
    "} " +
    "}")
private external fun wasmSyncMetadata(title: String, artist: String, album: String, artworkUrl: String)

@JsFun("(state, posSec, durSec, rate) => { " +
    "if (typeof navigator !== 'undefined' && navigator && navigator.mediaSession) { " +
    "  const ms = navigator.mediaSession; " +
    "  try { ms.playbackState = state; } catch (e) {} " +
    "  if (durSec > 0 && typeof ms.setPositionState === 'function') { " +
    "    try { ms.setPositionState({ duration: durSec, playbackRate: rate, position: Math.max(0, Math.min(posSec, durSec)) }); } catch (e) {} " +
    "  } " +
    "} " +
    "}")
private external fun wasmSyncPlaybackState(state: String, posSec: Double, durSec: Double, rate: Double)

internal actual fun webInstallMediaSessionAndKeyListeners(impl: WebMediaControlsImpl) {
    wasmInstallMediaSession(
        onAction = { actionName, seekSec ->
            impl.handleMediaSessionAction(actionName, if (seekSec >= 0.0) seekSec else null)
        },
        onKey = { key ->
            impl.handleDomMediaKey(key)
        }
    )
}

internal actual fun webUninstallMediaSessionAndKeyListeners(impl: WebMediaControlsImpl) {
    wasmUninstallMediaSession()
}

internal actual fun webSyncMediaSessionMetadata(
    title: String,
    artist: String,
    album: String,
    artworkUrl: String
) {
    wasmSyncMetadata(title, artist, album, artworkUrl)
}

internal actual fun webSyncMediaSessionPlaybackState(
    playbackState: String,
    positionSeconds: Double,
    durationSeconds: Double,
    playbackRate: Double
) {
    wasmSyncPlaybackState(playbackState, positionSeconds, durationSeconds, playbackRate)
}
