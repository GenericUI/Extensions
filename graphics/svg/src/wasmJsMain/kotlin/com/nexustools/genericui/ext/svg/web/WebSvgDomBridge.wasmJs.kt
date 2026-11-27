@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.nexustools.genericui.ext.svg.web

@JsFun("() => typeof window !== 'undefined' && window !== null && typeof document !== 'undefined' && document !== null")
private external fun wasmIsBrowserEnv(): Boolean

@JsFun("(tagName) => { " +
    "if (typeof document === 'undefined' || !document) return 0; " +
    "const g = typeof globalThis !== 'undefined' ? globalThis : window; " +
    "if (!g.__genericUiSvgNodes) g.__genericUiSvgNodes = {}; " +
    "const id = ((g.__genericUiSvgNextId || 0) + 1) | 0; " +
    "g.__genericUiSvgNextId = id; " +
    "g.__genericUiSvgNodes[id] = document.createElementNS('http://www.w3.org/2000/svg', tagName); " +
    "return id; }")
private external fun wasmCreateSvgNode(tagName: String): Int

@JsFun("(hostPeer, rootHandle) => { " +
    "const g = typeof globalThis !== 'undefined' ? globalThis : window; " +
    "const nodes = g && g.__genericUiSvgNodes; " +
    "const rootEl = nodes ? nodes[rootHandle] : null; " +
    "if (!hostPeer || !rootEl) return false; " +
    "if (rootEl.parentNode !== hostPeer) hostPeer.appendChild(rootEl); " +
    "return true; }")
private external fun wasmMountSvgRoot(hostPeer: JsAny?, rootHandle: Int): Boolean

@JsFun("(rootHandle) => { " +
    "const g = typeof globalThis !== 'undefined' ? globalThis : window; " +
    "const nodes = g && g.__genericUiSvgNodes; " +
    "const rootEl = nodes ? nodes[rootHandle] : null; " +
    "if (rootEl && rootEl.parentNode) { rootEl.parentNode.removeChild(rootEl); } }")
private external fun wasmUnmountSvgRoot(rootHandle: Int)

@JsFun("(handle) => { " +
    "const g = typeof globalThis !== 'undefined' ? globalThis : window; " +
    "if (g && g.__genericUiSvgNodes) delete g.__genericUiSvgNodes[handle]; }")
private external fun wasmReleaseSvgNode(handle: Int)

@JsFun("(handle) => { " +
    "const g = typeof globalThis !== 'undefined' ? globalThis : window; " +
    "const el = g && g.__genericUiSvgNodes ? g.__genericUiSvgNodes[handle] : null; " +
    "if (!el || !el.attributes) return; " +
    "while (el.attributes.length > 0) { el.removeAttribute(el.attributes[0].name); } }")
private external fun wasmClearNodeAttributes(handle: Int)

@JsFun("(handle, name, value) => { " +
    "const g = typeof globalThis !== 'undefined' ? globalThis : window; " +
    "const el = g && g.__genericUiSvgNodes ? g.__genericUiSvgNodes[handle] : null; " +
    "if (el) el.setAttribute(name, value); }")
private external fun wasmSetAttribute(handle: Int, name: String, value: String)

@JsFun("(handle, name, value) => { " +
    "const g = typeof globalThis !== 'undefined' ? globalThis : window; " +
    "const el = g && g.__genericUiSvgNodes ? g.__genericUiSvgNodes[handle] : null; " +
    "if (el && el.style && typeof el.style.setProperty === 'function') { el.style.setProperty(name, value); } }")
private external fun wasmSetStyleProperty(handle: Int, name: String, value: String)

@JsFun("(handle, name) => { " +
    "const g = typeof globalThis !== 'undefined' ? globalThis : window; " +
    "const el = g && g.__genericUiSvgNodes ? g.__genericUiSvgNodes[handle] : null; " +
    "if (el && el.style && typeof el.style.removeProperty === 'function') { el.style.removeProperty(name); } }")
private external fun wasmRemoveStyleProperty(handle: Int, name: String)

@JsFun("(handle, text) => { " +
    "const g = typeof globalThis !== 'undefined' ? globalThis : window; " +
    "const el = g && g.__genericUiSvgNodes ? g.__genericUiSvgNodes[handle] : null; " +
    "if (el) el.textContent = text; }")
