package com.nexustools.genericui.ext.webview.web

import com.nexustools.genericui.ext.webview.WebView
import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.WebViewConsoleMessage
import com.nexustools.genericui.ext.webview.WebViewEngineType
import com.nexustools.genericui.ext.webview.WebViewExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebIframeWebViewTest {

    @Test
    fun testWebIframeEngineTypeAndElementTag() {
        WebWebViewExtensionProvider.install()
        assertEquals(WebViewEngineType.WEB_NATIVE_IFRAME, WebViewExtension.activeEngineType)
        assertFalse(WebViewExtension.activeEngineType.usesNjsJitRuntime)
        assertFalse(WebViewExtension.activeEngineType.usesChromium)

        val impl = WebNativeIframeWebViewImpl()
        assertEquals("iframe", impl.nativeWidget.tagName)
        assertTrue(impl.nativeWidget.enumClasses.contains("webview-iframe"))
    }

    @Test
    fun testIframeUrlNavigationAndHistory() {
        WebWebViewExtensionProvider.install()
        val impl = WebNativeIframeWebViewImpl()
        val finishedUrls = ArrayList<String>()
        impl.setOnPageFinished { finishedUrls.add(it) }

        assertFalse(impl.canGoBack())
        assertFalse(impl.canGoForward())

        impl.loadUrl("https://example.com/page1")
        assertEquals("https://example.com/page1", impl.getUrl())
        assertEquals("https://example.com/page1", impl.nativeWidget.getAttribute("src"))

        impl.loadUrl("https://example.com/page2")
        assertEquals("https://example.com/page2", impl.getUrl())
        assertTrue(impl.canGoBack())
        assertFalse(impl.canGoForward())

        impl.goBack()
        assertEquals("https://example.com/page1", impl.getUrl())
        assertEquals("https://example.com/page1", impl.nativeWidget.getAttribute("src"))
        assertFalse(impl.canGoBack())
        assertTrue(impl.canGoForward())

        impl.goForward()
        assertEquals("https://example.com/page2", impl.getUrl())
        assertEquals("https://example.com/page2", impl.nativeWidget.getAttribute("src"))
        assertEquals(4, finishedUrls.size)
    }

    @Test
    fun testIframeSrcdocScriptEvaluationAndBidirectionalBridge() {
        WebWebViewExtensionProvider.install()
        val impl = WebNativeIframeWebViewImpl()

        val consoleLogs = ArrayList<WebViewConsoleMessage>()
        val jsErrors = ArrayList<String>()
        val receivedMessages = ArrayList<String>()
        impl.setOnConsoleMessage { consoleLogs.add(it) }
        impl.setOnJavaScriptError { jsErrors.add(it) }
        impl.setOnWebMessageReceived { receivedMessages.add(it) }

        impl.registerCommunicationFunction("hostFormat") { payload ->
            "formatted:$payload"
        }

        impl.loadHtml(
            """
            <!DOCTYPE html>
            <html>
            <head><title>Iframe Test</title></head>
            <body>
                <div id="status">initial</div>
                <script>
                    console.log("iframe inline script running");
                    var hostOut = window.genericUi.callCommunicationFunction("hostFormat", "42");
                    document.getElementById("status").textContent = hostOut;
                    window.genericUi.registerCommunicationFunction("guestReverse", function(str) {
                        return String(str).split("").reverse().join("");
                    });
                    window.genericUi.postMessage("iframe-ready");
                </script>
            </body>
            </html>
            """.trimIndent(),
            baseUrl = "https://app.local/frame.html"
        )

        assertEquals("https://app.local/frame.html", impl.getUrl())
        assertTrue(impl.nativeWidget.getAttribute("srcdoc")?.contains("Iframe Test") == true)
        assertTrue(consoleLogs.any { it.message.contains("iframe inline script running") })
        assertTrue(receivedMessages.contains("iframe-ready"))
        assertTrue(jsErrors.isEmpty())

        var statusText: String? = null
        impl.evaluateJavascript("document.getElementById('status').textContent") { statusText = it }
        assertEquals("formatted:42", statusText)

        var mathRes: String? = null
        impl.evaluateJavascript("6 * 7") { mathRes = it }
        assertEquals("42", mathRes)

        var guestRes: String? = null
        impl.callCommunicationFunction("guestReverse", "abcde") { guestRes = it }
        assertEquals("edcba", guestRes)

        // Verify disabled JS config blocks evaluation
        val disabledImpl = WebNativeIframeWebViewImpl(WebViewConfig(enableJavaScript = false))
        var blockedRes: String? = "non-null"
        disabledImpl.evaluateJavascript("1 + 1") { blockedRes = it }
        assertNull(blockedRes)
    }

    @Test
    fun testHighLevelWebViewComponentWithWebIframeProvider() {
        WebWebViewExtensionProvider.install()
        val webView = WebView()
        assertEquals(WebViewEngineType.WEB_NATIVE_IFRAME, webView.engineType)

        webView.registerCommunicationFunction("ping") { "pong:$it" }
        var evalOut: String? = null
        webView.evaluateJavascript("window.genericUi.call('ping', 'web')") { evalOut = it }
        assertEquals("pong:web", evalOut)
    }
}
