package com.nexustools.genericui.ext.webview

/**
 * Identifies the underlying rendering and JavaScript execution engine powering [WebView].
 *
 * - On Desktop builds (`:desktop` / `:windows`, `:macos`, `:gtk`, `:qt`, `:swing`), uses the
 *   **Chromium** project (`ChromiumBlinkEngine`) for HTML/CSS layout & rendering coupled with
 *   **`NJS`** (`lib/NJS` with AsmJit/SLJIT Native JIT) for the JavaScript runtime.
 * - On Mobile (`:android`, `:ios`) and `:web`, uses the native platform implementation
 *   (`android.webkit.WebView`, `WKWebView`, or HTML5 `<iframe>`) and **NEVER** uses NJS.
 */
enum class WebViewEngineType(
    val displayName: String,
    val usesChromium: Boolean,
    val usesNjsJitRuntime: Boolean
) {
    CHROMIUM_NJS_JIT_DESKTOP(
        displayName = "Chromium Blink + NJS Native JIT Runtime (Desktop)",
        usesChromium = true,
        usesNjsJitRuntime = true
    ),
    ANDROID_NATIVE_WEBKIT(
        displayName = "Android Native android.webkit.WebView",
        usesChromium = true,
        usesNjsJitRuntime = false
    ),
    IOS_NATIVE_WKWEBVIEW(
        displayName = "iOS Native WKWebView (WebKit)",
        usesChromium = false,
        usesNjsJitRuntime = false
    ),
    WEB_NATIVE_IFRAME(
        displayName = "Web Native HTML5 <iframe>",
        usesChromium = false,
        usesNjsJitRuntime = false
    )
}
