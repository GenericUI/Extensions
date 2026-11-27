package com.nexustools.genericui.ext.webview

/**
 * Identifies the underlying rendering and JavaScript execution engine powering [WebView].
 *
 * - On Desktop builds (`:desktop` / `:windows`, `:macos`, `:gtk`, `:kde`, `:swing`), uses the
 *   Alien layout/compositing engine + companion extensions (`svg`, `canvas2d`, `canvas3d`, `media`)
 *   coupled with **`NJS`** (`lib/NJS`) in an isolated per-instance OS worker process over
 *   `:extensions:system:ipc`.
 * - On Mobile (`:android`, `:ios`) and `:web`, uses the native platform implementation
 *   (`android.webkit.WebView`, `WKWebView`, or HTML5 `<iframe>`) and **NEVER** uses NJS.
 */
enum class WebViewEngineType(
    val displayName: String,
    val usesNjsJitRuntime: Boolean
) {
    ALIEN_NJS(
        displayName = "Alien + NJS Web Runtime (Isolated IPC Worker)",
        usesNjsJitRuntime = true
    ),
    ANDROID_WEBVIEW(
        displayName = "Android Native android.webkit.WebView",
        usesNjsJitRuntime = false
    ),
    APPLE_WKWEBVIEW(
        displayName = "Apple Native WKWebView (WebKit)",
        usesNjsJitRuntime = false
    ),
    WEB_IFRAME(
        displayName = "Web Native HTML5 <iframe>",
        usesNjsJitRuntime = false
    )
}
