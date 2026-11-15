@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.nexustools.genericui.web

@JsFun("(want) => { " +
    "if (typeof window === 'undefined' || typeof navigator === 'undefined') return; " +
    "try { " +
    "  const win = window; " +
    "  win.__genericUiWakeLockDesired = !!want; " +
    "  const acquireWakeSentinel = () => { " +
    "    if (!win.__genericUiWakeLockDesired) return; " +
    "    if (win.__genericUiWakeSentinel && !win.__genericUiWakeSentinel.released) return; " +
    "    if (typeof document !== 'undefined' && document.visibilityState && document.visibilityState !== 'visible') return; " +
    "    const wl = navigator.wakeLock; " +
    "    if (!wl || typeof wl.request !== 'function') return; " +
    "    try { " +
    "      const p = wl.request('screen'); " +
    "      if (p && typeof p.then === 'function') { " +
    "        p.then((sentinel) => { " +
    "          if (!win.__genericUiWakeLockDesired) { " +
    "            try { sentinel.release(); } catch (_e) {} " +
    "            return; " +
    "          } " +
    "          win.__genericUiWakeSentinel = sentinel; " +
    "          if (sentinel && typeof sentinel.addEventListener === 'function') { " +
    "            sentinel.addEventListener('release', () => { " +
    "              if (win.__genericUiWakeSentinel === sentinel) win.__genericUiWakeSentinel = null; " +
    "            }); " +
    "          } " +
    "        }).catch((_err) => {}); " +
    "      } " +
    "    } catch (_err) {} " +
    "  }; " +
    "  if (!win.__genericUiWakeLockVisInstalled && typeof document !== 'undefined' && typeof document.addEventListener === 'function') { " +
    "    win.__genericUiWakeLockVisInstalled = true; " +
    "    document.addEventListener('visibilitychange', () => { " +
    "      if (win.__genericUiWakeLockDesired && document.visibilityState === 'visible') { " +
    "        acquireWakeSentinel(); " +
    "      } " +
    "    }); " +
    "  } " +
    "  if (want) { " +
    "    acquireWakeSentinel(); " +
    "  } else { " +
    "    const active = win.__genericUiWakeSentinel; " +
    "    win.__genericUiWakeSentinel = null; " +
    "    if (active && typeof active.release === 'function') { " +
    "      try { active.release(); } catch (_e) {} " +
    "    } " +
    "  } " +
    "} catch (_e) {} }")
private external fun wasmSetScreenWakeLock(want: Boolean)

internal actual object WebWakeLockBridge {

    private var desiredEnabled: Boolean = false

    actual val isWakeLockHeld: Boolean
        get() = desiredEnabled

    actual fun setWakeLockEnabled(enabled: Boolean, reason: String) {
        desiredEnabled = enabled
        if (!WebBrowserDomBridge.isBrowserEnvironment) return
        wasmSetScreenWakeLock(enabled)
    }
}