private external fun wasmSetTextContent(handle: Int, text: String)

@JsFun("(handle) => { " +
    "const g = typeof globalThis !== 'undefined' ? globalThis : window; " +
    "const el = g && g.__genericUiSvgNodes ? g.__genericUiSvgNodes[handle] : null; " +
    "if (!el) return; " +
    "while (el.firstChild) { el.removeChild(el.firstChild); } }")
private external fun wasmClearChildren(handle: Int)

@JsFun("(parentHandle, childHandle) => { " +
    "const g = typeof globalThis !== 'undefined' ? globalThis : window; " +
    "const nodes = g && g.__genericUiSvgNodes; " +
    "if (!nodes) return; " +
    "const p = nodes[parentHandle]; const c = nodes[childHandle]; " +
    "if (p && c) p.appendChild(c); }")
private external fun wasmAppendChild(parentHandle: Int, childHandle: Int)

@JsFun("(handle, svgRootHandle, onClick, onEnter, onLeave) => { " +
    "const g = typeof globalThis !== 'undefined' ? globalThis : window; " +
    "const nodes = g && g.__genericUiSvgNodes; " +
    "const el = nodes ? nodes[handle] : null; " +
    "if (!el) return; " +
    "const rootEl = nodes ? nodes[svgRootHandle] : null; " +
    "const fireMouse = (cb, ev) => { " +
    "  const cx = (ev && typeof ev.clientX === 'number') ? ev.clientX : 0; " +
    "  const cy = (ev && typeof ev.clientY === 'number') ? ev.clientY : 0; " +
    "  const target = rootEl || el; " +
    "  let lx = cx; let ly = cy; " +
    "  if (target && typeof target.getBoundingClientRect === 'function') { " +
    "    const r = target.getBoundingClientRect(); " +
    "    lx = cx - r.left; ly = cy - r.top; " +
    "  } " +
    "  cb(lx, ly, cx, cy); " +
    "}; " +
    "el.addEventListener('click', () => onClick()); " +
    "el.addEventListener('mouseenter', (ev) => fireMouse(onEnter, ev)); " +
    "el.addEventListener('mouseleave', (ev) => fireMouse(onLeave, ev)); }")
private external fun wasmAttachElementListeners(
    handle: Int,
    svgRootHandle: Int,
    onClick: () -> Unit,
    onEnter: (Float, Float, Float, Float) -> Unit,
    onLeave: (Float, Float, Float, Float) -> Unit
)

internal actual object WebSvgDomBridge {

    actual fun isBrowserEnvironment(): Boolean = wasmIsBrowserEnv()

    actual fun createSvgNode(tagName: String): Int = wasmCreateSvgNode(tagName)

    actual fun mountSvgRoot(hostPeer: Any?, svgRootHandle: Int): Boolean =
        wasmMountSvgRoot(hostPeer as? JsAny, svgRootHandle)

    actual fun unmountSvgRoot(hostPeer: Any?, svgRootHandle: Int) {
        wasmUnmountSvgRoot(svgRootHandle)
    }

    actual fun releaseHandle(handle: Int) {
        wasmReleaseSvgNode(handle)
    }

    actual fun clearNodeAttributes(handle: Int) {
        wasmClearNodeAttributes(handle)
    }

    actual fun setAttribute(handle: Int, name: String, value: String) {
        wasmSetAttribute(handle, name, value)
    }

    actual fun setStyleProperty(handle: Int, name: String, value: String) {
        wasmSetStyleProperty(handle, name, value)
    }

    actual fun removeStyleProperty(handle: Int, name: String) {
        wasmRemoveStyleProperty(handle, name)
    }

    actual fun setTextContent(handle: Int, text: String) {
        wasmSetTextContent(handle, text)
    }

    actual fun clearChildren(handle: Int) {
        wasmClearChildren(handle)
    }

    actual fun appendChild(parentHandle: Int, childHandle: Int) {
        wasmAppendChild(parentHandle, childHandle)
    }

    actual fun attachElementListeners(
        handle: Int,
        svgRootHandle: Int,
        onClick: () -> Unit,
        onPointerEnter: (Float, Float, Float, Float) -> Unit,
        onPointerLeave: (Float, Float, Float, Float) -> Unit
    ) {
        wasmAttachElementListeners(handle, svgRootHandle, onClick, onPointerEnter, onPointerLeave)
    }
}
