package com.nexustools.genericui.ext.webview.swing

import com.nexustools.genericui.ext.webview.WebView
import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.WebViewConsoleMessage
import com.nexustools.genericui.ext.webview.WebViewEngineType
import com.nexustools.genericui.ext.webview.WebViewExtension
import com.nexustools.njs.compiler.RuntimeCompiler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwingChromiumNjsWebViewTest {

    @Test
    fun testSwingWebViewUsesChromiumBlinkAndJavaTranspiler() {
        SwingWebViewExtensionProvider.install()
        assertEquals(WebViewEngineType.CHROMIUM_NJS_JIT_DESKTOP, WebViewExtension.activeEngineType)
        assertTrue(WebViewExtension.activeEngineType.usesChromium)
        assertTrue(WebViewExtension.activeEngineType.usesNjsJitRuntime)
        assertNotNull(RuntimeCompiler.activeBlockCompiler)

        val impl = SwingChromiumNjsWebViewImpl()
        assertEquals("JavaTranspiler", impl.njsRuntime.activeEngineName)
        assertTrue(impl.browserProcessHost.renderProcessHost.ping())
        assertTrue(impl.browserProcessHost.gpuProcessHost.ping())
        assertTrue(impl.browserProcessHost.networkProcessHost.ping())
        impl.dispose()
    }

    @Test
    fun testSwingWebViewHtmlDomExecutionAndCommunicationBridge() {
        SwingWebViewExtensionProvider.install()
        val impl = SwingChromiumNjsWebViewImpl()
        val consoleLogs = ArrayList<WebViewConsoleMessage>()
        val jsErrors = ArrayList<String>()
        val messages = ArrayList<String>()
        val finishedPages = ArrayList<String>()

        impl.setOnConsoleMessage { consoleLogs.add(it) }
        impl.setOnJavaScriptError { jsErrors.add(it) }
        impl.setOnWebMessageReceived { messages.add(it) }
        impl.setOnPageFinished { finishedPages.add(it) }

        impl.registerCommunicationFunction("swingHostGreet") { name ->
            "Hello, $name!"
        }

        impl.loadHtml(
            """
            <!DOCTYPE html>
            <html>
            <head><title>Swing WebView</title></head>
            <body>
                <div id="greeting">pending</div>
                <script>
                    console.log("Swing WebView script running");
                    var msg = window.genericUi.callCommunicationFunction("swingHostGreet", "Swing");
                    document.getElementById("greeting").textContent = msg;
                    window.genericUi.registerCommunicationFunction("swingGuestSquare", function(val) {
                        var n = parseInt(val, 10) || 0;
                        return String(n * n);
                    });
                    window.addEventListener("message", function(ev) {
                        window.genericUi.postMessage("swing-ack:" + ev.data);
                    });
                </script>
            </body>
            </html>
            """.trimIndent(),
            baseUrl = "https://swing.local/index.html"
        )

        assertEquals("https://swing.local/index.html", impl.getUrl())
        assertTrue(finishedPages.contains("https://swing.local/index.html"))
        assertTrue(consoleLogs.any { it.message.contains("Swing WebView script running") })
        assertTrue(jsErrors.isEmpty(), "Expected no JS errors, got: $jsErrors")

        var greeting: String? = null
        impl.evaluateJavascript("document.getElementById('greeting').textContent") { greeting = it }
        assertEquals("Hello, Swing!", greeting)

        var squared: String? = null
        impl.callCommunicationFunction("swingGuestSquare", "9") { squared = it }
        assertEquals("81", squared)

        impl.postWebMessage("host-ping")
        assertEquals(listOf("swing-ack:host-ping"), messages)

        // Navigation history
        impl.loadUrl("https://swing.local/page1")
        impl.loadUrl("https://swing.local/page2")
        assertTrue(impl.canGoBack())
        impl.goBack()
        assertEquals("https://swing.local/page1", impl.getUrl())
        assertTrue(impl.canGoForward())
        impl.goForward()
        assertEquals("https://swing.local/page2", impl.getUrl())

        val webView = WebView()
        assertEquals(WebViewEngineType.CHROMIUM_NJS_JIT_DESKTOP, webView.engineType)

        val disabled = SwingChromiumNjsWebViewImpl(WebViewConfig(enableJavaScript = false))
        var disabledOut: String? = "non-null"
        disabled.evaluateJavascript("1 + 1") { disabledOut = it }
        assertNull(disabledOut)
        assertFalse(disabled.config.enableJavaScript)
        assertTrue(impl.browserProcessHost.gpuProcessHost.rasterizedFrameCount >= 1)
        impl.dispose()
        disabled.dispose()
    }
}
