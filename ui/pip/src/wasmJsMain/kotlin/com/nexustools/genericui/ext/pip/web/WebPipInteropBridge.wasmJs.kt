@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.nexustools.genericui.ext.pip.web

@JsFun("() => typeof window !== 'undefined' && window !== null && typeof document !== 'undefined' && document !== null")
private external fun wasmIsBrowserEnv(): Boolean

@JsFun("() => typeof window !== 'undefined' && window !== null && typeof window.documentPictureInPicture !== 'undefined' && window.documentPictureInPicture !== null && typeof window.documentPictureInPicture.requestWindow === 'function'")
private external fun wasmIsDocumentPipSupported(): Boolean

@JsFun("() => typeof document !== 'undefined' && document !== null && document.pictureInPictureEnabled === true")
private external fun wasmIsVideoPipSupported(): Boolean

@JsFun("() => typeof document !== 'undefined' && document !== null && (document.hidden === true || document.visibilityState === 'hidden')")
private external fun wasmIsDocumentHidden(): Boolean

@JsFun("(onChanged) => { " +
    "if (typeof document === 'undefined' || !document) return -1; " +
    "window.__genericUiPipVisListeners = window.__genericUiPipVisListeners || {}; " +
    "window.__genericUiPipVisNextId = (window.__genericUiPipVisNextId || 0) + 1; " +
    "const id = window.__genericUiPipVisNextId; " +
    "const fn = () => { onChanged(document.hidden === true || document.visibilityState === 'hidden'); }; " +
    "window.__genericUiPipVisListeners[id] = fn; " +
    "document.addEventListener('visibilitychange', fn); " +
    "return id; }")
private external fun wasmAddVisibilityListener(onChanged: (Boolean) -> Unit): Int

@JsFun("(id) => { " +
    "if (typeof document === 'undefined' || !document || typeof window === 'undefined' || !window.__genericUiPipVisListeners) return; " +
    "const fn = window.__genericUiPipVisListeners[id]; " +
    "if (fn) { document.removeEventListener('visibilitychange', fn); delete window.__genericUiPipVisListeners[id]; } }")
private external fun wasmRemoveVisibilityListener(id: Int)

@JsFun("(widthPx, heightPx, title, rootPeer, onEntered, onClosed, onFailed) => { " +
    "if (typeof window === 'undefined' || !window || !window.documentPictureInPicture || typeof window.documentPictureInPicture.requestWindow !== 'function' || !rootPeer) return false; " +
    "try { " +
    "  if (window.__genericUiPipWin) { try { window.__genericUiPipWin.close(); } catch (_e) {} window.__genericUiPipWin = null; } " +
    "  window.__genericUiPipClosedCb = onClosed; " +
    "  window.documentPictureInPicture.requestWindow({ width: Math.max(160, widthPx), height: Math.max(90, heightPx) }).then((pipWin) => { " +
    "    window.__genericUiPipWin = pipWin; " +
    "    try { " +
    "      const pipDoc = pipWin.document; " +
    "      if (title) pipDoc.title = title; " +
    "      pipDoc.body.style.margin = '0'; " +
    "      pipDoc.body.style.padding = '0'; " +
    "      pipDoc.body.style.overflow = 'hidden'; " +
    "      pipDoc.body.style.background = '#000'; " +
    "      const sheets = Array.from(document.styleSheets || []); " +
    "      for (let i = 0; i < sheets.length; i++) { " +
    "        const href = sheets[i] && sheets[i].href; " +
    "        if (href) { " +
    "          const link = pipDoc.createElement('link'); " +
    "          link.rel = 'stylesheet'; " +
    "          link.href = href; " +
    "          pipDoc.head.appendChild(link); " +
    "        } " +
    "      } " +
    "      pipDoc.body.appendChild(rootPeer); " +
    "      pipWin.addEventListener('pagehide', () => { " +
    "        const cb = window.__genericUiPipClosedCb; " +
    "        window.__genericUiPipWin = null; " +
    "        window.__genericUiPipClosedCb = null; " +
    "        if (cb) cb(); " +
    "      }); " +
    "    } catch (_e) {} " +
    "    onEntered(); " +
    "  }, (_err) => { " +
    "    window.__genericUiPipWin = null; " +
    "    window.__genericUiPipClosedCb = null; " +
    "    onFailed(); " +
    "  }); " +
    "  return true; " +
    "} catch (_e) { " +
    "  window.__genericUiPipWin = null; " +
    "  window.__genericUiPipClosedCb = null; " +
    "  return false; } }")
private external fun wasmOpenDocumentPipWindow(
    widthPx: Int,
    heightPx: Int,
    title: String,
    rootPeer: JsAny?,
    onEntered: () -> Unit,
    onClosed: () -> Unit,
    onFailed: () -> Unit
): Boolean

@JsFun("() => { " +
    "if (typeof window === 'undefined' || !window) return; " +
    "const w = window.__genericUiPipWin; " +
    "window.__genericUiPipWin = null; " +
    "window.__genericUiPipClosedCb = null; " +
    "if (w) { try { w.close(); } catch (_e) {} } }")
private external fun wasmCloseDocumentPipWindow()

