package com.nexustools.genericui.ext.pip.web

private var activeDocumentPipWin: dynamic = null
private var activeDocumentPipCloseCallback: (() -> Unit)? = null
private var activeVideoPipElement: dynamic = null
private var activeVideoPipLeaveHandler: dynamic = null

private fun hasDomWindow(): Boolean =
    js("typeof window !== 'undefined' && window !== null && typeof document !== 'undefined' && document !== null") as Boolean

internal actual object WebPipInteropBridge {

    actual fun isBrowserEnvironment(): Boolean = hasDomWindow()

    actual fun isDocumentPipSupported(): Boolean {
        if (!hasDomWindow()) return false
        return js("typeof window.documentPictureInPicture !== 'undefined' && window.documentPictureInPicture !== null && typeof window.documentPictureInPicture.requestWindow === 'function'") as Boolean
    }

    actual fun isVideoElementPipSupported(): Boolean {
        if (!hasDomWindow()) return false
        return js("document.pictureInPictureEnabled === true") as Boolean
    }

    actual fun isDocumentHidden(): Boolean {
        if (!hasDomWindow()) return false
        return js("document.hidden === true || document.visibilityState === 'hidden'") as Boolean
    }

    actual fun observeDocumentVisibility(onHiddenChanged: (Boolean) -> Unit): () -> Unit {
        if (!hasDomWindow()) return {}
        val doc: dynamic = js("document")
        val listener: dynamic = { _: dynamic ->
            onHiddenChanged(isDocumentHidden())
        }
        runCatching { doc.addEventListener("visibilitychange", listener) }
        return {
            runCatching { doc.removeEventListener("visibilitychange", listener) }
        }
    }

    actual fun openDocumentPipWindow(
        widthPx: Int,
        heightPx: Int,
        title: String,
        rootPeer: Any?,
        onEntered: () -> Unit,
        onClosed: () -> Unit,
        onFailed: () -> Unit
    ): Boolean {
        if (!isDocumentPipSupported() || rootPeer == null) return false
        return try {
            closeDocumentPipWindow()
            val dpip: dynamic = js("window.documentPictureInPicture")
            val opts: dynamic = js("({})")
            opts.width = widthPx.coerceAtLeast(160)
            opts.height = heightPx.coerceAtLeast(90)
            val promise: dynamic = dpip.requestWindow(opts)
            activeDocumentPipCloseCallback = onClosed
            promise.then(
                { pipWin: dynamic ->
                    activeDocumentPipWin = pipWin
                    runCatching {
                        val pipDoc = pipWin.document
                        if (title.isNotBlank()) {
                            pipDoc.title = title
                        }
                        pipDoc.body.style.margin = "0"
                        pipDoc.body.style.padding = "0"
                        pipDoc.body.style.overflow = "hidden"
                        pipDoc.body.style.background = "#000"
                        val srcSheets: dynamic = js("Array.from(document.styleSheets)")
                        val len = (srcSheets.length as? Int) ?: 0
                        for (i in 0 until len) {
                            val sheet = srcSheets[i]
                            val href = sheet?.href as? String
                            if (!href.isNullOrBlank()) {
                                val link = pipDoc.createElement("link")
                                link.rel = "stylesheet"
                                link.href = href
                                pipDoc.head.appendChild(link)
                            }
                        }
                        pipDoc.body.appendChild(rootPeer.asDynamic())
                        pipWin.addEventListener("pagehide") { _: dynamic ->
                            val cb = activeDocumentPipCloseCallback
                            activeDocumentPipWin = null
                            activeDocumentPipCloseCallback = null
                            cb?.invoke()
                        }
                    }
                    onEntered()
                },
                { _: dynamic ->
                    activeDocumentPipWin = null
                    activeDocumentPipCloseCallback = null
                    onFailed()
                }
            )
            true
        } catch (_: Throwable) {
            activeDocumentPipWin = null
            activeDocumentPipCloseCallback = null
            false
        }
    }

    actual fun closeDocumentPipWindow() {
        val pipWin = activeDocumentPipWin
        activeDocumentPipWin = null
        activeDocumentPipCloseCallback = null
        if (pipWin != null) {
            runCatching { pipWin.close() }
        }
    }

    actual fun attachInPlaceRoot(rootPeer: Any?): Boolean {
        if (!hasDomWindow() || rootPeer == null) return false
        return try {
            val body: dynamic = js("document.body")
            if (body != null) {
                body.appendChild(rootPeer.asDynamic())
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    actual fun detachInPlaceRoot(rootPeer: Any?) {
        if (!hasDomWindow() || rootPeer == null) return
        runCatching {
            val el = rootPeer.asDynamic()
            if (el.parentNode != null) {
                el.parentNode.removeChild(el)
            }
        }
    }

    actual fun isHtmlVideoElement(peer: Any?): Boolean {
        if (!hasDomWindow() || peer == null) return false
        return try {
            val el = peer.asDynamic()
            val tag = (el.tagName as? String)?.lowercase()
            tag == "video"
        } catch (_: Throwable) {
            false
        }
    }

    actual fun requestVideoElementPip(
        videoPeer: Any?,
        onEntered: () -> Unit,
        onExited: () -> Unit,
        onFailed: () -> Unit
    ): Boolean {
        if (!isVideoElementPipSupported() || !isHtmlVideoElement(videoPeer)) return false
        return try {
            val el = videoPeer.asDynamic()
            val leaveListener: dynamic = { _: dynamic ->
                if (activeVideoPipElement === el) {
                    activeVideoPipElement = null
                    activeVideoPipLeaveHandler = null
                    onExited()
                }
            }
            activeVideoPipElement = el
            activeVideoPipLeaveHandler = leaveListener
            el.addEventListener("leavepictureinpicture", leaveListener)
            val promise = el.requestPictureInPicture()
            promise.then(
                { _: dynamic -> onEntered() },
                { _: dynamic ->
                    if (activeVideoPipElement === el) {
                        runCatching { el.removeEventListener("leavepictureinpicture", leaveListener) }
                        activeVideoPipElement = null
                        activeVideoPipLeaveHandler = null
                    }
                    onFailed()
                }
            )
            true
        } catch (_: Throwable) {
            activeVideoPipElement = null
            activeVideoPipLeaveHandler = null
            false
        }
    }

    actual fun exitVideoElementPip(): Boolean {
        val el = activeVideoPipElement
        val listener = activeVideoPipLeaveHandler
        activeVideoPipElement = null
        activeVideoPipLeaveHandler = null
        if (el != null && listener != null) {
            runCatching { el.removeEventListener("leavepictureinpicture", listener) }
        }
        if (!hasDomWindow()) return false
        return try {
            val hasActive = js("document.pictureInPictureElement !== null && typeof document.exitPictureInPicture === 'function'") as Boolean
            if (hasActive) {
                js("document.exitPictureInPicture()")
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }
}
