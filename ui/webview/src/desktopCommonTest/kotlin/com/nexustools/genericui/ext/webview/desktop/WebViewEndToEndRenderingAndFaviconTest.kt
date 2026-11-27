package com.nexustools.genericui.ext.webview.desktop

import com.nexustools.genericui.ext.webview.WebView
import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.desktop.dom.WebHtml5Parser
import com.nexustools.genericui.ext.webview.desktop.engine.WebAlienDocumentEngine
import com.nexustools.genericui.ext.webview.desktop.ipc.WebViewResolvedCapabilities
import com.nexustools.genericui.ext.webview.desktop.njs.WebViewWorkerRuntime
import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebViewEndToEndRenderingAndFaviconTest {

    @Test
    fun testCssVarsCalcAndBodyCanvasBackgroundRenderRealColoredPixels() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
              <style>
                :root {
                  --page-bg: #0f172a;
                  --card-bg: #e11d48;
                  --box-w: calc(100px + 80px);
                  --box-h: calc(50px * 2);
                }
                body {
                  margin: 0;
                  background-color: var(--page-bg);
                }
                .panel {
                  width: var(--box-w);
                  height: var(--box-h);
                  background-color: var(--card-bg);
                }
              </style>
            </head>
            <body>
              <div class="panel"></div>
            </body>
            </html>
        """.trimIndent()

        val engine = WebAlienDocumentEngine(
            initialWidth = 320,
            initialHeight = 240,
            capabilities = WebViewResolvedCapabilities(),
            allowFileAccess = false
        )
        try {
            val doc = WebHtml5Parser.parseDocument(html, "https://css-vars.test/")
            engine.replaceDocument(doc)
            val tiles = engine.renderAndConsumeDirtyTiles(fullFrame = true)
            assertTrue(tiles.isNotEmpty(), "Expected dirty tiles from initial render")

            val buf = engine.session.offscreenBuffer
            // Pixel at (40, 40) inside .panel (#e11d48 = 225, 29, 72)
            val panelPx = buf.getPixel(40, 40)
            assertEquals(225, panelPx.r)
            assertEquals(29, panelPx.g)
            assertEquals(72, panelPx.b)

            // Pixel at (260, 200) on <body> canvas background (#0f172a = 15, 23, 42)
            val bgPx = buf.getPixel(260, 200)
            assertEquals(15, bgPx.r)
            assertEquals(23, bgPx.g)
            assertEquals(42, bgPx.b)
        } finally {
            engine.dispose()
        }

        // Also verify across isolated worker process IPC -> host framebuffer
        DesktopWebViewTestHarness.installProvider()
        val impl: WebViewNativeImpl<*> = DesktopWebViewTestHarness.createDirectWebViewImpl(
            WebViewConfig(enableJavaScript = true)
        )
        try {
            impl.resizeViewport(320, 240)
            impl.loadHtml(html, "https://css-vars-ipc.test/")
            val rgba = impl.captureViewportRgba()
            assertNotNull(rgba)
            val (w, _) = impl.captureViewportSize()!!
            val panelOff = (40 * w + 40) * 4
            assertEquals(225, rgba[panelOff].toInt() and 0xFF)
            assertEquals(29, rgba[panelOff + 1].toInt() and 0xFF)
            assertEquals(72, rgba[panelOff + 2].toInt() and 0xFF)

            val bgOff = (200 * w + 260) * 4
            assertEquals(15, rgba[bgOff].toInt() and 0xFF)
            assertEquals(23, rgba[bgOff + 1].toInt() and 0xFF)
            assertEquals(42, rgba[bgOff + 2].toInt() and 0xFF)
        } finally {
            impl.dispose()
        }
    }

    @Test
    fun testFaviconExtractionAndWebViewFaviconChangedCallback() {
        val engine = WebAlienDocumentEngine(
            initialWidth = 320,
            initialHeight = 240,
            capabilities = WebViewResolvedCapabilities(),
            allowFileAccess = false
        )
        try {
            // Prefers standard icon over apple-touch-icon and resolves against <base href>
            val docWithBase = WebHtml5Parser.parseDocument(
                """
                <html><head>
                  <base href="https://cdn.example.org/assets/">
                  <link rel="apple-touch-icon" href="touch.png">
                  <link rel="shortcut icon" href="favicon-32.png">
                </head><body></body></html>
                """.trimIndent(),
                "https://app.example.org/index.html"
            )
            engine.replaceDocument(docWithBase)
            assertEquals("https://cdn.example.org/assets/favicon-32.png", engine.extractFaviconUrl("https://app.example.org/index.html"))

            // Falls back to /favicon.ico for http(s) without <link>
            val docNoLink = WebHtml5Parser.parseDocument("<html><head><title>Plain</title></head><body>Hi</body></html>", "https://plain.example.com/sub/page?q=1")
            engine.replaceDocument(docNoLink)
            assertEquals("https://plain.example.com/favicon.ico", engine.extractFaviconUrl("https://plain.example.com/sub/page?q=1"))

            // Returns null for about:blank without <link>
            val docBlank = WebHtml5Parser.parseDocument("<html><head></head><body>Blank</body></html>", "about:blank")
            engine.replaceDocument(docBlank)
            assertNull(engine.extractFaviconUrl("about:blank"))
        } finally {
            engine.dispose()
        }

        // End-to-end WebView + worker process favicon pipeline including DOM <head> mutation
        DesktopWebViewTestHarness.installProvider()
        val webView = WebView(config = WebViewConfig(enableJavaScript = true))
        try {
            val faviconHistory = ArrayList<String?>()
            webView.onFaviconChanged = { url -> faviconHistory.add(url) }

            webView.loadHtml(
                "<html><head><link id='fav' rel='icon' href='/static/app.ico'></head><body>Hello</body></html>",
                "https://fav.example.org/home"
            )
            assertEquals("https://fav.example.org/static/app.ico", webView.currentFaviconUrl)
            assertEquals("https://fav.example.org/static/app.ico", faviconHistory.lastOrNull())

            // Mutate <link href> dynamically via DOM JS
            var mutatedHref: String? = null
            webView.evaluateJavascript(
                "document.getElementById('fav').setAttribute('href', 'https://cdn.example.net/v2.png'); document.getElementById('fav').getAttribute('href');"
            ) { mutatedHref = it }
            assertEquals("https://cdn.example.net/v2.png", mutatedHref)
            assertEquals("https://cdn.example.net/v2.png", webView.currentFaviconUrl)
            assertEquals("https://cdn.example.net/v2.png", faviconHistory.lastOrNull())

            // Fallback to /favicon.ico when no <link> exists
            webView.loadHtml("<html><head><title>NoIcon</title></head><body>Ok</body></html>", "https://nofav.example.com/docs/a")
            assertEquals("https://nofav.example.com/favicon.ico", webView.currentFaviconUrl)
            assertEquals("https://nofav.example.com/favicon.ico", faviconHistory.lastOrNull())
        } finally {
            webView.dispose()
        }
    }

    @Test
    fun testSpaCustomElementsTemplateHydrationNoscriptHidingAndErrorPage() {
        val progressiveFrames = ArrayList<Int>()
        val jsErrors = ArrayList<String>()
        val runtime = WebViewWorkerRuntime(
            initialWidth = 400,
            initialHeight = 300,
            enableJavaScript = true,
            allowFileAccess = false,
            capabilities = WebViewResolvedCapabilities(),
            userAgentOverride = null,
            onConsoleMessage = { _, _, _, _ -> },
            onJavaScriptError = { err -> jsErrors.add(err) },
            onPageFinished = { _, _, _ -> },
            onWebMessageToHost = {},
            onHostBridgeCall = { _, _ -> "{}" }
        )
        runtime.onProgressiveFrame = { tiles -> progressiveFrames.add(tiles.size) }

        try {
            val spaHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                  <title>SPA App</title>
                  <style>
                    :root { --surface: #111827; --accent: #22c55e; }
                    body { margin: 0; background-color: var(--surface); }
                  </style>
                </head>
                <body>
                  <noscript><div id="ns" style="width:400px;height:300px;background-color:#ff0000;">Enable JS</div></noscript>
                  <template id="card-tpl">
                    <div class="banner" style="width:200px;height:100px;background-color:#22c55e;color:#052e16;">Hydrated</div>
                  </template>
                  <ytd-shell title="Initial"></ytd-shell>
                  <script id="boot-script">
                    window.__currentScriptId = document.currentScript ? document.currentScript.id : "";
                    window.__connectedCount = 0;
                    window.__attrChanges = [];
                    class YtdShell extends HTMLElement {
                      static get observedAttributes() { return ['title']; }
                      constructor() {
                        super();
                      }
                      connectedCallback() {
                        window.__connectedCount++;
                        var tpl = document.getElementById('card-tpl');
                        var frag = document.importNode(tpl.content, true);
                        this.appendChild(frag);
                      }
                      attributeChangedCallback(name, oldVal, newVal) {
                        window.__attrChanges.push(name + ':' + (oldVal || '') + '->' + (newVal || ''));
                      }
                    }
                    customElements.define('ytd-shell', YtdShell);
                    var el = document.querySelector('ytd-shell');
                    el.setAttribute('title', 'Updated');
                  </script>
                </body>
                </html>
            """.trimIndent()

            runtime.loadHtml(spaHtml, "https://spa.example/")
            assertTrue(jsErrors.isEmpty(), "Expected no JS errors, got: $jsErrors")
            assertEquals("boot-script", runtime.evaluateJavaScript("window.__currentScriptId").second)
            assertEquals("1", runtime.evaluateJavaScript("String(window.__connectedCount)").second)
            assertTrue(
                runtime.evaluateJavaScript("window.__attrChanges.join(',')").second?.contains("title:Initial->Updated") == true
            )
            // Verify customElements.get & instantiated element
            assertEquals("true", runtime.evaluateJavaScript("typeof customElements.get('ytd-shell') === 'function'").second)
            assertEquals("none", runtime.evaluateJavaScript("getComputedStyle(document.querySelector('noscript')).display").second)

            val buf = runtime.engine.session.offscreenBuffer
            // Top-left (40, 40) was hydrated from <template> inside <ytd-shell>, NOT hidden by red <noscript> (#ff0000)
            val cardPx = buf.getPixel(40, 40)
            assertEquals(34, cardPx.r)
            assertEquals(197, cardPx.g)
            assertEquals(94, cardPx.b)

            // Outside card at (320, 220) shows dark surface #111827 (17, 24, 39)
            val bgPx = buf.getPixel(320, 220)
            assertEquals(17, bgPx.r)
            assertEquals(24, bgPx.g)
            assertEquals(39, bgPx.b)

            // Verify diagnostic error fallback page when loading missing local/invalid URL renders cleanly (not white)
            runtime.loadUrl("file:///nonexistent_genericui_webview_missing_12345.html")
            assertEquals("Page Load Error", runtime.currentTitle)
            assertEquals("Retry", runtime.evaluateJavaScript("document.getElementById('gui-retry-btn').textContent").second)
            val errRgba = runtime.engine.session.offscreenBuffer.bytes
            assertFalse(errRgba.all { it == (-1).toByte() }, "Error fallback page must render visible styled pixels")
        } finally {
            runtime.dispose()
        }
    }
}