@JsFun("(rootPeer) => { " +
    "if (typeof document === 'undefined' || !document || !document.body || !rootPeer) return false; " +
    "try { document.body.appendChild(rootPeer); return true; } catch (_e) { return false; } }")
private external fun wasmAttachInPlaceRoot(rootPeer: JsAny?): Boolean

@JsFun("(rootPeer) => { " +
    "if (!rootPeer || !rootPeer.parentNode) return; " +
    "try { rootPeer.parentNode.removeChild(rootPeer); } catch (_e) {} }")
private external fun wasmDetachInPlaceRoot(rootPeer: JsAny?)

@JsFun("(peer) => !!(peer && typeof peer.tagName === 'string' && peer.tagName.toLowerCase() === 'video')")
private external fun wasmIsHtmlVideoElement(peer: JsAny?): Boolean

@JsFun("(videoPeer, onEntered, onExited, onFailed) => { " +
    "if (typeof document === 'undefined' || !document || document.pictureInPictureEnabled !== true || !videoPeer || typeof videoPeer.requestPictureInPicture !== 'function') return false; " +
    "try { " +
    "  const onLeave = () => { " +
    "    if (window.__genericUiPipVideo === videoPeer) { " +
    "      window.__genericUiPipVideo = null; " +
    "      window.__genericUiPipVideoLeave = null; " +
    "      onExited(); " +
    "    } " +
    "  }; " +
    "  window.__genericUiPipVideo = videoPeer; " +
    "  window.__genericUiPipVideoLeave = onLeave; " +
    "  videoPeer.addEventListener('leavepictureinpicture', onLeave); " +
    "  videoPeer.requestPictureInPicture().then(() => onEntered(), (_err) => { " +
    "    if (window.__genericUiPipVideo === videoPeer) { " +
    "      try { videoPeer.removeEventListener('leavepictureinpicture', onLeave); } catch (_e) {} " +
    "      window.__genericUiPipVideo = null; " +
    "      window.__genericUiPipVideoLeave = null; " +
    "    } " +
    "    onFailed(); " +
    "  }); " +
    "  return true; " +
    "} catch (_e) { return false; } }")
private external fun wasmRequestVideoPip(
    videoPeer: JsAny?,
    onEntered: () -> Unit,
    onExited: () -> Unit,
    onFailed: () -> Unit
): Boolean

@JsFun("() => { " +
    "if (typeof window !== 'undefined' && window && window.__genericUiPipVideo && window.__genericUiPipVideoLeave) { " +
    "  try { window.__genericUiPipVideo.removeEventListener('leavepictureinpicture', window.__genericUiPipVideoLeave); } catch (_e) {} " +
    "  window.__genericUiPipVideo = null; " +
    "  window.__genericUiPipVideoLeave = null; " +
    "} " +
    "if (typeof document !== 'undefined' && document && document.pictureInPictureElement && typeof document.exitPictureInPicture === 'function') { " +
    "  try { document.exitPictureInPicture(); return true; } catch (_e) {} " +
    "} return false; }")
private external fun wasmExitVideoPip(): Boolean

internal actual object WebPipInteropBridge {

    actual fun isBrowserEnvironment(): Boolean = wasmIsBrowserEnv()

    actual fun isDocumentPipSupported(): Boolean = wasmIsDocumentPipSupported()

    actual fun isVideoElementPipSupported(): Boolean = wasmIsVideoPipSupported()

    actual fun isDocumentHidden(): Boolean = wasmIsDocumentHidden()

    actual fun observeDocumentVisibility(onHiddenChanged: (Boolean) -> Unit): () -> Unit {
        val id = wasmAddVisibilityListener(onHiddenChanged)
        if (id <= 0) return {}
        return { wasmRemoveVisibilityListener(id) }
    }

    actual fun openDocumentPipWindow(
        widthPx: Int,
        heightPx: Int,
        title: String,
        rootPeer: Any?,
        onEntered: () -> Unit,
        onClosed: () -> Unit,
        onFailed: () -> Unit
    ): Boolean = wasmOpenDocumentPipWindow(
        widthPx = widthPx.coerceAtLeast(160),
        heightPx = heightPx.coerceAtLeast(90),
        title = title,
        rootPeer = rootPeer as? JsAny,
        onEntered = onEntered,
        onClosed = onClosed,
        onFailed = onFailed
    )

    actual fun closeDocumentPipWindow() {
        wasmCloseDocumentPipWindow()
    }

    actual fun attachInPlaceRoot(rootPeer: Any?): Boolean =
        wasmAttachInPlaceRoot(rootPeer as? JsAny)

    actual fun detachInPlaceRoot(rootPeer: Any?) {
        wasmDetachInPlaceRoot(rootPeer as? JsAny)
    }

    actual fun isHtmlVideoElement(peer: Any?): Boolean =
        wasmIsHtmlVideoElement(peer as? JsAny)

    actual fun requestVideoElementPip(
        videoPeer: Any?,
        onEntered: () -> Unit,
        onExited: () -> Unit,
        onFailed: () -> Unit
    ): Boolean = wasmRequestVideoPip(
        videoPeer = videoPeer as? JsAny,
        onEntered = onEntered,
        onExited = onExited,
        onFailed = onFailed
    )

    actual fun exitVideoElementPip(): Boolean = wasmExitVideoPip()
}
