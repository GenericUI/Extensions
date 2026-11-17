package com.nexustools.genericui.web

internal actual object WebWakeLockBridge {

    private var desiredEnabled: Boolean = false

    actual val isWakeLockHeld: Boolean
        get() = desiredEnabled

    actual fun setWakeLockEnabled(enabled: Boolean, reason: String) {
        desiredEnabled = enabled
        if (!WebBrowserDomBridge.isBrowserEnvironment) return
        try {
            js(
                """
                (function(want) {
                    if (typeof window === 'undefined' || typeof navigator === 'undefined') return;
                    var win = window;
                    win.__genericUiWakeLockDesired = !!want;
                    var acquireWakeSentinel = function() {
                        if (!win.__genericUiWakeLockDesired) return;
                        if (win.__genericUiWakeSentinel && !win.__genericUiWakeSentinel.released) return;
                        if (typeof document !== 'undefined' && document.visibilityState && document.visibilityState !== 'visible') return;
                        var wl = navigator.wakeLock;
                        if (!wl || typeof wl.request !== 'function') return;
                        try {
                            var p = wl.request('screen');
                            if (p && typeof p.then === 'function') {
                                p.then(function(sentinel) {
                                    if (!win.__genericUiWakeLockDesired) {
                                        try { sentinel.release(); } catch (_e) {}
                                        return;
                                    }
                                    win.__genericUiWakeSentinel = sentinel;
                                    if (sentinel && typeof sentinel.addEventListener === 'function') {
                                        sentinel.addEventListener('release', function() {
                                            if (win.__genericUiWakeSentinel === sentinel) {
                                                win.__genericUiWakeSentinel = null;
                                            }
                                        });
                                    }
                                }).catch(function(_err) {});
                            }
                        } catch (_err) {}
                    };
                    if (!win.__genericUiWakeLockVisInstalled && typeof document !== 'undefined' && typeof document.addEventListener === 'function') {
                        win.__genericUiWakeLockVisInstalled = true;
                        document.addEventListener('visibilitychange', function() {
                            if (win.__genericUiWakeLockDesired && document.visibilityState === 'visible') {
                                acquireWakeSentinel();
                            }
                        });
                    }
                    if (want) {
                        acquireWakeSentinel();
                    } else {
                        var active = win.__genericUiWakeSentinel;
                        win.__genericUiWakeSentinel = null;
                        if (active && typeof active.release === 'function') {
                            try { active.release(); } catch (_e) {}
                        }
                    }
                })(enabled)
                """
            )
        } catch (_: Throwable) {
        }
    }
}
