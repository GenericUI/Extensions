package com.nexustools.genericui.ext.webview.desktop

import com.nexustools.genericui.ext.webview.WebView
import com.nexustools.genericui.ext.webview.WebViewCapabilities
import com.nexustools.genericui.ext.webview.WebViewConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class WebViewTwentySuiteComplianceTest {

    private val suiteFiles: List<String> = listOf(
        "01-html5-parser-and-dom-tree.html",
        "02-css-selectors-cascade-and-variables.html",
        "03-box-model-borders-backgrounds-and-visuals.html",
        "04-css-flexbox-layout.html",
        "05-css-grid-layout.html",
        "06-positioning-stacking-and-scrolling.html",
        "07-typography-inline-flow-lists-and-tables.html",
        "08-forms-inputs-and-validation.html",
        "09-dom-events-bubbling-capture-and-input.html",
        "10-canvas2d-drawing-paths-and-transforms.html",
        "11-canvas2d-gradients-images-text-and-pixels.html",
        "12-canvas3d-webgl-shaders-buffers-and-rendering.html",
        "13-canvas3d-webgl-textures-framebuffers-and-animation.html",
        "14-media-video-element-playback-and-controls.html",
        "15-media-audio-and-webaudio-analyzer.html",
        "16-images-svg-and-data-uris.html",
        "17-css-transitions-keyframes-and-raf-animations.html",
        "18-observers-storage-url-encoding-and-history.html",
        "19-network-fetch-xhr-websocket-and-host-bridge.html",
        "20-iframes-shadow-dom-dynamic-scripts-and-feature-flags.html"
    )

    @Test
    fun testAllTwentySuitesPassInsideIsolatedWorkers() {
        DesktopWebViewTestHarness.installProvider()
        val suiteDir = DesktopWebViewTestHarness.resolveWebViewSuiteDirectory()
            .replace('\\', '/')
            .trimEnd('/')
        val hostPid = DesktopWebViewTestHarness.currentProcessId()
        val suiteFailures = ArrayList<String>()

        for (suiteName in suiteFiles) {
            try {
                runSingleSuite(suiteDir, suiteName, hostPid)
            } catch (t: Throwable) {
                suiteFailures.add("[$suiteName] ${t.message ?: t::class.simpleName}")
            }
        }
        assertTrue(
            suiteFailures.isEmpty(),
            "WebView 20-suite failures (${suiteFailures.size}/${suiteFiles.size}):\n" +
                suiteFailures.joinToString("\n")
        )
    }

    @Test
    fun testPerInstanceCapabilityFeatureFlagsEnforced() {
        DesktopWebViewTestHarness.installProvider()
        val wv = WebView(
            config = WebViewConfig(
                enableJavaScript = true,
                domStorageEnabled = false,
                capabilities = WebViewCapabilities(
                    canvas2d = false,
                    webgl = false,
                    storage = false,
                    webSockets = false,
                    shadowDom = false
                )
            )
        )
        try {
            wv.loadHtml("<html><body><canvas id='c'></canvas><div id='sh'></div></body></html>", "https://cap.local/")
            var probeResult: String? = null
            wv.evaluateJavascript(
                """
                (function () {
                  var c = document.getElementById('c');
                  var ctx2 = c.getContext('2d');
                  var ctx3 = c.getContext('webgl');
                  var storageBlocked = false;
                  try { localStorage.setItem('a', 'b'); } catch (e) { storageBlocked = true; }
                  var wsBlocked = false;
                  try { new WebSocket('ws://127.0.0.1:9'); } catch (e2) { wsBlocked = true; }
                  var shadowBlocked = false;
                  try { document.getElementById('sh').attachShadow({ mode: 'open' }); } catch (e3) { shadowBlocked = true; }
                  return (ctx2 === null) + '|' + (ctx3 === null) + '|' + storageBlocked + '|' + wsBlocked + '|' + shadowBlocked;
                })();
                """.trimIndent()
            ) { probeResult = it }
            assertEquals("true|true|true|true|true", probeResult)
        } finally {
            wv.dispose()
        }
    }

    private fun runSingleSuite(suiteDir: String, suiteName: String, hostPid: Long) {
        val fileUrl = if (suiteDir.startsWith("/")) {
            "file://$suiteDir/$suiteName"
        } else {
            "file:///$suiteDir/$suiteName"
        }

        val webMessages = ArrayList<String>()
        val jsErrors = ArrayList<String>()
        val wv = WebView(
            config = WebViewConfig(
                enableJavaScript = true,
                domStorageEnabled = true,
                enableDevTools = true,
                allowFileAccess = true,
                initialViewportWidth = 1024,
                initialViewportHeight = 768
            )
        )
        wv.onWebMessageReceived = { msg -> webMessages.add(msg) }
        wv.onJavaScriptError = { err -> jsErrors.add(err) }
        wv.registerCommunicationFunction("hostEcho") { payload -> "host:$payload" }

        try {
            wv.loadUrl(fileUrl)

            val workerPid = wv.getWorkerProcessId()
            assertNotNull(workerPid, "Worker PID null for $suiteName")
            assertTrue(workerPid > 0L, "Invalid worker PID $workerPid for $suiteName")
            assertNotEquals(hostPid, workerPid, "Worker must run in isolated child OS process for $suiteName")

            var summaryLine: String? = null
            val probeExpr =
                "(function () {" +
                    " var s = window.__suiteSummary;" +
                    " if (!s) return '';" +
                    " return s.passed + '|' + s.total + '|' + s.failed + '|' + JSON.stringify(s.failures || []);" +
                    "})();"

            for (attempt in 0 until 240) {
                var evalOut: String? = null
                wv.evaluateJavascript(probeExpr) { evalOut = it }
                if (!evalOut.isNullOrBlank()) {
                    summaryLine = evalOut
                    break
                }
                runBlocking { delay(25L) }
            }

            if (summaryLine.isNullOrBlank()) {
                fail(
                    "Timed out waiting for __suiteSummary on '$suiteName' " +
                        "(jsErrors=$jsErrors)"
                )
            }

            val parts = summaryLine.split('|', limit = 4)
            assertTrue(parts.size >= 3, "Malformed summary '$summaryLine' on $suiteName")
            val passed = parts[0].toInt()
            val total = parts[1].toInt()
            val failed = parts[2].toInt()
            val failures = parts.getOrElse(3) { "[]" }

            assertTrue(total > 0, "Expected total > 0 tests on $suiteName")
            assertEquals(0, failed, "Suite '$suiteName' had $failed failures ($passed/$total): $failures (jsErrors=$jsErrors)")
            assertEquals(total, passed, "Suite '$suiteName' mismatch passed=$passed total=$total")

            val rgba = wv.captureViewportRgba()
            val size = wv.captureViewportSize()
            assertNotNull(rgba, "Missing composited RGBA framebuffer on $suiteName")
            assertEquals(1024 to 768, size)
            assertEquals(1024 * 768 * 4, rgba.size)
            assertTrue(hasNonUniformRenderedPixels(rgba), "Viewport pixels were blank uniform on $suiteName")
        } finally {
            wv.dispose()
        }
    }

    private fun hasNonUniformRenderedPixels(rgba: ByteArray): Boolean {
        if (rgba.size < 8) return false
        val r0 = rgba[0]
        val g0 = rgba[1]
        val b0 = rgba[2]
        var i = 4
        while (i + 3 < rgba.size) {
            if (rgba[i] != r0 || rgba[i + 1] != g0 || rgba[i + 2] != b0) {
                return true
            }
            i += 16
        }
        return false
    }
}
