package com.nexustools.genericui.ext.webview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebViewComponentTest {

    @Test
    fun testWebViewEngineTypesAndInvariants() {
        assertTrue(WebViewEngineType.CHROMIUM_NJS_JIT_DESKTOP.usesChromium)
        assertTrue(WebViewEngineType.CHROMIUM_NJS_JIT_DESKTOP.usesNjsJitRuntime)

        assertTrue(WebViewEngineType.ANDROID_NATIVE_WEBKIT.usesChromium)
        assertFalse(WebViewEngineType.ANDROID_NATIVE_WEBKIT.usesNjsJitRuntime)

        assertFalse(WebViewEngineType.IOS_NATIVE_WKWEBVIEW.usesChromium)
        assertFalse(WebViewEngineType.IOS_NATIVE_WKWEBVIEW.usesNjsJitRuntime)

        assertFalse(WebViewEngineType.WEB_NATIVE_IFRAME.usesChromium)
        assertFalse(WebViewEngineType.WEB_NATIVE_IFRAME.usesNjsJitRuntime)
    }

    @Test
    fun testWebViewConfigAndCommunicationFunctions() {
        val config = WebViewConfig(
            enableJavaScript = true,
            domStorageEnabled = true,
            enableDevTools = true,
            userAgentOverride = "GenericUI-TestAgent/2.0",
            allowFileAccess = true
        )
        val webView = WebView(initialUrl = "https://example.org", config = config)
        assertEquals("https://example.org", webView.url)
        assertTrue(webView.config.enableJavaScript)
        assertTrue(webView.config.domStorageEnabled)
        assertTrue(webView.config.enableDevTools)
        assertTrue(webView.config.allowFileAccess)
        assertEquals("GenericUI-TestAgent/2.0", webView.config.userAgentOverride)

        webView.registerCommunicationFunction("sumPair") { payload ->
            val parts = payload.split(",").mapNotNull { it.trim().toIntOrNull() }
            parts.sum().toString()
        }
        assertTrue(webView.hasCommunicationFunction("sumPair"))
        assertTrue(webView.getCommunicationFunctionNames().contains("sumPair"))
        assertEquals("30", webView.invokeCommunicationFunctionFromGuest("sumPair", "12,18"))

        webView.unregisterCommunicationFunction("sumPair")
        assertFalse(webView.hasCommunicationFunction("sumPair"))
        assertEquals("{}", webView.invokeCommunicationFunctionFromGuest("sumPair", "12,18"))
    }

    @Test
    fun testWebViewLoadingAndDisabledJsBehavior() {
        val webView = WebView(config = WebViewConfig(enableJavaScript = false))

        webView.loadUrl("https://genericui.dev/docs")
        assertEquals("https://genericui.dev/docs", webView.url)
        assertNull(webView.htmlContent)

        webView.loadHtml("<h1>Hello</h1>", "https://genericui.dev/")
        assertEquals("<h1>Hello</h1>", webView.htmlContent)
        assertEquals("https://genericui.dev/", webView.htmlBaseUrl)
        assertNull(webView.url)

        // When enableJavaScript = false, evaluateJavascript and callCommunicationFunction return null to callback
        var evalCallbackInvoked = false
        var evalResult: String? = "non-null"
        webView.evaluateJavascript("1 + 1") { res ->
            evalCallbackInvoked = true
            evalResult = res
        }
        assertTrue(evalCallbackInvoked)
        assertNull(evalResult)

        var commCallbackInvoked = false
        var commResult: String? = "non-null"
        webView.callCommunicationFunction("anyFn", "{}") { res ->
            commCallbackInvoked = true
            commResult = res
        }
        assertTrue(commCallbackInvoked)
        assertNull(commResult)
    }
}
