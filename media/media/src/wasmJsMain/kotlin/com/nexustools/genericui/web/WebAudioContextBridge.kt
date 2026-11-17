@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.nexustools.genericui.web

@JsFun("(el) => { " +
    "if (!el) return; " +
    "try { " +
    "  if (el.crossOrigin !== 'anonymous') el.crossOrigin = 'anonymous'; " +
    "  if (typeof el.setAttribute === 'function') el.setAttribute('crossorigin', 'anonymous'); " +
    "} catch (_e) {} }")
private external fun wasmEnsureCrossOriginAnonymous(el: JsAny)

@JsFun("(el, fftSize) => { " +
    "if (!el || typeof window === 'undefined') return false; " +
    "try { " +
    "  if (el.crossOrigin !== 'anonymous') el.crossOrigin = 'anonymous'; " +
    "  if (typeof el.setAttribute === 'function') el.setAttribute('crossorigin', 'anonymous'); " +
    "  const win = window; " +
    "  let ctx = win.__genericUiAudioCtx; " +
    "  if (!ctx) { " +
    "    const Ctor = win.AudioContext || win.webkitAudioContext; " +
    "    if (!Ctor) return false; " +
    "    ctx = new Ctor(); " +
    "    win.__genericUiAudioCtx = ctx; " +
    "  } " +
    "  let analyser = el.__genericUiAnalyserNode; " +
    "  if (!analyser) { " +
    "    analyser = ctx.createAnalyser(); " +
    "    analyser.fftSize = fftSize; " +
    "    analyser.smoothingTimeConstant = 0.8; " +
    "    const source = ctx.createMediaElementSource(el); " +
    "    source.connect(analyser); " +
    "    analyser.connect(ctx.destination); " +
    "    el.__genericUiMediaSourceNode = source; " +
    "    el.__genericUiAnalyserNode = analyser; " +
    "  } else { " +
    "    analyser.fftSize = fftSize; " +
    "  } " +
    "  win.__genericUiActiveAnalyserNode = analyser; " +
    "  return true; " +
    "} catch (_e) { return false; } }")
private external fun wasmConnectMediaElement(el: JsAny, fftSize: Int): Boolean

@JsFun("() => { " +
    "if (typeof window === 'undefined') return false; " +
    "try { " +
    "  const ctx = window.__genericUiAudioCtx; " +
    "  if (!ctx) return false; " +
    "  if (ctx.state === 'suspended' && typeof ctx.resume === 'function') ctx.resume(); " +
    "  return true; " +
    "} catch (_e) { return false; } }")
private external fun wasmResumeAudioContext(): Boolean

@JsFun("() => typeof window !== 'undefined' && !!window.__genericUiActiveAnalyserNode")
private external fun wasmHasActiveAnalyser(): Boolean

@JsFun("(fftSize, count) => { " +
    "if (typeof window === 'undefined') return null; " +
    "try { " +
    "  const a = window.__genericUiActiveAnalyserNode; " +
    "  if (!a) return null; " +
    "  if (a.fftSize !== fftSize) a.fftSize = fftSize; " +
    "  const buf = new Uint8Array(count); " +
    "  a.getByteTimeDomainData(buf); " +
    "  return buf; " +
    "} catch (_e) { return null; } }")
private external fun wasmCaptureByteTimeDomain(fftSize: Int, count: Int): JsAny?

@JsFun("(fftSize, count) => { " +
    "if (typeof window === 'undefined') return null; " +
    "try { " +
    "  const a = window.__genericUiActiveAnalyserNode; " +
    "  if (!a) return null; " +
    "  if (a.fftSize !== fftSize) a.fftSize = fftSize; " +
    "  const buf = new Uint8Array(count); " +
    "  a.getByteFrequencyData(buf); " +
    "  return buf; " +
    "} catch (_e) { return null; } }")
private external fun wasmCaptureByteFrequency(fftSize: Int, count: Int): JsAny?

@JsFun("(arr, idx) => (arr[idx] | 0)")
private external fun wasmGetUint8(arr: JsAny, idx: Int): Int

internal actual object WebAudioContextBridge {

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    actual fun ensureCrossOriginAnonymous(mediaPeer: Any?) {
        val rawPeer = if (mediaPeer is WebDomElement) mediaPeer.nativeBrowserPeer else mediaPeer
        if (!WebBrowserDomBridge.isBrowserEnvironment || rawPeer == null) return
        val jsPeer = rawPeer as? JsAny ?: return
        wasmEnsureCrossOriginAnonymous(jsPeer)
    }

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    actual fun connectMediaElement(mediaPeer: Any?, fftSize: Int): Boolean {
        val rawPeer = if (mediaPeer is WebDomElement) mediaPeer.nativeBrowserPeer else mediaPeer
        if (!WebBrowserDomBridge.isBrowserEnvironment || rawPeer == null) return false
        val jsPeer = rawPeer as? JsAny ?: return false
        return wasmConnectMediaElement(jsPeer, coercePowerOfTwoFft(fftSize))
    }

    actual fun resumeAudioContext(): Boolean {
        if (!WebBrowserDomBridge.isBrowserEnvironment) return false
        return wasmResumeAudioContext()
    }

    actual fun hasActiveAnalyser(): Boolean =
        WebBrowserDomBridge.isBrowserEnvironment && wasmHasActiveAnalyser()

    actual fun readByteTimeDomainData(fftSize: Int, destination: ByteArray): Boolean {
        if (!WebBrowserDomBridge.isBrowserEnvironment || destination.isEmpty()) return false
        val arr = wasmCaptureByteTimeDomain(coercePowerOfTwoFft(fftSize), destination.size) ?: return false
        for (i in destination.indices) {
            destination[i] = (wasmGetUint8(arr, i) and 0xFF).toByte()
        }
        return true
    }

    actual fun readFloatTimeDomainData(fftSize: Int, destination: FloatArray): Boolean {
        if (!WebBrowserDomBridge.isBrowserEnvironment || destination.isEmpty()) return false
        val bytes = ByteArray(destination.size)
        if (!readByteTimeDomainData(fftSize, bytes)) return false
        for (i in destination.indices) {
            val u = bytes[i].toInt() and 0xFF
            destination[i] = ((u - 128) / 128f).coerceIn(-1f, 1f)
        }
        return true
    }

    actual fun readByteFrequencyData(fftSize: Int, destination: ByteArray): Boolean {
        if (!WebBrowserDomBridge.isBrowserEnvironment || destination.isEmpty()) return false
        val arr = wasmCaptureByteFrequency(coercePowerOfTwoFft(fftSize), destination.size) ?: return false
        for (i in destination.indices) {
            destination[i] = (wasmGetUint8(arr, i) and 0xFF).toByte()
        }
        return true
    }

    actual fun readFloatFrequencyData(fftSize: Int, destination: FloatArray): Boolean {
        if (!WebBrowserDomBridge.isBrowserEnvironment || destination.isEmpty()) return false
        val bytes = ByteArray(destination.size)
        if (!readByteFrequencyData(fftSize, bytes)) return false
        for (i in destination.indices) {
            val u = bytes[i].toInt() and 0xFF
            destination[i] = -100f + (u / 255f) * 70f
        }
        return true
    }

    private fun coercePowerOfTwoFft(fftSize: Int): Int {
        val clamped = fftSize.coerceIn(32, 32768)
        var p = 32
        while (p < clamped && p < 32768) {
            p = p shl 1
        }
        return p
    }
}
