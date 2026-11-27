package com.nexustools.genericui.ext.webview.swing

import com.nexustools.genericui.ext.webview.WebView
import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.WebViewEngineType
import com.nexustools.genericui.ext.webview.WebViewExtension
import javax.swing.JEditorPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SwingAlienNjsWebViewTest {

    @Test
    fun testSwingProviderUsesAlienIpcAndDistinctWorkerPidsAndNoJEditorPane() {
        SwingWebViewExtensionProvider.install()
        assertEquals(WebViewEngineType.ALIEN_NJS, WebViewExtension.activeEngineType)

        val hostPid = ProcessHandle.current().pid()
        val impl1 = SwingAlienNjsWebViewImpl(WebViewConfig(enableJavaScript = true))
        val impl2 = SwingAlienNjsWebViewImpl(WebViewConfig(enableJavaScript = true))
        try {
            assertFalse(containsJEditorPane(impl1.nativeWidget), "Swing WebView must NEVER use JEditorPane")
            assertTrue(containsPanelViewport(impl1.nativeWidget), "Swing WebView must embed WebViewSwingPanel")

            val pid1 = impl1.getWorkerProcessId()
            val pid2 = impl2.getWorkerProcessId()
            assertNotNull(pid1)
            assertNotNull(pid2)
            assertNotEquals(hostPid, pid1)
            assertNotEquals(hostPid, pid2)
            assertNotEquals(pid1, pid2, "Each WebView instance must own its own isolated worker process")

            var finishedUrl: String? = null
            impl1.setOnPageFinished { finishedUrl = it }
            impl1.registerCommunicationFunction("doubleNum") { p -> ((p.trim().toIntOrNull() ?: 0) * 2).toString() }
            impl1.loadHtml(
                "<html><body style='background:#0f172a;color:#38bdf8;'>" +
                    "<h1 id='ttl'>Swing Alien</h1>" +
                    "<script>window.__bridgeOut = window.genericUi.callCommunicationFunction('doubleNum', '21');</script>" +
                    "</body></html>",
                "https://swing.test/"
            )
            assertEquals("https://swing.test/", finishedUrl)

            var bridgeVal: String? = null
            impl1.evaluateJavascript("window.__bridgeOut") { bridgeVal = it }
            assertEquals("42", bridgeVal)

            val rgba = impl1.captureViewportRgba()
            assertTrue(rgba.size == 1024 * 768 * 4)
            assertTrue(rgba.any { it != (-1).toByte() })
        } finally {
            impl1.dispose()
            impl2.dispose()
        }
    }

    @Test
    fun testSwingWorkerCrashTriggersAutoRestartAndDocumentReplay() {
        SwingWebViewExtensionProvider.install()
        val impl = SwingAlienNjsWebViewImpl(WebViewConfig(enableJavaScript = true))
        try {
            impl.loadHtml(
                "<html><body><div id='state'>survived-recovery</div></body></html>",
                "https://recovery.swing/"
            )
            val originalPid = impl.getWorkerProcessId()
            assertNotNull(originalPid)

            val killedPid = impl.workerSession.terminateWorkerProcessForCrashRecovery()
            assertEquals(originalPid, killedPid)
            assertTrue(
                impl.workerSession.waitForRestartRecovery(expectedRestartCount = 1, timeoutMs = 15_000),
                "Swing WebView worker should auto-restart after abrupt worker termination"
            )
            val recoveredPid = impl.getWorkerProcessId()
            assertNotNull(recoveredPid)
            assertNotEquals(originalPid, recoveredPid)
            assertTrue(impl.workerSession.workerCrashCount >= 1)
            assertTrue(impl.workerSession.workerRestartCount >= 1)

            var replayedText: String? = null
            impl.evaluateJavascript("document.getElementById('state').textContent") { replayedText = it }
            assertEquals("survived-recovery", replayedText)
        } finally {
            impl.dispose()
        }
    }

    @Test
    fun testPublicWebViewComponentOnSwingIsolatedWorker() {
        SwingWebViewExtensionProvider.install()
        val wv = WebView(config = WebViewConfig(enableJavaScript = true))
        try {
            val msgs = ArrayList<String>()
            wv.onWebMessageReceived = { msgs.add(it) }
            wv.loadHtml("<script>window.genericUi.postMessage('swing-hello');</script>", "https://wv.swing/")
            assertTrue(msgs.contains("swing-hello"))
        } finally {
            wv.dispose()
        }
    }

    private fun containsJEditorPane(c: java.awt.Component): Boolean {
        if (c is JEditorPane) return true
        if (c is java.awt.Container) {
            for (child in c.components) {
                if (containsJEditorPane(child)) return true
            }
        }
        return false
    }

    private fun containsPanelViewport(c: java.awt.Component): Boolean {
        if (c is WebViewSwingPanel) return true
        if (c is java.awt.Container) {
            for (child in c.components) {
                if (containsPanelViewport(child)) return true
            }
        }
        return false
    }
}
