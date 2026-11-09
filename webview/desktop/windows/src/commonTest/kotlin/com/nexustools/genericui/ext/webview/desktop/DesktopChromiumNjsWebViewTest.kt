package com.nexustools.genericui.ext.webview.desktop

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

class DesktopChromiumNjsWebViewTest {

    @Test
    fun testDesktopWebViewUsesChromiumBlinkAndNativeJitCompiler() {
        DesktopWebViewExtensionProvider.install()
        assertEquals(WebViewEngineType.CHROMIUM_NJS_JIT_DESKTOP, WebViewExtension.activeEngineType)
        assertTrue(WebViewExtension.activeEngineType.usesChromium)
        assertTrue(WebViewExtension.activeEngineType.usesNjsJitRuntime)
        assertNotNull(RuntimeCompiler.activeBlockCompiler)

        val impl = DesktopChromiumNjsWebViewImpl()
        assertEquals("NativeJitCompiler", impl.njsRuntime.activeEngineName)
        assertTrue(impl.browserProcessHost.renderProcessHost.ping())
        assertTrue(impl.browserProcessHost.gpuProcessHost.ping())
        assertTrue(impl.browserProcessHost.networkProcessHost.ping())
        impl.dispose()
    }

    @Test
    fun testDesktopWebViewHtmlExecutionDomBridgeAndHistory() {
        DesktopWebViewExtensionProvider.install()
        val impl = DesktopChromiumNjsWebViewImpl()
        val consoleLogs = ArrayList<WebViewConsoleMessage>()
        val jsErrors = ArrayList<String>()
        val messages = ArrayList<String>()
        val finishedPages = ArrayList<String>()

        impl.setOnConsoleMessage { consoleLogs.add(it) }
        impl.setOnJavaScriptError { jsErrors.add(it) }
        impl.setOnWebMessageReceived { messages.add(it) }
        impl.setOnPageFinished { finishedPages.add(it) }

        impl.registerCommunicationFunction("nativeHostCompute") { payload ->
            val v = payload.toIntOrNull() ?: 0
            (v * 10).toString()
        }

        impl.loadHtml(
            """
            <!DOCTYPE html>
            <html>
            <head><title>Desktop Native WebView</title></head>
            <body>
                <div id="result-box">0</div>
                <script>
                    console.log("Desktop native script started");
                    var computed = window.genericUi.callCommunicationFunction("nativeHostCompute", "7");
                    document.getElementById("result-box").textContent = computed;
                    window.addEventListener("message", function(e) {
                        window.genericUi.postMessage("reply:" + e.data);
                    });
                </script>
            </body>
            </html>
            """.trimIndent(),
            baseUrl = "file:///desktop/index.html"
        )

        assertEquals("file:///desktop/index.html", impl.getUrl())
        assertTrue(finishedPages.contains("file:///desktop/index.html"))
        assertTrue(consoleLogs.any { it.message.contains("Desktop native script started") })
        assertTrue(jsErrors.isEmpty())

        var boxText: String? = null
        impl.evaluateJavascript("document.getElementById('result-box').textContent") { boxText = it }
        assertEquals("70", boxText)

        // Verify postWebMessage delivers to guest listener without duplicate firing
        impl.postWebMessage("ping-from-host")
        assertEquals(listOf("reply:ping-from-host"), messages)

        // History navigation
        impl.loadUrl("https://desktop.example.com/one")
        impl.loadUrl("https://desktop.example.com/two")
        assertTrue(impl.canGoBack())
        impl.goBack()
        assertEquals("https://desktop.example.com/one", impl.getUrl())
        assertTrue(impl.canGoForward())
        impl.goForward()
        assertEquals("https://desktop.example.com/two", impl.getUrl())

        val webView = WebView()
        assertEquals(WebViewEngineType.CHROMIUM_NJS_JIT_DESKTOP, webView.engineType)

        val disabled = DesktopChromiumNjsWebViewImpl(WebViewConfig(enableJavaScript = false))
        var disabledOut: String? = "non-null"
        disabled.evaluateJavascript("2 + 2") { disabledOut = it }
        assertNull(disabledOut)
        assertFalse(disabled.config.enableJavaScript)
        assertTrue(impl.browserProcessHost.gpuProcessHost.rasterizedFrameCount >= 1)
        impl.dispose()
        disabled.dispose()
    }
}
