package com.nexustools.genericui.web

internal actual object WebAudioContextBridge {

    actual fun ensureCrossOriginAnonymous(mediaPeer: Any?) {
        val rawPeer = if (mediaPeer is WebDomElement) mediaPeer.nativeBrowserPeer else mediaPeer
        if (!WebBrowserDomBridge.isBrowserEnvironment || rawPeer == null) return
        val el: dynamic = rawPeer
        try {
            if (el.crossOrigin != "anonymous") {
                el.crossOrigin = "anonymous"
            }
            if (js("typeof el.setAttribute === 'function'") as Boolean) {
                el.setAttribute("crossorigin", "anonymous")
            }
        } catch (_: Throwable) {
        }
    }

    actual fun connectMediaElement(mediaPeer: Any?, fftSize: Int): Boolean {
        val rawPeer = if (mediaPeer is WebDomElement) mediaPeer.nativeBrowserPeer else mediaPeer
        if (!WebBrowserDomBridge.isBrowserEnvironment || rawPeer == null) return false
        val el: dynamic = rawPeer
        return try {
            ensureCrossOriginAnonymous(el)
            val win: dynamic = js("window")
            val validFft = coercePowerOfTwoFft(fftSize)
            var ctx: dynamic = win.__genericUiAudioCtx
            if (ctx == null) {
                val ctor: dynamic = win.AudioContext ?: win.webkitAudioContext
                if (ctor == null) return false
                ctx = js("new ctor()")
                win.__genericUiAudioCtx = ctx
            }
            var analyser: dynamic = el.__genericUiAnalyserNode
            if (analyser == null) {
                analyser = ctx.createAnalyser()
                analyser.fftSize = validFft
                analyser.smoothingTimeConstant = 0.8
                val source: dynamic = ctx.createMediaElementSource(el)
                source.connect(analyser)
                analyser.connect(ctx.destination)
                el.__genericUiMediaSourceNode = source
                el.__genericUiAnalyserNode = analyser
            } else {
                analyser.fftSize = validFft
            }
            win.__genericUiActiveAnalyserNode = analyser
            true
        } catch (_: Throwable) {
            false
        }
    }

    actual fun resumeAudioContext(): Boolean {
        if (!WebBrowserDomBridge.isBrowserEnvironment) return false
        return try {
            val win: dynamic = js("window")
            val ctx: dynamic = win.__genericUiAudioCtx ?: return false
            if (ctx.state == "suspended" && (js("typeof ctx.resume === 'function'") as Boolean)) {
                ctx.resume()
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    actual fun hasActiveAnalyser(): Boolean {
        if (!WebBrowserDomBridge.isBrowserEnvironment) return false
        return try {
            val win: dynamic = js("window")
            win.__genericUiActiveAnalyserNode != null
        } catch (_: Throwable) {
            false
        }
    }

    actual fun readByteTimeDomainData(fftSize: Int, destination: ByteArray): Boolean {
        if (!WebBrowserDomBridge.isBrowserEnvironment || destination.isEmpty()) return false
        return try {
            val win: dynamic = js("window")
            val analyser: dynamic = win.__genericUiActiveAnalyserNode ?: return false
            val validFft = coercePowerOfTwoFft(fftSize)
            if ((analyser.fftSize as? Int) != validFft) {
                analyser.fftSize = validFft
            }
            val n = destination.size
            val u8: dynamic = js("new Uint8Array(n)")
            analyser.getByteTimeDomainData(u8)
            for (i in 0 until n) {
                destination[i] = ((u8[i] as Int) and 0xFF).toByte()
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    actual fun readFloatTimeDomainData(fftSize: Int, destination: FloatArray): Boolean {
        if (!WebBrowserDomBridge.isBrowserEnvironment || destination.isEmpty()) return false
        return try {
            val win: dynamic = js("window")
            val analyser: dynamic = win.__genericUiActiveAnalyserNode ?: return false
            val validFft = coercePowerOfTwoFft(fftSize)
            if ((analyser.fftSize as? Int) != validFft) {
                analyser.fftSize = validFft
            }
            val n = destination.size
            val f32: dynamic = js("new Float32Array(n)")
            analyser.getFloatTimeDomainData(f32)
            for (i in 0 until n) {
                destination[i] = (f32[i] as Number).toFloat()
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    actual fun readByteFrequencyData(fftSize: Int, destination: ByteArray): Boolean {
        if (!WebBrowserDomBridge.isBrowserEnvironment || destination.isEmpty()) return false
        return try {
            val win: dynamic = js("window")
            val analyser: dynamic = win.__genericUiActiveAnalyserNode ?: return false
            val validFft = coercePowerOfTwoFft(fftSize)
            if ((analyser.fftSize as? Int) != validFft) {
                analyser.fftSize = validFft
            }
            val n = destination.size
            val u8: dynamic = js("new Uint8Array(n)")
            analyser.getByteFrequencyData(u8)
            for (i in 0 until n) {
                destination[i] = ((u8[i] as Int) and 0xFF).toByte()
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    actual fun readFloatFrequencyData(fftSize: Int, destination: FloatArray): Boolean {
        if (!WebBrowserDomBridge.isBrowserEnvironment || destination.isEmpty()) return false
        return try {
            val win: dynamic = js("window")
            val analyser: dynamic = win.__genericUiActiveAnalyserNode ?: return false
            val validFft = coercePowerOfTwoFft(fftSize)
            if ((analyser.fftSize as? Int) != validFft) {
                analyser.fftSize = validFft
            }
            val n = destination.size
            val f32: dynamic = js("new Float32Array(n)")
            analyser.getFloatFrequencyData(f32)
            for (i in 0 until n) {
                destination[i] = (f32[i] as Number).toFloat()
            }
            true
        } catch (_: Throwable) {
            false
        }
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
