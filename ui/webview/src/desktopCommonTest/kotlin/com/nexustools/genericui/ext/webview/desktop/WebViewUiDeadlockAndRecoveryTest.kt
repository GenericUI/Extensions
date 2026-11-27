@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.webview.desktop

import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomElement
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomSelectorMatcher
import com.nexustools.genericui.ext.webview.desktop.engine.WebResourceLoader
import com.nexustools.genericui.ext.webview.desktop.engine.WebResourceResult
import com.nexustools.genericui.ext.webview.desktop.engine.WorkerPlatformServices
import com.nexustools.genericui.ext.webview.desktop.ipc.WebViewHostUiBridge
import com.nexustools.genericui.ext.webview.desktop.ipc.WebViewHostUiBridgeTestHooks
import com.nexustools.genericui.ext.webview.desktop.njs.NjsEs6Preprocessor
import com.nexustools.genericui.nativeimpl.net.DefaultHttpClientNativeImpl
import com.nexustools.genericui.net.http.HttpResponse
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebViewUiDeadlockAndRecoveryTest {

    @AfterTest
    fun tearDown() {
        WebViewHostUiBridgeTestHooks.reset()
        DefaultHttpClientNativeImpl.clearStaticRoutes()
    }

    @Test
    fun testOnPageFinishedReentrantEvaluateJavascriptAndHostUiStateWithoutDeadlock() {
        DesktopWebViewTestHarness.installProvider()
        val impl = DesktopWebViewTestHarness.createDirectWebViewImpl(
            WebViewConfig(enableJavaScript = true)
        )
        try {
            var finishedUrl: String? = null
            var observedTitle: String? = null
            var reentrantEvalValue: String? = null
            var reentrantViewportSize: Pair<Int, Int>? = null
            val loadingTransitions = ArrayList<Boolean>()

            impl.setOnLoadingChanged { loading -> loadingTransitions.add(loading) }
            impl.setOnPageFinished { url ->
                finishedUrl = url
                observedTitle = impl.getTitle()
                reentrantViewportSize = impl.captureViewportSize()
                impl.evaluateJavascript("document.title + ':' + window.__reentryTag") { value ->
                    reentrantEvalValue = value
                }
            }

            impl.loadHtml(
                """
                <!DOCTYPE html>
                <html>
                <head><title>Reentry Page</title></head>
                <body>
                  <script>window.__reentryTag = 'ok';</script>
                  <div style="width:120px;height:40px;background:#2563eb;">Hello</div>
                </body>
                </html>
                """.trimIndent(),
                "https://reentry.test/"
            )

            assertEquals("https://reentry.test/", finishedUrl)
            assertEquals("Reentry Page", observedTitle)
            assertEquals("Reentry Page:ok", reentrantEvalValue)
            assertNotNull(reentrantViewportSize)
            assertFalse(impl.isLoading())
            assertTrue(loadingTransitions.contains(true))
            assertEquals(false, loadingTransitions.lastOrNull())
        } finally {
            impl.dispose()
        }
    }

    @Test
    fun testActiveUiLoopMarshalingLoadHtmlNonBlockingAndSerializedOrder() {
        DesktopWebViewTestHarness.installProvider()
        val uiQueue = Channel<() -> Unit>(Channel.UNLIMITED)
        WebViewHostUiBridgeTestHooks.uiLoopActiveOverride.store(true)
        WebViewHostUiBridgeTestHooks.onUiThreadOverride.store(false)
        WebViewHostUiBridgeTestHooks.postToUiOverride.store { block ->
            uiQueue.trySend(block)
        }

        assertTrue(WebViewHostUiBridge.isUiLoopActive())
        assertFalse(WebViewHostUiBridge.isOnUiThread())

        val impl = DesktopWebViewTestHarness.createDirectWebViewImpl(
            WebViewConfig(enableJavaScript = true)
        )
        try {
            val finishedUrls = ArrayList<String>()
            val titleUpdates = ArrayList<String>()
            val loadingStates = ArrayList<Boolean>()
            var evalResult: String? = null

            impl.setOnPageFinished { url -> finishedUrls.add(url) }
            impl.setOnTitleChanged { title -> titleUpdates.add(title) }
            impl.setOnLoadingChanged { loading -> loadingStates.add(loading) }

            impl.loadHtml(
                "<html><head><title>First</title></head><body><h1>One</h1></body></html>",
                "https://seq1.test/"
            )
            impl.loadHtml(
                "<html><head><title>Second</title></head><body><h1>Two</h1></body></html>",
                "https://seq2.test/"
            )

            // Both calls must return immediately without blocking on IPC/worker completion.
            assertTrue(finishedUrls.isEmpty(), "Interactive loadHtml must not block caller thread waiting on IPC")

            runBlocking {
                withTimeout(25_000L) {
                    while (finishedUrls.size < 2) {
                        val uiTask = uiQueue.receive()
                        uiTask()
                    }
                    // Drain any trailing redraw/title/loading UI tasks before asserting.
                    while (true) {
                        val trailing = uiQueue.tryReceive().getOrNull() ?: break
                        trailing()
                    }
                }
            }

            assertEquals(listOf("https://seq1.test/", "https://seq2.test/"), finishedUrls)
            assertEquals(listOf("First", "Second"), titleUpdates)
            assertTrue(loadingStates.contains(true))
            assertEquals(false, loadingStates.lastOrNull())
            assertFalse(impl.isLoading())
            assertEquals("Second", impl.getTitle())

            impl.evaluateJavascript("document.title") { value ->
                evalResult = value
            }
            runBlocking {
                withTimeout(15_000L) {
                    while (evalResult == null) {
                        val uiTask = uiQueue.receive()
                        uiTask()
                    }
                }
            }
            assertEquals("Second", evalResult)
        } finally {
            WebViewHostUiBridgeTestHooks.reset()
            impl.dispose()
        }
    }

    @Test
    fun testWorkerSubresourceFailureMalformedCssSvgAndScriptRecoverGracefully() {
        DesktopWebViewTestHarness.installProvider()
        val impl = DesktopWebViewTestHarness.createDirectWebViewImpl(
            WebViewConfig(enableJavaScript = true)
        )
        try {
            val finishedUrls = ArrayList<String>()
            val jsErrors = ArrayList<String>()
            impl.setOnPageFinished { url -> finishedUrls.add(url) }
            impl.setOnJavaScriptError { err -> jsErrors.add(err) }

            impl.loadHtml(
                """
                <!DOCTYPE html>
                <html>
                <head>
                  <title>Resilient Page</title>
                  <link rel="stylesheet" href="unsupported://broken.invalid/main.css">
                  <style>
                    @import "ftp://bad.invalid/theme.css";
                    .good { color: #0f172a; }
                    .broken { color: red; ??? broken_block { }
                  </style>
                </head>
                <body>
                  <script src="mailto:nobody@example.com"></script>
                  <script>function broken(( { </script>
                  <svg><path d="invalid"><unclosed></svg>
                  <img src="about:blank" alt="empty">
                  <div id="ok" class="good">Survived</div>
                  <script>window.__survived = 42;</script>
                </body>
                </html>
                """.trimIndent(),
                "https://resilient.test/"
            )

            assertEquals(listOf("https://resilient.test/"), finishedUrls)
            var survived: String? = null
            impl.evaluateJavascript("String(window.__survived)") { survived = it }
            assertEquals("42", survived)

            // Unsupported scheme loadUrl must also fall back cleanly without killing the worker.
            impl.loadUrl("ftp://invalid-scheme.example/nope")
            assertEquals(2, finishedUrls.size)

            var fallbackText: String? = null
            impl.evaluateJavascript("document.body.textContent.length > 0 ? 'hasBody' : 'empty'") {
                fallbackText = it
            }
            assertEquals("hasBody", fallbackText)
        } finally {
            impl.dispose()
        }
    }

    @Test
    fun testResourceLoaderSubresourceDeduplicationAndAuthorityResolution() {
        val fs = WorkerPlatformServices.createFileSystem()
        val http = DefaultHttpClientNativeImpl()
        var fetchCount = 0
        http.registerRoute("https://assets.example.com/*") { req ->
            fetchCount++
            HttpResponse(
                statusCode = 200,
                statusText = "OK",
                headers = mapOf("Content-Type" to "application/javascript"),
                bodyBytes = "window.x = 1;".encodeToByteArray(),
                url = req.url
            )
        }
        try {
            val loader = WebResourceLoader(fileSystem = fs, httpClient = http, allowFileAccess = false)
            assertEquals(
                "https://example.com/sub/a.js",
                loader.resolveUrl("sub/a.js", "https://example.com?v=1")
            )
            assertEquals(
                "https://example.com/app.js",
                loader.resolveUrl("/app.js", "https://example.com#section")
            )

            loader.beginSubresourceSession(budgetMs = 5_000L)
            val r1 = loader.loadSubresourceSync("/bundle.js", "https://assets.example.com/index.html")
            val r2 = loader.loadSubresourceSync("bundle.js", "https://assets.example.com/index.html")
            val r3 = loader.loadSubresourceSync("https://assets.example.com/bundle.js", "https://assets.example.com/index.html")
            loader.endSubresourceSession()

            assertTrue(r1.ok)
            assertTrue(r2.ok)
            assertTrue(r3.ok)
            assertEquals("window.x = 1;", r2.decodeUtf8Text())
            assertEquals(1, fetchCount, "Identical subresource URLs in one page session should be deduplicated")
        } finally {
            http.dispose()
            fs.dispose()
        }
    }

    @Test
    fun testUtf8BomStylesheetAndMalformedSelectorsAndAsyncAwaitPreprocessWithoutHang() {
        val bomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + ":root { --c: rgb(1, 2, 3); }".encodeToByteArray()
        val bomRes = WebResourceResult(
            url = "https://example.com/bom.css",
            status = 200,
            statusText = "OK",
            mimeType = "text/css",
            bytes = bomBytes
        )
        assertEquals(":root { --c: rgb(1, 2, 3); }", bomRes.decodeUtf8Text())

        // Malformed / BOM / nesting selectors must never hang or throw OutOfBounds
        val el = WebDomElement("div").apply {
            id = "box"
            className = "item"
        }
        for (sel in listOf("\uFEFF:root", "a:not(", "& > .foo", "div%bar")) {
            val compiled = WebDomSelectorMatcher.compileSelectorList(sel)
            WebDomSelectorMatcher.computeSpecificity(sel)
            WebDomSelectorMatcher.maxMatchingSpecificity(el, compiled)
        }
        assertFalse(WebDomSelectorMatcher.matches(el, "a:not("))
        assertFalse(WebDomSelectorMatcher.matches(el, "& > .foo"))
        assertFalse(WebDomSelectorMatcher.matches(el, "div%bar"))

        // Malformed async/await must terminate cleanly without StackOverflowError
        val malformedAsync = NjsEs6Preprocessor.preprocess("async function f() { await ; }")
        assertTrue(malformedAsync.contains("function f"))
        val inlineReturnAwait = NjsEs6Preprocessor.preprocess("async function g() { return await foo() + 1; }")
        assertTrue(inlineReturnAwait.contains("Promise.resolve"))

        // End-to-end WebView load with BOM stylesheet resolves custom properties cleanly via cascade buckets
        DesktopWebViewTestHarness.installProvider()
        val impl = DesktopWebViewTestHarness.createDirectWebViewImpl(
            WebViewConfig(enableJavaScript = true)
        )
        try {
            impl.loadHtml(
                "<!DOCTYPE html><html><head><title>BOM CSS</title>" +
                    "<style>\uFEFF:root { --c: rgb(1, 2, 3); } #box { color: var(--c); }</style>" +
                    "</head><body><div id=\"box\" class=\"item\">Hi</div></body></html>",
                "https://bom.test/"
            )
            var resolvedColor: String? = null
            impl.evaluateJavascript("getComputedStyle(document.getElementById('box')).color") { resolvedColor = it }
            assertEquals("rgb(1, 2, 3)", resolvedColor)

            impl.loadHtml(
                "<!DOCTYPE html><html><head><title>Prepend Self</title></head>" +
                    "<body><p id=\"en\">English lead paragraph</p><p id=\"es\">Segundo parrafo</p></body></html>",
                "https://prepend-self.test/"
            )
            var prependCheck: String? = null
            impl.evaluateJavascript(
                "(function(){ var B = document.body; B.prepend(B.children[0]); return B.children.length + ':' + B.children[0].textContent; })()"
            ) { prependCheck = it }
            assertEquals("2:English lead paragraph", prependCheck)
        } finally {
            impl.dispose()
        }
    }
}
