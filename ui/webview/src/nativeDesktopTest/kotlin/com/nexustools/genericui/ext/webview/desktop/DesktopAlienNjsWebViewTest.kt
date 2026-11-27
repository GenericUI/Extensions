package com.nexustools.genericui.ext.webview.desktop

import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.WebViewEngineType
import com.nexustools.genericui.ext.webview.WebViewExtension
import com.nexustools.genericui.ext.webview.desktop.ipc.NativeWorkerPlatformSupport
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class DesktopAlienNjsWebViewTest {

    @Test
    fun testNativeDesktopProviderSpawnsIsolatedProcessesAndCompositesSurface() {
        DesktopWebViewExtensionProvider.install()
        assertEquals(WebViewEngineType.ALIEN_NJS, WebViewExtension.activeEngineType)

        val hostPid = NativeWorkerPlatformSupport.currentPid()
        val impl1 = DesktopAlienNjsWebViewImpl(WebViewConfig(enableJavaScript = true))
        val impl2 = DesktopAlienNjsWebViewImpl(WebViewConfig(enableJavaScript = true))
        try {
            assertNotNull(impl1.nativeWidget.interactiveSurfaceSpec)
            val pid1 = impl1.getWorkerProcessId()
            val pid2 = impl2.getWorkerProcessId()
            assertNotNull(pid1)
            assertNotNull(pid2)
            assertNotEquals(hostPid, pid1)
            assertNotEquals(hostPid, pid2)
            assertNotEquals(pid1, pid2, "Each WebView instance must spawn a separate genericui-webview-process binary")

            impl1.registerCommunicationFunction("tripleNum") { p -> ((p.trim().toIntOrNull() ?: 0) * 3).toString() }
            impl1.loadHtml(
                """
                <html>
                <body style="margin:0;background:#1e293b;">
                  <button id="btn" style="width:160px;height:48px;background:#38bdf8;color:#0f172a;">Click Me</button>
                  <div id="out">0</div>
                  <script>
                    window.__nativeBridgeVal = window.genericUi.callCommunicationFunction('tripleNum', '14');
                    document.getElementById('btn').addEventListener('click', function () {
                      document.getElementById('out').textContent = 'clicked';
                    });
                  </script>
                </body>
                </html>
                """.trimIndent(),
                "https://native.test/"
            )

            var bridgeVal: String? = null
            impl1.evaluateJavascript("window.__nativeBridgeVal") { bridgeVal = it }
            assertEquals("42", bridgeVal)

            // Forward pointer down + up on the top-left button over IPC
            impl1.workerSession.dispatchPointerEvent("down", 24f, 24f, 0)
            impl1.workerSession.dispatchPointerEvent("up", 24f, 24f, 0)
            var outText: String? = null
            impl1.evaluateJavascript("document.getElementById('out').textContent") { outText = it }
            assertEquals("clicked", outText)

            val surfaceRgba = impl1.nativeWidget.interactiveSurfaceSpec!!.captureRgba!!.invoke()
            assertEquals(1024 * 768 * 4, surfaceRgba.size)
            assertTrue(surfaceRgba.any { it != (-1).toByte() })
        } finally {
            impl1.dispose()
            impl2.dispose()
        }
    }

    @Test
    fun testNativeDesktopWorkerCrashTriggersAutoRestartAndDocumentReplay() {
        DesktopWebViewExtensionProvider.install()
        val impl = DesktopAlienNjsWebViewImpl(WebViewConfig(enableJavaScript = true))
        try {
            impl.loadHtml(
                "<html><body><span id='badge'>native-recovered</span></body></html>",
                "https://recovery.native/"
            )
            val originalPid = impl.getWorkerProcessId()
            assertNotNull(originalPid)

            val killedPid = impl.workerSession.terminateWorkerProcessForCrashRecovery()
            assertEquals(originalPid, killedPid)
            assertTrue(
                impl.workerSession.waitForRestartRecovery(expectedRestartCount = 1, timeoutMs = 15_000),
                "Native desktop worker should auto-restart on unexpected crash"
            )
            val newPid = impl.getWorkerProcessId()
            assertNotNull(newPid)
            assertNotEquals(originalPid, newPid)
            assertTrue(impl.workerSession.workerCrashCount >= 1)
            assertTrue(impl.workerSession.workerRestartCount >= 1)

            var replayed: String? = null
            impl.evaluateJavascript("document.getElementById('badge').textContent") { replayed = it }
            assertEquals("native-recovered", replayed)
        } finally {
            impl.dispose()
        }
    }

    @Test
    fun testSurfaceFrameReadyGlobalTriggeredOnWorkerRedrawAndBgraOpaque() {
        DesktopWebViewExtensionProvider.install()
        val impl = DesktopAlienNjsWebViewImpl(WebViewConfig(enableJavaScript = true))
        val prevHook = DesktopNativeWidget.onSurfaceFrameReadyGlobal
        val readyCount = AtomicInt(0)
        var lastWidget: DesktopNativeWidget? = null
        DesktopNativeWidget.onSurfaceFrameReadyGlobal = { widget ->
            if (widget === impl.nativeWidget) {
                lastWidget = widget
                readyCount.fetchAndAdd(1)
            }
            prevHook?.invoke(widget)
        }
        try {
            impl.loadHtml(
                "<html><body style='margin:0;background:#1d4ed8;'><div style='width:120px;height:80px;background:#f59e0b;'></div></body></html>",
                "https://surface-ready.test/"
            )
            // Also verify direct workerSession.onViewportRedraw hook (invoked on EVT_FRAME_TILES)
            val before = readyCount.load()
            impl.workerSession.onViewportRedraw?.invoke()
            assertTrue(
                readyCount.load() > before,
                "DesktopAlienNjsWebViewImpl must notify DesktopNativeWidget.notifySurfaceFrameReady on viewport redraw"
            )
            assertTrue(lastWidget === impl.nativeWidget)

            val spec = impl.nativeWidget.interactiveSurfaceSpec
            assertNotNull(spec)
            assertTrue(spec.swapCount!!.invoke() > 0L)
            val bgra = spec.captureBgra!!.invoke()
            assertEquals(1024 * 768 * 4, bgra.size)
            // Pixel at (20, 20) should be orange #f59e0b (B=0x0b, G=0x9e, R=0xf5, A=0xff)
            val off = (20 * 1024 + 20) * 4
            assertEquals(0xFF.toByte(), bgra[off + 3])
            assertTrue(bgra[off] != 0xFF.toByte() || bgra[off + 1] != 0xFF.toByte() || bgra[off + 2] != 0xFF.toByte())
        } finally {
            DesktopNativeWidget.onSurfaceFrameReadyGlobal = prevHook
            impl.dispose()
        }
    }
}
