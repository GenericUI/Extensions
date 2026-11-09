package com.nexustools.genericui.ext.webview.desktop.process

import com.nexustools.genericui.ext.webview.WebViewConsoleMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChromiumMultiProcessWebViewTest {

    @Test
    fun testMojoWireLineFramingRoundTripsArbitraryHtmlJsAndJsonLosslessly() {
        val complexHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Pipe | Ampersand & Equals = Percent %25</title></head>
            <body>
                <div id="box" data-json='{"k":"v|1&2=3\nline2"}'>Hello Chromium!</div>
                <script>
                    var x = 10 | 5;
                    var s = "a=b&c=d|e%20f\r\n";
                </script>
            </body>
            </html>
        """.trimIndent()

        val original = ChromiumMojoMessage(
            messageId = 42L,
            channelId = "chan|main&1=2",
            type = ChromiumMojoMessageType.LOAD_HTML,
            sourceProcess = ChromiumProcessType.BROWSER,
            targetProcess = ChromiumProcessType.RENDERER,
            payload = mapOf(
                "html" to complexHtml,
                "baseUrl" to "https://desktop.local/index.html?a=1&b=2",
                "special" to "line1\nline2\r\n100%|ok=true&done"
            )
        )

        val wireLine = original.encodeWireLine()
        assertTrue(wireLine.startsWith("MOJO/1.0|"))
        assertFalse(wireLine.contains('\n'), "Encoded Mojo wire line must be single-line (no LF)")
        assertFalse(wireLine.contains('\r'), "Encoded Mojo wire line must be single-line (no CR)")

        val decoded = ChromiumMojoMessage.decodeWireLine(wireLine)
        assertNotNull(decoded)
        assertEquals(original.messageId, decoded.messageId)
        assertEquals(original.channelId, decoded.channelId)
        assertEquals(original.type, decoded.type)
        assertEquals(original.sourceProcess, decoded.sourceProcess)
        assertEquals(original.targetProcess, decoded.targetProcess)
        assertEquals(original.payload, decoded.payload)
    }

    @Test
    fun testMultiProcessBrowserRendererGpuAndNetworkCoordination() {
        val postMessages = ArrayList<String>()
        val consoleMessages = ArrayList<WebViewConsoleMessage>()
        val jsErrors = ArrayList<String>()
        val finishedUrls = ArrayList<String>()

        val browserHost = ChromiumBrowserProcessHost(
            instanceId = "mp-test-1",
            onPostMessage = { postMessages.add(it) },
            onConsoleMessage = { consoleMessages.add(it) },
            onJavaScriptError = { jsErrors.add(it) },
            onPageFinished = { finishedUrls.add(it) }
        )

        try {
            assertEquals(ChromiumProcessState.RUNNING, browserHost.renderProcessHost.state)
            assertEquals(ChromiumProcessState.RUNNING, browserHost.gpuProcessHost.state)
            assertEquals(ChromiumProcessState.RUNNING, browserHost.networkProcessHost.state)
            assertTrue(browserHost.renderProcessHost.ping())
            assertTrue(browserHost.gpuProcessHost.ping())
            assertTrue(browserHost.networkProcessHost.ping())

            browserHost.registerVirtualAsset(
                "https://appassets.genericui.local/module.js",
                """
                var titleNode = document.getElementById('status-label');
                titleNode.textContent = 'LoadedFromNetworkService';
                """.trimIndent()
            )

            browserHost.registerCommunicationFunction("hostTransform") { input ->
                "transformed:$input"
            }

            browserHost.loadHtml(
                html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>Multi-Process Desktop WebView</title>
                    </head>
                    <body>
                        <div id="status-label">Initial</div>
                        <div id="bridge-out">None</div>
                        <script src="https://appassets.genericui.local/module.js"></script>
                        <script>
                            console.info("Renderer process executing NJS");
                            var res = window.genericUi.callCommunicationFunction("hostTransform", "payload-42");
                            document.getElementById("bridge-out").textContent = res;
                            window.GenericUIWebView.postMessage("renderer-ready:" + res);
                            window.genericUi.registerCommunicationFunction("guestCompute", function(v) {
                                var num = parseInt(v, 10) || 0;
                                return String(num + 100);
                            });
                        </script>
                    </body>
                    </html>
                """.trimIndent(),
                baseUrl = "https://appassets.genericui.local/index.html"
            )

            assertTrue(jsErrors.isEmpty(), "Expected no JS errors, got: $jsErrors")
            assertEquals(listOf("https://appassets.genericui.local/index.html"), finishedUrls)
            assertTrue(consoleMessages.any { it.level == "info" && it.message.contains("Renderer process executing NJS") })
            assertEquals(listOf("renderer-ready:transformed:payload-42"), postMessages)

            assertEquals("LoadedFromNetworkService", browserHost.evaluateJavaScript("document.getElementById('status-label').textContent"))
            assertEquals("transformed:payload-42", browserHost.evaluateJavaScript("document.getElementById('bridge-out').textContent"))
            assertEquals("125", browserHost.callCommunicationFunction("guestCompute", "25"))

            // Verify Network Service and GPU Compositor process metrics
            assertTrue(browserHost.networkProcessHost.completedFetchCount >= 1)
            assertTrue(browserHost.gpuProcessHost.rasterizedFrameCount >= 1)
            assertNotEquals(0L, browserHost.gpuProcessHost.lastRasterizedFrameChecksum)
            assertTrue(browserHost.gpuProcessHost.lastNonBlankPixelCount > 0L)
            assertTrue(browserHost.gpuProcessHost.lastRasterizedRgbaSummary.startsWith("RGBA8888:"))

            // Verify viewport resize triggers new compositor frame + GPU rasterization
            val prevFrameCount = browserHost.gpuProcessHost.rasterizedFrameCount
            browserHost.setViewportSize(1920, 1080)
            assertTrue(browserHost.gpuProcessHost.rasterizedFrameCount > prevFrameCount)
            assertTrue(browserHost.gpuProcessHost.lastRasterizedRgbaSummary.contains("1920x1080"))
        } finally {
            browserHost.shutdown()
            assertEquals(ChromiumProcessState.TERMINATED, browserHost.renderProcessHost.state)
            assertEquals(ChromiumProcessState.TERMINATED, browserHost.gpuProcessHost.state)
            assertEquals(ChromiumProcessState.TERMINATED, browserHost.networkProcessHost.state)
        }
    }

    @Test
    fun testRendererProcessCrashAndRestartRecovery() {
        val browserHost = ChromiumBrowserProcessHost(instanceId = "mp-crash-test")
        try {
            browserHost.registerCommunicationFunction("survivesRestart") { arg ->
                "recovered:$arg"
            }

            browserHost.renderProcessHost.simulateCrash()
            assertEquals(ChromiumProcessState.CRASHED, browserHost.renderProcessHost.state)

            val restarted = browserHost.renderProcessHost.restartProcess()
            assertTrue(restarted)
            assertEquals(ChromiumProcessState.RUNNING, browserHost.renderProcessHost.state)

            browserHost.loadHtml(
                html = """
                    <!DOCTYPE html>
                    <html>
                    <body>
                        <div id="out"></div>
                        <script>
                            document.getElementById("out").textContent =
                                window.genericUi.callCommunicationFunction("survivesRestart", "ok");
                        </script>
                    </body>
                    </html>
                """.trimIndent(),
                baseUrl = "https://recovery.local/index.html"
            )

            assertEquals("recovered:ok", browserHost.evaluateJavaScript("document.getElementById('out').textContent"))
            assertTrue(browserHost.gpuProcessHost.rasterizedFrameCount >= 1)
        } finally {
            browserHost.shutdown()
        }
    }

    @Test
    fun testNetworkProcessDataUrlAboutBlankAndCspValidation() {
        val networkHost = ChromiumNetworkProcessHost(channelId = "net-test")
        try {
            val blankResp = networkHost.fetchResource("about:blank")
            assertEquals("true", blankResp["ok"])
            assertEquals("200", blankResp["status"])

            val dataResp = networkHost.fetchResource("data:text/html,<html><body>Hello%20Data</body></html>")
            assertEquals("true", dataResp["ok"])
            assertTrue(dataResp["body"].orEmpty().contains("Hello Data"))

            val cspBlocked = networkHost.fetchResource(
                url = "https://example.com/script.js",
                csp = "default-src 'none'"
            )
            assertEquals("false", cspBlocked["ok"])
            assertEquals("403", cspBlocked["status"])
            assertEquals("false", cspBlocked["cspAllowed"])
        } finally {
            networkHost.shutdown()
        }
    }

    @Test
    fun testStdinStdoutWireLineLoopWithRendererGpuAndNetworkWorkers() {
        val rendererWorker = ChromiumRendererProcessWorker("wire-renderer")
        val gpuWorker = ChromiumGpuProcessWorker("wire-gpu")
        val networkWorker = ChromiumNetworkProcessWorker("wire-network")

        val handshakeMsg = ChromiumMojoMessage(
            messageId = 1L,
            channelId = "wire-renderer",
            type = ChromiumMojoMessageType.HANDSHAKE,
            sourceProcess = ChromiumProcessType.BROWSER,
            targetProcess = ChromiumProcessType.RENDERER
        )
        val handshakeLines = rendererWorker.handleWireLine(handshakeMsg.encodeWireLine())
        assertEquals(1, handshakeLines.size)
        val handshakeAck = ChromiumMojoMessage.decodeWireLine(handshakeLines.first())
        assertNotNull(handshakeAck)
        assertEquals(ChromiumMojoMessageType.HANDSHAKE_ACK, handshakeAck.type)
        assertEquals("READY", handshakeAck.payload["status"])

        val loadHtmlMsg = ChromiumMojoMessage(
            messageId = 2L,
            channelId = "wire-renderer",
            type = ChromiumMojoMessageType.LOAD_HTML,
            sourceProcess = ChromiumProcessType.BROWSER,
            targetProcess = ChromiumProcessType.RENDERER,
            payload = mapOf(
                "html" to "<html><head><title>WireDoc</title></head><body><div id='w'>WireText</div></body></html>",
                "baseUrl" to "https://wire.local/"
            )
        )
        val loadOutLines = rendererWorker.handleWireLine(loadHtmlMsg.encodeWireLine())
        val decodedLoadOut = loadOutLines.mapNotNull { ChromiumMojoMessage.decodeWireLine(it) }
        assertTrue(decodedLoadOut.any { it.type == ChromiumMojoMessageType.DOM_MUTATION_COMMIT })
        val compositorMsg = decodedLoadOut.firstOrNull { it.type == ChromiumMojoMessageType.SUBMIT_COMPOSITOR_FRAME }
        assertNotNull(compositorMsg)

        val rasterReq = ChromiumMojoMessage(
            messageId = 3L,
            channelId = "wire-gpu",
            type = ChromiumMojoMessageType.RASTERIZE_DISPLAY_LIST,
            sourceProcess = ChromiumProcessType.BROWSER,
            targetProcess = ChromiumProcessType.GPU,
            payload = compositorMsg.payload
        )
        val gpuLines = gpuWorker.handleWireLine(rasterReq.encodeWireLine())
        val gpuReply = ChromiumMojoMessage.decodeWireLine(gpuLines.first())
        assertNotNull(gpuReply)
        assertEquals(ChromiumMojoMessageType.RASTERIZE_FRAME_COMPLETE, gpuReply.type)
        assertTrue((gpuReply.payload["pixelChecksum"]?.toLongOrNull() ?: 0L) != 0L)

        networkWorker.registerVirtualAsset("https://wire.local/data.json", "{\"wire\":true}")
        val netReq = ChromiumMojoMessage(
            messageId = 4L,
            channelId = "wire-network",
            type = ChromiumMojoMessageType.NETWORK_FETCH_REQUEST,
            sourceProcess = ChromiumProcessType.BROWSER,
            targetProcess = ChromiumProcessType.NETWORK,
            payload = mapOf("url" to "https://wire.local/data.json")
        )
        val netLines = networkWorker.handleWireLine(netReq.encodeWireLine())
        val netReply = ChromiumMojoMessage.decodeWireLine(netLines.first())
        assertNotNull(netReply)
        assertEquals(ChromiumMojoMessageType.NETWORK_FETCH_RESPONSE, netReply.type)
        assertEquals("{\"wire\":true}", netReply.payload["body"])
    }
}
